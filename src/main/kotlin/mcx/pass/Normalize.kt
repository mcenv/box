@file:Suppress("NAME_SHADOWING")

package mcx.pass

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mcx.ast.Core.Pattern
import mcx.ast.Core.Term
import mcx.ast.Lvl
import mcx.ast.Modifier
import mcx.ast.toIdx
import mcx.ast.toLvl
import mcx.util.collections.mapWith
import mcx.util.map

fun emptyEnv(): Env {
  return persistentListOf()
}

fun Env.next(): Lvl {
  return Lvl(size)
}

fun Env.evalTerm(term: Term): Value {
  return when (term) {
    is Term.Tag        -> {
      Value.Tag
    }

    is Term.TagOf      -> {
      Value.TagOf(term.value)
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
          val type = term.type.map { evalTerm(it) }
          Value.If(condition, thenBranch, elseBranch, type)
        }
      }
    }

    is Term.Is         -> {
      val scrutinee = lazy { evalTerm(term.scrutinee) }
      val scrutineer = term.scrutineer
      when (val result = scrutineer matches scrutinee) {
        null -> Value.Is(scrutinee, scrutineer)
        else -> Value.BoolOf(result)
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
      val type = term.type.map { evalTerm(it) }
      Value.VecOf(elements, type)
    }

    is Term.Struct     -> {
      val elements = term.elements.mapValuesTo(linkedMapOf()) { lazy { evalTerm(it.value) } }
      Value.Struct(elements)
    }

    is Term.StructOf   -> {
      val elements = term.elements.mapValuesTo(linkedMapOf()) { lazy { evalTerm(it.value) } }
      val type = term.type.map { evalTerm(it) }
      Value.StructOf(elements, type)
    }

    is Term.Point      -> {
      val element = lazy { evalTerm(term.element) }
      val type = term.type.map { evalTerm(it) }
      Value.Point(element, type)
    }

    is Term.Union      -> {
      val elements = term.elements.map { lazy { evalTerm(it) } }
      val type = term.type.map { evalTerm(it) }
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
      val type = term.type.map { evalTerm(it) }
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
        val type = term.type.map { evalTerm(it) }
        Value.Apply(term.open, func, args, type)
      }
    }

    is Term.Code       -> {
      val element = lazy { evalTerm(term.element) }
      Value.Code(element)
    }

    is Term.CodeOf     -> {
      val element = lazy { evalTerm(term.element) }
      val type = term.type.map { evalTerm(it) }
      Value.CodeOf(element, type)
    }

    is Term.Splice     -> {
      when (val element = evalTerm(term.element)) {
        is Value.CodeOf -> element.element.value
        else            -> {
          val type = term.type.map { evalTerm(it) }
          Value.Splice(element, type)
        }
      }
    }

    is Term.Command    -> {
      val element = lazy { evalTerm(term.element) }
      val type = term.type.map { evalTerm(it) }
      Value.Command(element, type)
    }

    is Term.Let        -> {
      val init = lazy { evalTerm(term.init) }
      (this + init).evalTerm(term.body)
    }

    is Term.Var        -> {
      this[next().toLvl(term.idx).value].value
    }

    is Term.Def        -> {
      if (Modifier.BUILTIN in term.def.modifiers) {
        // Builtin definitions have compiler-defined semantics and need to be handled specially.
        val type = term.type.map { evalTerm(it) }
        Value.Def(term.def, type)
      } else {
        evalTerm(term.def.body!!)
      }
    }
    is Term.Meta       -> {
      val type = term.type.map { evalTerm(it) }
      Value.Meta(term.index, term.source, type)
    }

    is Term.Hole       -> {
      Value.Hole
    }
  }
}

fun Lvl.quoteValue(value: Value): Term {
  return when (value) {
    is Value.Tag        -> {
      Term.Tag
    }

    is Value.TagOf      -> {
      Term.TagOf(value.value)
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
      val type = value.type.map { quoteValue(it) }
      Term.If(condition, thenBranch, elseBranch, type)
    }

    is Value.Is         -> {
      val scrutinee = quoteValue(value.scrutinee.value)
      Term.Is(scrutinee, value.scrutineer)
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
      val type = value.type.map { quoteValue(it) }
      Term.VecOf(elements, type)
    }

    is Value.Struct     -> {
      val elements = value.elements.mapValuesTo(linkedMapOf()) { quoteValue(it.value.value) }
      Term.Struct(elements)
    }

    is Value.StructOf   -> {
      val elements = value.elements.mapValuesTo(linkedMapOf()) { quoteValue(it.value.value) }
      val type = value.type.map { quoteValue(it) }
      Term.StructOf(elements, type)
    }

    is Value.Point      -> {
      val element = quoteValue(value.element.value)
      val type = value.type.map { quoteValue(it) }
      Term.Point(element, type)
    }
    is Value.Union      -> {
      val elements = value.elements.map { quoteValue(it.value) }
      val type = value.type.map { quoteValue(it) }
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
      val type = value.type.map { quoteValue(it) }
      Term.FuncOf(value.open, value.params, result, type)
    }

    is Value.Apply      -> {
      val func = quoteValue(value.func)
      val args = value.args.map { quoteValue(it.value) }
      val type = value.type.map { quoteValue(it) }
      Term.Apply(value.open, func, args, type)
    }

    is Value.Code       -> {
      val element = quoteValue(value.element.value)
      Term.Code(element)
    }

    is Value.CodeOf     -> {
      val element = quoteValue(value.element.value)
      val type = value.type.map { quoteValue(it) }
      Term.CodeOf(element, type)
    }

    is Value.Splice     -> {
      val element = quoteValue(value.element)
      val type = value.type.map { quoteValue(it) }
      Term.Splice(element, type)
    }

    is Value.Command    -> {
      val element = quoteValue(value.element.value)
      val type = value.type.map { quoteValue(it) }
      Term.Command(element, type)
    }

    is Value.Let        -> {
      // TODO: glued evaluation
      error("unexpected value: $value")
    }

    is Value.Var        -> {
      val type = value.type.map { quoteValue(it) }
      Term.Var(value.name, toIdx(value.lvl), type)
    }

    is Value.Def        -> {
      val type = value.type.map { quoteValue(it) }
      Term.Def(value.def, type)
    }

    is Value.Meta       -> {
      val type = value.type.map { quoteValue(it) }
      Term.Meta(value.index, value.source, type)
    }

    is Value.Hole       -> {
      Term.Hole
    }
  }
}

operator fun Closure.invoke(args: List<Lazy<Value>>): Value {
  return (env + args).evalTerm(body)
}

fun Closure.open(
  next: Lvl,
  types: List<Lazy<Value>>,
): Value {
  return this(types.mapIndexed { i, type ->
    lazyOf(Value.Var("#${next + i}", next + i, type))
  })
}

infix fun Pattern.matches(value: Lazy<Value>): Boolean? {
  return when (this) {
    is Pattern.I32Of -> {
      val value = value.value as? Value.I32Of ?: return null
      this.value == value.value
    }
    is Pattern.Var   -> true
    is Pattern.Drop  -> true
    is Pattern.Hole  -> null
  }
}
