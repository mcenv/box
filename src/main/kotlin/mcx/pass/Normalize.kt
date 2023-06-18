@file:Suppress("NAME_SHADOWING")

package mcx.pass

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mcx.ast.*
import mcx.ast.Core.Pattern
import mcx.ast.Core.Term
import mcx.util.collections.mapWith

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
    is Term.Tag        -> {
      Value.Tag
    }

    is Term.TagOf      -> {
      Value.TagOf(term.repr)
    }

    is Term.Type       -> {
      val tag = lazy { evalTerm(term.element) }
      Value.Type(tag)
    }

    is Term.Bool       -> {
      Value.Bool
    }

    is Term.BoolOf     -> {
      Value.BoolOf(term.value)
    }

    is Term.If         -> {
      when (val condition = evalTerm(term.condition)) {
        is Value.BoolOf -> {
          if (condition.value) {
            evalTerm(term.thenBranch)
          } else {
            evalTerm(term.elseBranch)
          }
        }
        else            -> {
          val thenBranch = lazy { evalTerm(term.thenBranch) }
          val elseBranch = lazy { evalTerm(term.elseBranch) }
          val type = lazy { evalTerm(term.type) }
          Value.If(condition, thenBranch, elseBranch, type)
        }
      }
    }

    is Term.I8         -> {
      Value.I8
    }

    is Term.I8Of       -> {
      Value.I8Of(term.value)
    }

    is Term.I16        -> {
      Value.I16
    }

    is Term.I16Of      -> {
      Value.I16Of(term.value)
    }

    is Term.I32        -> {
      Value.I32
    }

    is Term.I32Of      -> {
      Value.I32Of(term.value)
    }

    is Term.I64        -> {
      Value.I64
    }

    is Term.I64Of      -> {
      Value.I64Of(term.value)
    }

    is Term.F32        -> {
      Value.F32
    }

    is Term.F32Of      -> {
      Value.F32Of(term.value)
    }

    is Term.F64        -> {
      Value.F64
    }

    is Term.F64Of      -> {
      Value.F64Of(term.value)
    }

    is Term.Str        -> {
      Value.Str
    }

    is Term.StrOf      -> {
      Value.StrOf(term.value)
    }

    is Term.I8Array    -> {
      Value.I8Array
    }

    is Term.I8ArrayOf  -> {
      val elements = term.elements.map { lazy { evalTerm(it) } }
      Value.I8ArrayOf(elements)
    }

    is Term.I32Array   -> {
      Value.I32Array
    }

    is Term.I32ArrayOf -> {
      val elements = term.elements.map { lazy { evalTerm(it) } }
      Value.I32ArrayOf(elements)
    }

    is Term.I64Array   -> {
      Value.I64Array
    }

    is Term.I64ArrayOf -> {
      val elements = term.elements.map { lazy { evalTerm(it) } }
      Value.I64ArrayOf(elements)
    }

    is Term.Vec        -> {
      val element = lazy { evalTerm(term.element) }
      Value.Vec(element)
    }

    is Term.VecOf      -> {
      val elements = term.elements.map { lazy { evalTerm(it) } }
      val type = lazy { evalTerm(term.type) }
      Value.VecOf(elements, type)
    }

    is Term.Struct     -> {
      val elements = term.elements.mapValuesTo(linkedMapOf()) { lazy { evalTerm(it.value) } }
      Value.Struct(elements)
    }

    is Term.StructOf   -> {
      val elements = term.elements.mapValuesTo(linkedMapOf()) { lazy { evalTerm(it.value) } }
      val type = lazy { evalTerm(term.type) }
      Value.StructOf(elements, type)
    }

    is Term.Ref        -> {
      val element = lazy { evalTerm(term.element) }
      Value.Ref(element)
    }

    is Term.RefOf      -> {
      val element = lazy { evalTerm(term.element) }
      val type = lazy { evalTerm(term.type) }
      Value.RefOf(element, type)
    }

    is Term.Point      -> {
      val element = lazy { evalTerm(term.element) }
      val type = lazy { evalTerm(term.type) }
      Value.Point(element, type)
    }

    is Term.Union      -> {
      val elements = term.elements.map { lazy { evalTerm(it) } }
      val type = lazy { evalTerm(term.type) }
      Value.Union(elements, type)
    }

    is Term.Func       -> {
      val (_, params) = term.params.mapWith(this) { modify, (param, type) ->
        val type = lazy { evalTerm(type) }
        modify(this + lazyOf(Value.Var("#${next()}", next(), type)))
        param to type
      }
      val result = Closure(this, term.result)
      Value.Func(term.open, params, result)
    }

    is Term.FuncOf     -> {
      val result = Closure(this, term.result)
      val type = lazy { evalTerm(term.type) }
      Value.FuncOf(term.open, term.params, result, type)
    }

    is Term.Apply      -> {
      val func = evalTerm(term.func)
      val args = term.args.map { lazy { evalTerm(it) } }
      when (func) {
        is Value.FuncOf -> func.result(args)
        is Value.Def    -> lookupBuiltin(func.def.name)!!.eval(args)
        else            -> null
      } ?: run {
        val type = lazy { evalTerm(term.type) }
        Value.Apply(term.open, func, args, type)
      }
    }

    is Term.Code       -> {
      val element = lazy { evalTerm(term.element) }
      Value.Code(element)
    }

    is Term.CodeOf     -> {
      val element = lazy { evalTerm(term.element) }
      val type = lazy { evalTerm(term.type) }
      Value.CodeOf(element, type)
    }

    is Term.Splice  -> {
      when (val element = evalTerm(term.element)) {
        is Value.CodeOf -> element.element.value
        else            -> {
          val type = lazy { evalTerm(term.type) }
          Value.Splice(element, type)
        }
      }
    }

    is Term.Command -> {
      val element = lazy { evalTerm(term.element) }
      val type = lazy { evalTerm(term.type) }
      Value.Command(element, type)
    }

    is Term.Let     -> {
      val init = lazy { evalTerm(term.init) }
      (this + init).evalTerm(term.body)
    }

    is Term.Match -> {
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
        -1   -> {
          val type = lazy { evalTerm(term.type) }
          Value.Match(scrutinee, branches, type)
        }
        else -> {
          val (_, body) = term.branches[matchedIndex]
          (this + scrutinee).evalTerm(body)
        }
      }
    }

    is Term.Proj  -> {
      when (val target = evalTerm(term.target)) {
        is Value.StructOf -> {
          val projection = term.projection as Projection.StructOf
          target.elements[projection.name]!!.value
        }
        else              -> {
          val type = lazy { evalTerm(term.type) }
          Value.Proj(target, term.projection, type)
        }
      }
    }

    is Term.Var   -> {
      this[term.idx.toLvl(next()).value].value
    }

    is Term.Def   -> {
      if (Modifier.BUILTIN in term.def.modifiers) {
        // Builtin definitions have compiler-defined semantics and need to be handled specially.
        val type = lazy { evalTerm(term.type) }
        Value.Def(term.def, type)
      } else {
        evalTerm(term.def.body!!)
      }
    }
    is Term.Meta  -> {
      val type = lazy { evalTerm(term.type) }
      Value.Meta(term.index, term.source, type)
    }

    is Term.Hole  -> {
      Value.Hole
    }
  }
}

/**
 * Quotes [value] into a [Term] under the environment of [this] [Lvl] size.
 */
fun Lvl.quoteValue(value: Value): Term {
  return when (value) {
    is Value.Tag        -> {
      Term.Tag
    }

    is Value.TagOf      -> {
      Term.TagOf(value.repr)
    }

    is Value.Type       -> {
      val tag = quoteValue(value.element.value)
      Term.Type(tag)
    }

    is Value.Bool       -> {
      Term.Bool
    }

    is Value.BoolOf     -> {
      Term.BoolOf(value.value)
    }

    is Value.If         -> {
      val condition = quoteValue(value.condition)
      val thenBranch = quoteValue(value.thenBranch.value)
      val elseBranch = quoteValue(value.elseBranch.value)
      val type = quoteValue(value.type.value)
      Term.If(condition, thenBranch, elseBranch, type)
    }

    is Value.I8         -> {
      Term.I8
    }

    is Value.I8Of       -> {
      Term.I8Of(value.value)
    }

    is Value.I16        -> {
      Term.I16
    }

    is Value.I16Of      -> {
      Term.I16Of(value.value)
    }

    is Value.I32        -> {
      Term.I32
    }

    is Value.I32Of      -> {
      Term.I32Of(value.value)
    }

    is Value.I64        -> {
      Term.I64
    }

    is Value.I64Of      -> {
      Term.I64Of(value.value)
    }

    is Value.F32        -> {
      Term.F32
    }

    is Value.F32Of      -> {
      Term.F32Of(value.value)
    }

    is Value.F64        -> {
      Term.F64
    }

    is Value.F64Of      -> {
      Term.F64Of(value.value)
    }

    is Value.Str        -> {
      Term.Str
    }

    is Value.StrOf      -> {
      Term.StrOf(value.value)
    }

    is Value.I8Array    -> {
      Term.I8Array
    }

    is Value.I8ArrayOf  -> {
      val elements = value.elements.map { quoteValue(it.value) }
      Term.I8ArrayOf(elements)
    }

    is Value.I32Array   -> {
      Term.I32Array
    }

    is Value.I32ArrayOf -> {
      val elements = value.elements.map { quoteValue(it.value) }
      Term.I32ArrayOf(elements)
    }

    is Value.I64Array   -> {
      Term.I64Array
    }

    is Value.I64ArrayOf -> {
      val elements = value.elements.map { quoteValue(it.value) }
      Term.I64ArrayOf(elements)
    }

    is Value.Vec        -> {
      val element = quoteValue(value.element.value)
      Term.Vec(element)
    }

    is Value.VecOf      -> {
      val elements = value.elements.map { quoteValue(it.value) }
      val type = quoteValue(value.type.value)
      Term.VecOf(elements, type)
    }

    is Value.Struct     -> {
      val elements = value.elements.mapValuesTo(linkedMapOf()) { quoteValue(it.value.value) }
      Term.Struct(elements)
    }

    is Value.StructOf   -> {
      val elements = value.elements.mapValuesTo(linkedMapOf()) { quoteValue(it.value.value) }
      val type = quoteValue(value.type.value)
      Term.StructOf(elements, type)
    }

    is Value.Ref        -> {
      val element = quoteValue(value.element.value)
      Term.Ref(element)
    }

    is Value.RefOf      -> {
      val element = quoteValue(value.element.value)
      val type = quoteValue(value.type.value)
      Term.RefOf(element, type)
    }

    is Value.Point      -> {
      val element = quoteValue(value.element.value)
      val type = quoteValue(value.type.value)
      Term.Point(element, type)
    }
    is Value.Union      -> {
      val elements = value.elements.map { quoteValue(it.value) }
      val type = quoteValue(value.type.value)
      Term.Union(elements, type)
    }

    is Value.Func       -> {
      val params = value.params.mapIndexed { i, (pattern, type) ->
        pattern to (this + i).quoteValue(type.value)
      }
      val result = (this + value.params.size).quoteValue(
        value.result.open(this, value.params.map { (_, type) -> type })
      )
      Term.Func(value.open, params, result)
    }

    is Value.FuncOf     -> {
      val result = (this + value.params.size).quoteValue(
        value.result.open(this, (value.type.value as Value.Func /* TODO: unify */).params.map { (_, type) -> type })
      )
      val type = quoteValue(value.type.value)
      Term.FuncOf(value.open, value.params, result, type)
    }

    is Value.Apply      -> {
      val func = quoteValue(value.func)
      val args = value.args.map { quoteValue(it.value) }
      val type = quoteValue(value.type.value)
      Term.Apply(value.open, func, args, type)
    }

    is Value.Code       -> {
      val element = quoteValue(value.element.value)
      Term.Code(element)
    }

    is Value.CodeOf  -> {
      val element = quoteValue(value.element.value)
      val type = quoteValue(value.type.value)
      Term.CodeOf(element, type)
    }

    is Value.Splice  -> {
      val element = quoteValue(value.element)
      val type = quoteValue(value.type.value)
      Term.Splice(element, type)
    }

    is Value.Command -> {
      val element = quoteValue(value.element.value)
      val type = quoteValue(value.type.value)
      Term.Command(element, type)
    }

    is Value.Let     -> {
      // TODO: glued evaluation
      error("Unexpected value: $value")
    }

    is Value.Match   -> {
      val scrutinee = quoteValue(value.scrutinee.value)
      val branches = value.branches.map { (pattern, body) ->
        val body = (this + 1).quoteValue(body.value)
        pattern to body
      }
      val type = quoteValue(value.type.value)
      Term.Match(scrutinee, branches, type)
    }

    is Value.Proj    -> {
      val target = quoteValue(value.target)
      val type = quoteValue(value.type.value)
      Term.Proj(target, value.projection, type)
    }

    is Value.Var     -> {
      val type = quoteValue(value.type.value)
      Term.Var(value.name, value.lvl.toIdx(this), type)
    }

    is Value.Def     -> {
      val type = quoteValue(value.type.value)
      Term.Def(value.def, type)
    }

    is Value.Meta    -> {
      val type = quoteValue(value.type.value)
      Term.Meta(value.index, value.source, type)
    }

    is Value.Hole    -> {
      Term.Hole
    }
  }
}

/**
 * Applies [this] [Closure] closure to [args] and returns the result [Value].
 */
operator fun Closure.invoke(args: List<Lazy<Value>>): Value {
  return (env + args).evalTerm(body)
}

/**
 * Converts [this] [Closure] closure to an open [Value] with the free variables of [types] under the context of the [size].
 */
fun Closure.open(
  size: Lvl,
  types: List<Lazy<Value>>,
): Value {
  return this(types.mapIndexed { i, type ->
    lazyOf(Value.Var("#${size + i}", size + i, type))
  })
}

infix fun Pattern.matches(value: Lazy<Value>): Boolean {
  return when (this) {
    is Pattern.I32Of    -> {
      when (val value = value.value) {
        is Value.I32Of -> value.value == this.value
        else           -> false
      }
    }

    is Pattern.StructOf -> {
      when (val value = value.value) {
        is Value.StructOf -> {
          elements.all { (name, pattern) ->
            value.elements[name]?.let { pattern matches it } ?: false
          }
        }
        else              -> false
      }
    }

    is Pattern.Drop     -> true

    is Pattern.Var      -> true

    is Pattern.Hole     -> false
  }
}
