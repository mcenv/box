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
import mcx.util.mapWith

fun emptyEnv(): Env {
  return persistentListOf()
}

fun Env.evalTerm(term: Term): Value {
  return when (term) {
    is Term.Tag         -> Value.Tag
    is Term.TagOf       -> Value.TagOf(term.value)
    is Term.Type        -> {
      val tag = lazy { evalTerm(term.element) }
      Value.Type(tag)
    }
    is Term.Bool        -> Value.Bool
    is Term.BoolOf      -> Value.BoolOf(term.value)
    is Term.If          -> {
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
          Value.If(condition, thenBranch, elseBranch)
        }
      }
    }
    is Term.Is          -> {
      val scrutinee = lazy { evalTerm(term.scrutinee) }
      val scrutineer = evalPattern(term.scrutineer)
      when (val result = scrutineer matches scrutinee) {
        null -> Value.Is(scrutinee, scrutineer)
        else -> Value.BoolOf(result)
      }
    }
    is Term.I8          -> Value.I8
    is Term.I8Of        -> Value.I8Of(term.value)
    is Term.I16         -> Value.I16
    is Term.I16Of       -> Value.I16Of(term.value)
    is Term.I32         -> Value.I32
    is Term.I32Of       -> Value.I32Of(term.value)
    is Term.I64         -> Value.I64
    is Term.I64Of       -> Value.I64Of(term.value)
    is Term.F32         -> Value.F32
    is Term.F32Of       -> Value.F32Of(term.value)
    is Term.F64         -> Value.F64
    is Term.F64Of       -> Value.F64Of(term.value)
    is Term.Str         -> Value.Str
    is Term.StrOf       -> Value.StrOf(term.value)
    is Term.I8Array     -> Value.I8Array
    is Term.I8ArrayOf   -> {
      val elements = term.elements.map { lazy { evalTerm(it) } }
      Value.I8ArrayOf(elements)
    }
    is Term.I32Array    -> Value.I32Array
    is Term.I32ArrayOf  -> {
      val elements = term.elements.map { lazy { evalTerm(it) } }
      Value.I32ArrayOf(elements)
    }
    is Term.I64Array    -> Value.I64Array
    is Term.I64ArrayOf  -> {
      val elements = term.elements.map { lazy { evalTerm(it) } }
      Value.I64ArrayOf(elements)
    }
    is Term.Vec         -> {
      val element = lazy { evalTerm(term.element) }
      Value.Vec(element)
    }
    is Term.VecOf       -> {
      val elements = term.elements.map { lazy { evalTerm(it) } }
      Value.VecOf(elements)
    }
    is Term.Struct      -> {
      val elements = term.elements.mapValuesTo(linkedMapOf()) { lazy { evalTerm(it.value) } }
      Value.Struct(elements)
    }
    is Term.StructOf    -> {
      val elements = term.elements.mapValuesTo(linkedMapOf()) { lazy { evalTerm(it.value) } }
      Value.StructOf(elements)
    }
    is Term.Point       -> {
      val element = lazy { evalTerm(term.element) }
      val elementType = evalTerm(term.elementType)
      Value.Point(element, elementType)
    }
    is Term.Union       -> {
      val elements = term.elements.map { lazy { evalTerm(it) } }
      Value.Union(elements)
    }
    is Term.Func        -> {
      val (binders, params) = term.params.mapWith(this) { modify, (param, type) ->
        val type = lazyOf(evalTerm(type))
        val param = evalPattern(param)
        modify(this + next().collect(listOf(param)))
        param to type
      }.unzip()
      val result = Closure(this, binders, term.result)
      Value.Func(term.open, params, result)
    }
    is Term.FuncOf     -> {
      val binders = term.params.map { evalPattern(it) }
      val result = Closure(this, binders, term.result)
      Value.FuncOf(term.open, result)
    }
    is Term.Apply      -> {
      val func = evalTerm(term.func)
      val args = term.args.map { lazy { evalTerm(it) } }
      when (func) {
        is Value.FuncOf -> func.result(args)
        is Value.Def    -> lookupBuiltin(func.def.name)!!.eval(args)
        else            -> null
      } ?: run {
        val type = evalTerm(term.type)
        Value.Apply(term.open, func, args, type)
      }
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
    is Term.Command    -> {
      val element = lazy { evalTerm(term.element) }
      val type = evalTerm(term.type)
      Value.Command(element, type)
    }
    is Term.Let        -> {
      val init = lazy { evalTerm(term.init) }
      val binder = evalPattern(term.binder)
      (this + (binder binds init)).evalTerm(term.body)
    }
    is Term.Var        -> this[next().toLvl(term.idx).value].value
    is Term.Def        -> {
      // Builtin definitions have compiler-defined semantics and need to be handled specially.
      if (Modifier.BUILTIN in term.def.modifiers) {
        Value.Def(term.def)
      } else {
        evalTerm(term.def.body!!)
      }
    }
    is Term.Meta       -> Value.Meta(term.index, term.source)
    is Term.Hole       -> Value.Hole
  }
}

fun Lvl.quoteValue(value: Value): Term {
  return when (value) {
    is Value.Tag         -> Term.Tag
    is Value.TagOf       -> Term.TagOf(value.value)
    is Value.Type        -> {
      val tag = quoteValue(value.element.value)
      Term.Type(tag)
    }
    is Value.Bool        -> Term.Bool
    is Value.BoolOf      -> Term.BoolOf(value.value)
    is Value.If          -> {
      val condition = quoteValue(value.condition)
      val thenBranch = quoteValue(value.thenBranch.value)
      val elseBranch = quoteValue(value.elseBranch.value)
      Term.If(condition, thenBranch, elseBranch)
    }
    is Value.Is          -> {
      val scrutinee = quoteValue(value.scrutinee.value)
      val scrutineer = quotePattern(value.scrutineer)
      Term.Is(scrutinee, scrutineer)
    }
    is Value.I8          -> Term.I8
    is Value.I8Of        -> Term.I8Of(value.value)
    is Value.I16         -> Term.I16
    is Value.I16Of       -> Term.I16Of(value.value)
    is Value.I32         -> Term.I32
    is Value.I32Of       -> Term.I32Of(value.value)
    is Value.I64         -> Term.I64
    is Value.I64Of       -> Term.I64Of(value.value)
    is Value.F32         -> Term.F32
    is Value.F32Of       -> Term.F32Of(value.value)
    is Value.F64         -> Term.F64
    is Value.F64Of       -> Term.F64Of(value.value)
    is Value.Str         -> Term.Str
    is Value.StrOf       -> Term.StrOf(value.value)
    is Value.I8Array     -> Term.I8Array
    is Value.I8ArrayOf   -> {
      val elements = value.elements.map { quoteValue(it.value) }
      Term.I8ArrayOf(elements)
    }
    is Value.I32Array    -> Term.I32Array
    is Value.I32ArrayOf  -> {
      val elements = value.elements.map { quoteValue(it.value) }
      Term.I32ArrayOf(elements)
    }
    is Value.I64Array    -> Term.I64Array
    is Value.I64ArrayOf  -> {
      val elements = value.elements.map { quoteValue(it.value) }
      Term.I64ArrayOf(elements)
    }
    is Value.Vec         -> {
      val element = quoteValue(value.element.value)
      Term.Vec(element)
    }
    is Value.VecOf       -> {
      val elements = value.elements.map { quoteValue(it.value) }
      Term.VecOf(elements)
    }
    is Value.Struct      -> {
      val elements = value.elements.mapValuesTo(linkedMapOf()) { quoteValue(it.value.value) }
      Term.Struct(elements)
    }
    is Value.StructOf    -> {
      val elements = value.elements.mapValuesTo(linkedMapOf()) { quoteValue(it.value.value) }
      Term.StructOf(elements)
    }
    is Value.Point       -> {
      val element = quoteValue(value.element.value)
      val elementType = quoteValue(value.elementType)
      Term.Point(element, elementType)
    }
    is Value.Union       -> {
      val elements = value.elements.map { quoteValue(it.value) }
      Term.Union(elements)
    }
    is Value.Func        -> {
      // TODO: fix offsets
      val params = (value.result.binders zip value.params).map { (binder, param) ->
        val binder = quotePattern(binder)
        val param = quoteValue(param.value)
        binder to param
      }
      val result = quoteClosure(value.result)
      Term.Func(value.open, params, result)
    }
    is Value.FuncOf      -> {
      // TODO: fix offsets
      val params = value.result.binders.map { quotePattern(it) }
      val result = quoteClosure(value.result)
      Term.FuncOf(value.open, params, result)
    }
    is Value.Apply       -> {
      val func = quoteValue(value.func)
      val args = value.args.map { quoteValue(it.value) }
      val type = quoteValue(value.type)
      Term.Apply(value.open, func, args, type)
    }
    is Value.Code        -> {
      val element = quoteValue(value.element.value)
      Term.Code(element)
    }
    is Value.CodeOf      -> {
      val element = quoteValue(value.element.value)
      Term.CodeOf(element)
    }
    is Value.Splice  -> {
      val element = quoteValue(value.element)
      Term.Splice(element)
    }
    is Value.Command -> {
      val element = quoteValue(value.element.value)
      val type = quoteValue(value.type)
      Term.Command(element, type)
    }
    is Value.Let     -> error("unexpected value: $value")
    is Value.Var     -> {
      val type = quoteValue(value.type)
      Term.Var(value.name, toIdx(value.lvl), type)
    }
    is Value.Def     -> Term.Def(value.def)
    is Value.Meta    -> Term.Meta(value.index, value.source)
    is Value.Hole    -> Term.Hole
  }
}

fun Env.evalPattern(pattern: Pattern<Term>): Pattern<Value> {
  return when (pattern) {
    is Pattern.I32Of      -> pattern
    is Pattern.StructOf   -> {
      val elements = pattern.elements.mapValuesTo(linkedMapOf()) { (_, element) -> evalPattern(element) }
      Pattern.StructOf(elements)
    }
    is Pattern.Var        -> {
      val type = evalTerm(pattern.type)
      Pattern.Var(pattern.name, type)
    }
    is Pattern.Drop       -> {
      val type = evalTerm(pattern.type)
      Pattern.Drop(type)
    }
    is Pattern.Hole       -> pattern
  }
}

fun Lvl.quotePattern(pattern: Pattern<Value>): Pattern<Term> {
  return when (pattern) {
    is Pattern.I32Of      -> pattern
    is Pattern.StructOf   -> {
      val elements = pattern.elements.mapValuesTo(linkedMapOf()) { (_, element) -> quotePattern(element) }
      Pattern.StructOf(elements)
    }
    is Pattern.Var        -> {
      val type = quoteValue(pattern.type)
      Pattern.Var(pattern.name, type)
    }
    is Pattern.Drop       -> {
      val type = quoteValue(pattern.type)
      Pattern.Drop(type)
    }
    is Pattern.Hole       -> pattern
  }
}

fun Lvl.evalClosure(closure: Closure): Value {
  return closure(collect(closure.binders))
}

fun Lvl.quoteClosure(closure: Closure): Term {
  return collect(closure.binders).let { (this + it.size).quoteValue(closure(it)) }
}

operator fun Closure.invoke(args: List<Lazy<Value>>): Value {
  return (env + (binders zip args).flatMap { (binder, value) -> binder binds value }).evalTerm(body)
}

fun Lvl.collect(patterns: List<Pattern<Value>>): List<Lazy<Value>> {
  val vars = mutableListOf<Lazy<Value>>()
  fun go(pattern: Pattern<Value>) {
    when (pattern) {
      is Pattern.I32Of    -> {}
      is Pattern.StructOf -> pattern.elements.forEach { (_, element) -> go(element) }
      is Pattern.Var      -> vars += lazyOf(Value.Var(pattern.name, this + vars.size, pattern.type /* TODO: correctness */))
      is Pattern.Drop     -> {}
      is Pattern.Hole     -> {}
    }
  }
  patterns.forEach { go(it) }
  return vars
}

infix fun Pattern<*>.binds(value: Lazy<Value>): List<Lazy<Value>> {
  val values = mutableListOf<Lazy<Value>>()
  fun go(
    binder: Pattern<*>,
    value: Lazy<Value>,
  ) {
    when (binder) {
      is Pattern.I32Of    -> {}
      is Pattern.StructOf -> {
        val value = value.value
        if (value is Value.StructOf) {
          binder.elements.forEach { (key, element) -> go(element, value.elements[key]!!) }
        }
      }
      is Pattern.Var      -> values += value
      is Pattern.Drop     -> {}
      is Pattern.Hole     -> {}
    }
  }
  go(this, value)
  return values
}

infix fun Pattern<*>.matches(value: Lazy<Value>): Boolean? {
  return when (this) {
    is Pattern.I32Of    -> {
      val value = value.value as? Value.I32Of ?: return null
      this.value == value.value
    }
    is Pattern.StructOf -> {
      val value = value.value as? Value.StructOf ?: return null
      this.elements.all { (key, element) -> value.elements[key]?.let { element matches it } ?: false }
    }
    is Pattern.Var      -> true
    is Pattern.Drop     -> true
    is Pattern.Hole     -> null
  }
}
