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
    is Term.Byte        -> Value.Byte
    is Term.ByteOf      -> Value.ByteOf(term.value)
    is Term.Short       -> Value.Short
    is Term.ShortOf     -> Value.ShortOf(term.value)
    is Term.Int         -> Value.Int
    is Term.IntOf       -> Value.IntOf(term.value)
    is Term.Long        -> Value.Long
    is Term.LongOf      -> Value.LongOf(term.value)
    is Term.Float       -> Value.Float
    is Term.FloatOf     -> Value.FloatOf(term.value)
    is Term.Double      -> Value.Double
    is Term.DoubleOf    -> Value.DoubleOf(term.value)
    is Term.String      -> Value.String
    is Term.StringOf    -> Value.StringOf(term.value)
    is Term.ByteArray   -> Value.ByteArray
    is Term.ByteArrayOf -> {
      val elements = term.elements.map { lazy { evalTerm(it) } }
      Value.ByteArrayOf(elements)
    }
    is Term.IntArray    -> Value.IntArray
    is Term.IntArrayOf  -> {
      val elements = term.elements.map { lazy { evalTerm(it) } }
      Value.IntArrayOf(elements)
    }
    is Term.LongArray   -> Value.LongArray
    is Term.LongArrayOf -> {
      val elements = term.elements.map { lazy { evalTerm(it) } }
      Value.LongArrayOf(elements)
    }
    is Term.List        -> {
      val element = lazy { evalTerm(term.element) }
      Value.List(element)
    }
    is Term.ListOf      -> {
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
      val element = lazy { evalTerm(term.element) }
      val elementType = evalTerm(term.elementType)
      Value.Point(element, elementType)
    }
    is Term.Union      -> {
      val elements = term.elements.map { lazy { evalTerm(it) } }
      Value.Union(elements)
    }
    is Term.Func       -> {
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
    is Value.Byte        -> Term.Byte
    is Value.ByteOf      -> Term.ByteOf(value.value)
    is Value.Short       -> Term.Short
    is Value.ShortOf     -> Term.ShortOf(value.value)
    is Value.Int         -> Term.Int
    is Value.IntOf       -> Term.IntOf(value.value)
    is Value.Long        -> Term.Long
    is Value.LongOf      -> Term.LongOf(value.value)
    is Value.Float       -> Term.Float
    is Value.FloatOf     -> Term.FloatOf(value.value)
    is Value.Double      -> Term.Double
    is Value.DoubleOf    -> Term.DoubleOf(value.value)
    is Value.String      -> Term.String
    is Value.StringOf    -> Term.StringOf(value.value)
    is Value.ByteArray   -> Term.ByteArray
    is Value.ByteArrayOf -> {
      val elements = value.elements.map { quoteValue(it.value) }
      Term.ByteArrayOf(elements)
    }
    is Value.IntArray    -> Term.IntArray
    is Value.IntArrayOf  -> {
      val elements = value.elements.map { quoteValue(it.value) }
      Term.IntArrayOf(elements)
    }
    is Value.LongArray   -> Term.LongArray
    is Value.LongArrayOf -> {
      val elements = value.elements.map { quoteValue(it.value) }
      Term.LongArrayOf(elements)
    }
    is Value.List        -> {
      val element = quoteValue(value.element.value)
      Term.List(element)
    }
    is Value.ListOf      -> {
      val elements = value.elements.map { quoteValue(it.value) }
      Term.ListOf(elements)
    }
    is Value.Compound    -> {
      val elements = value.elements.mapValuesTo(linkedMapOf()) { quoteValue(it.value.value) }
      Term.Compound(elements)
    }
    is Value.CompoundOf  -> {
      val elements = value.elements.mapValuesTo(linkedMapOf()) { quoteValue(it.value.value) }
      Term.CompoundOf(elements)
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
    is Pattern.IntOf      -> pattern
    is Pattern.CompoundOf -> {
      val elements = pattern.elements.mapValuesTo(linkedMapOf()) { (_, element) -> evalPattern(element) }
      Pattern.CompoundOf(elements)
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
    is Pattern.IntOf      -> pattern
    is Pattern.CompoundOf -> {
      val elements = pattern.elements.mapValuesTo(linkedMapOf()) { (_, element) -> quotePattern(element) }
      Pattern.CompoundOf(elements)
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
      is Pattern.IntOf      -> {}
      is Pattern.CompoundOf -> pattern.elements.forEach { (_, element) -> go(element) }
      is Pattern.Var        -> vars += lazyOf(Value.Var(pattern.name, this + vars.size, pattern.type /* TODO: correctness */))
      is Pattern.Drop       -> {}
      is Pattern.Hole       -> {}
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
      is Pattern.IntOf      -> {}
      is Pattern.CompoundOf -> {
        val value = value.value
        if (value is Value.CompoundOf) {
          binder.elements.forEach { (key, element) -> go(element, value.elements[key]!!) }
        }
      }
      is Pattern.Var        -> values += value
      is Pattern.Drop       -> {}
      is Pattern.Hole       -> {}
    }
  }
  go(this, value)
  return values
}

infix fun Pattern<*>.matches(value: Lazy<Value>): Boolean? {
  return when (this) {
    is Pattern.IntOf      -> {
      val value = value.value as? Value.IntOf ?: return null
      this.value == value.value
    }
    is Pattern.CompoundOf -> {
      val value = value.value as? Value.CompoundOf ?: return null
      this.elements.all { (key, element) -> value.elements[key]?.let { element matches it } ?: false }
    }
    is Pattern.Var        -> true
    is Pattern.Drop       -> true
    is Pattern.Hole       -> null
  }
}
