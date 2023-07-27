@file:Suppress("NAME_SHADOWING")

package box.pass.frontend.elaborate

import box.ast.Core.Pattern
import box.ast.Core.Term
import box.ast.common.Lvl
import box.ast.common.Proj
import box.ast.common.toIdx
import box.ast.common.toLvl
import box.pass.Closure
import box.pass.Env
import box.pass.Value
import box.util.collections.mapWith
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus

/**
 * Creates an empty [Env].
 */
@Suppress("NOTHING_TO_INLINE")
inline fun emptyEnv(): Env {
  return persistentListOf()
}

fun Env.next(): Lvl {
  return Lvl(size)
}

/**
 * Evaluates [term] into a [Value] under [this] [Env] environment.
 */
fun Env.evalTerm(term: Term): Value {
  return when (term) {
    is Term.Tag        -> Value.Tag

    is Term.TagOf      -> Value.TagOf(term.repr)

    is Term.Type       -> {
      val tag = lazy { evalTerm(term.element) }
      Value.Type(tag)
    }

    is Term.Unit       -> Value.Unit

    is Term.Bool       -> Value.Bool

    is Term.I8         -> Value.I8

    is Term.I16        -> Value.I16

    is Term.I32        -> Value.I32

    is Term.I64        -> Value.I64

    is Term.F32        -> Value.F32

    is Term.F64        -> Value.F64

    is Term.Wtf16      -> Value.Wtf16

    is Term.ConstOf<*> -> Value.ConstOf(term.value)

    is Term.I8Array    -> Value.I8Array

    is Term.I8ArrayOf  -> {
      val elements = term.elements.map { lazy { evalTerm(it) } }
      Value.I8ArrayOf(elements)
    }

    is Term.I32Array   -> Value.I32Array

    is Term.I32ArrayOf -> {
      val elements = term.elements.map { lazy { evalTerm(it) } }
      Value.I32ArrayOf(elements)
    }

    is Term.I64Array   -> Value.I64Array

    is Term.I64ArrayOf -> {
      val elements = term.elements.map { lazy { evalTerm(it) } }
      Value.I64ArrayOf(elements)
    }

    is Term.List       -> {
      val element = lazy { evalTerm(term.element) }
      Value.List(element)
    }

    is Term.ListOf     -> {
      val elements = term.elements.map { lazy { evalTerm(it) } }
      Value.ListOf(elements)
    }

    is Term.Compound   -> {
      val elements = term.elements.mapValuesTo(linkedMapOf()) { lazy { evalTerm(it.value) } }
      Value.Compound(elements)
    }

    is Term.CompoundOf -> {
      val elements = term.elements.mapValuesTo(linkedMapOf()) { lazy { evalTerm(it.value) } }
      Value.CompoundOf(elements)
    }

    is Term.Point      -> {
      val elementType = lazy { evalTerm(term.elementType) }
      val element = lazy { evalTerm(term.element) }
      Value.Point(elementType, element)
    }

    is Term.Union      -> {
      val elements = term.elements.map { lazy { evalTerm(it) } }
      Value.Union(elements)
    }

    is Term.Func       -> {
      val (_, params) = term.params.mapWith(this) { modify, (param, type) ->
        val type = lazy { evalTerm(type) }
        modify(this + lazyOf(Value.Var("#${next()}", next())))
        param to type
      }
      val result = { args: List<Lazy<Value>> -> (this + args).evalTerm(term.result) }
      Value.Func(term.open, params, result)
    }

    is Term.FuncOf     -> {
      val result = { args: List<Lazy<Value>> -> (this + args).evalTerm(term.result) }
      Value.FuncOf(term.open, term.params, result)
    }

    is Term.Apply      -> {
      val func = evalTerm(term.func)
      val args = term.args.map { lazy { evalTerm(it) } }
      when (func) {
        is Value.FuncOf  -> func.result(args)
        is Value.Builtin -> func.builtin(args)
        else             -> null
      } ?: Value.Apply(term.open, func, args)
    }

    is Term.Code       -> {
      val element = lazy { evalTerm(term.element) }
      Value.Code(element)
    }

    is Term.CodeOf     -> {
      val element = lazy { evalTerm(term.element) }
      Value.CodeOf(element)
    }

    is Term.Splice     -> {
      when (val element = evalTerm(term.element)) {
        is Value.CodeOf -> element.element.value
        else            -> Value.Splice(element)
      }
    }

    is Term.Path       -> {
      val element = lazy { evalTerm(term.element) }
      Value.Path(element)
    }

    is Term.PathOf     -> {
      val element = lazy { evalTerm(term.element) }
      Value.PathOf(element)
    }

    is Term.Get        -> {
      when (val element = evalTerm(term.element)) {
        is Value.PathOf -> element.element.value
        else            -> Value.Get(element)
      }
    }

    is Term.Command    -> {
      val element = lazy { evalTerm(term.element) }
      val type = lazy { evalTerm(term.type) }
      Value.Command(element, type)
    }

    is Term.Let        -> {
      val init = lazy { evalTerm(term.init) }
      (this + init).evalTerm(term.body)
    }

    is Term.If         -> {
      val scrutinee = lazy { evalTerm(term.scrutinee) }
      var matchedIndex = -1
      val branches = term.branches.mapIndexed { index, (pattern, body) ->
        val body = lazy { evalTerm(body) }
        if (matchedIndex == -1 && pattern matches scrutinee) {
          matchedIndex = index
        }
        pattern to body
      }
      when (matchedIndex) {
        -1 -> Value.If(scrutinee, branches)
        else -> {
          val (_, body) = term.branches[matchedIndex]
          (this + scrutinee).evalTerm(body)
        }
      }
    }

    is Term.Project    -> {
      val target = evalTerm(term.target)
      term.projs.foldIndexed(target) { index, acc, proj ->
        when (acc) {
          is Value.ListOf     -> acc.elements[(proj as Proj.ListOf).index].value
          is Value.CompoundOf -> acc.elements[(proj as Proj.CompoundOf).name]!!.value
          else                -> {
            if (index == term.projs.lastIndex) {
              acc
            } else {
              Value.Project(acc, term.projs.drop(index))
            }
          }
        }
      }
    }

    is Term.Var        -> this[term.idx.toLvl(next()).value].value

    is Term.Def        -> evalTerm(term.def.body)

    is Term.Meta       -> Value.Meta(term.index, term.source)

    is Term.Builtin    -> Value.Builtin(term.builtin)

    is Term.Hole       -> Value.Hole
  }
}

/**
 * Quotes [value] into a [Term] under the environment of [this] [Lvl] size.
 */
fun Lvl.quoteValue(value: Value): Term {
  return when (value) {
    is Value.Tag        -> Term.Tag

    is Value.TagOf      -> Term.TagOf(value.repr)

    is Value.Type       -> {
      val tag = quoteValue(value.element.value)
      Term.Type(tag)
    }

    is Value.Unit       -> Term.Unit

    is Value.Bool       -> Term.Bool

    is Value.I8         -> Term.I8

    is Value.I16        -> Term.I16

    is Value.I32        -> Term.I32

    is Value.I64        -> Term.I64

    is Value.F32        -> Term.F32

    is Value.F64        -> Term.F64

    is Value.Wtf16      -> Term.Wtf16

    is Value.ConstOf<*> -> Term.ConstOf(value.value)

    is Value.I8Array    -> Term.I8Array

    is Value.I8ArrayOf  -> {
      val elements = value.elements.map { quoteValue(it.value) }
      Term.I8ArrayOf(elements)
    }

    is Value.I32Array   -> Term.I32Array

    is Value.I32ArrayOf -> {
      val elements = value.elements.map { quoteValue(it.value) }
      Term.I32ArrayOf(elements)
    }

    is Value.I64Array   -> Term.I64Array

    is Value.I64ArrayOf -> {
      val elements = value.elements.map { quoteValue(it.value) }
      Term.I64ArrayOf(elements)
    }

    is Value.List       -> {
      val element = quoteValue(value.element.value)
      Term.List(element)
    }

    is Value.ListOf     -> {
      val elements = value.elements.map { quoteValue(it.value) }
      Term.ListOf(elements)
    }

    is Value.Compound   -> {
      val elements = value.elements.mapValuesTo(linkedMapOf()) { quoteValue(it.value.value) }
      Term.Compound(elements)
    }

    is Value.CompoundOf -> {
      val elements = value.elements.mapValuesTo(linkedMapOf()) { quoteValue(it.value.value) }
      Term.CompoundOf(elements)
    }

    is Value.Point      -> {
      val elementType = quoteValue(value.element.value)
      val element = quoteValue(value.element.value)
      Term.Point(elementType, element)
    }
    is Value.Union      -> {
      val elements = value.elements.map { quoteValue(it.value) }
      Term.Union(elements)
    }

    is Value.Func       -> {
      val params = value.params.mapIndexed { i, (pattern, type) ->
        pattern to (this + i).quoteValue(type.value)
      }
      val result = (this + value.params.size).quoteValue(
        value.result.open(this, value.params.size)
      )
      Term.Func(value.open, params, result)
    }

    is Value.FuncOf     -> {
      val result = (this + value.params.size).quoteValue(
        value.result.open(this, value.params.size)
      )
      Term.FuncOf(value.open, value.params, result)
    }

    is Value.Apply      -> {
      val func = quoteValue(value.func)
      val args = value.args.map { quoteValue(it.value) }
      Term.Apply(value.open, func, args)
    }

    is Value.Code       -> {
      val element = quoteValue(value.element.value)
      Term.Code(element)
    }

    is Value.CodeOf     -> {
      val element = quoteValue(value.element.value)
      Term.CodeOf(element)
    }

    is Value.Splice     -> {
      val element = quoteValue(value.element)
      Term.Splice(element)
    }

    is Value.Path       -> {
      val element = quoteValue(value.element.value)
      Term.Path(element)
    }

    is Value.PathOf     -> {
      val element = quoteValue(value.element.value)
      Term.PathOf(element)
    }

    is Value.Get        -> {
      val element = quoteValue(value.element)
      Term.Get(element)
    }

    is Value.Command    -> {
      val element = quoteValue(value.element.value)
      val type = quoteValue(value.type.value)
      Term.Command(element, type)
    }

    is Value.Let        -> {
      // TODO: glued evaluation
      error("Unexpected value: $value")
    }

    is Value.If         -> {
      val scrutinee = quoteValue(value.scrutinee.value)
      val branches = value.branches.map { (pattern, body) ->
        val body = (this + 1).quoteValue(body.value)
        pattern to body
      }
      Term.If(scrutinee, branches)
    }

    is Value.Project    -> {
      val target = quoteValue(value.target)
      Term.Project(target, value.projs)
    }

    is Value.Var        -> Term.Var(value.name, value.lvl.toIdx(this))

    is Value.Def        -> Term.Def(value.def)

    is Value.Meta       -> Term.Meta(value.index, value.source)

    is Value.Builtin    -> Term.Builtin(value.builtin)

    is Value.Hole       -> Term.Hole
  }
}

/**
 * Converts [this] closure to an open [Value] with the free variables of [args] under the context of the [size].
 */
fun Closure.open(size: Lvl, args: Int): Value {
  return this(List(args) { i -> lazyOf(Value.Var("#${size + i}", size + i)) })
}

infix fun Pattern.matches(value: Lazy<Value>): Boolean {
  return when (this) {
    is Pattern.ConstOf -> {
      when (val value = value.value) {
        is Value.ConstOf<*> -> value.value == this.value
        else                -> false
      }
    }

    is Pattern.I8ArrayOf  -> {
      when (val value = value.value) {
        is Value.I8ArrayOf -> {
          elements.size == value.elements.size &&
          (elements zip value.elements).all { (pattern, value) ->
            pattern matches value
          }
        }
        else               -> false
      }
    }

    is Pattern.I32ArrayOf -> {
      when (val value = value.value) {
        is Value.I32ArrayOf -> {
          elements.size == value.elements.size &&
          (elements zip value.elements).all { (pattern, value) ->
            pattern matches value
          }
        }
        else                -> false
      }
    }

    is Pattern.I64ArrayOf -> {
      when (val value = value.value) {
        is Value.I64ArrayOf -> {
          elements.size == value.elements.size &&
          (elements zip value.elements).all { (pattern, value) ->
            pattern matches value
          }
        }
        else                -> false
      }
    }

    is Pattern.ListOf     -> {
      when (val value = value.value) {
        is Value.ListOf -> {
          elements.size == value.elements.size &&
          (elements zip value.elements).all { (pattern, value) ->
            pattern matches value
          }
        }
        else            -> false
      }
    }

    is Pattern.CompoundOf -> {
      when (val value = value.value) {
        is Value.CompoundOf -> {
          elements.all { (name, pattern) ->
            value.elements[name]?.let { pattern matches it } ?: false
          }
        }
        else                -> false
      }
    }

    is Pattern.Drop    -> true

    is Pattern.Var     -> true

    is Pattern.Hole    -> false
  }
}
