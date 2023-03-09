@file:Suppress("NAME_SHADOWING")

package mcx.phase

import kotlinx.collections.immutable.*
import mcx.ast.Core.Closure
import mcx.ast.Core.Pattern
import mcx.ast.Core.Term
import mcx.ast.Core.Value

operator fun Closure.invoke(
  args: List<Lazy<Value>>,
): Value {
  return (values + (binders zip args).flatMap { (binder, arg) -> bind(binder, arg) }).eval(body)
}

operator fun Closure.invoke(): Value {
  val vars = mutableListOf<Lazy<Value>>()
  fun go(pattern: Pattern) {
    when (pattern) {
      is Pattern.IntOf      -> {}
      is Pattern.CompoundOf -> {}
      is Pattern.Splice     -> go(pattern.element)
      is Pattern.Var        -> vars += lazyOf(Value.Var(pattern.name, values.size + vars.size))
      is Pattern.Drop       -> {}
      is Pattern.Hole       -> {}
    }
  }
  binders.forEach { go(it) }
  return (values + vars).eval(body)
}

fun PersistentList<Lazy<Value>>.eval(
  term: Term,
): Value {
  return when (term) {
    is Term.Tag         -> Value.Tag
    is Term.TagOf       -> Value.TagOf(term.value)
    is Term.Type        -> {
      val tag = lazy { eval(term) }
      Value.Type(tag)
    }
    is Term.Bool        -> Value.Bool
    is Term.BoolOf      -> Value.BoolOf(term.value)
    is Term.If          -> {
      when (val condition = eval(term.condition)) {
        is Value.BoolOf -> if (condition.value) eval(term.thenBranch) else eval(term.elseBranch)
        else            -> Value.If(condition, lazy { eval(term.thenBranch) }, lazy { eval(term.elseBranch) })
      }
    }
    is Term.Is          -> {
      val scrutinee = lazy { eval(term.scrutinee) }
      when (val result = match(term.scrutineer, scrutinee)) {
        null -> Value.Is(scrutinee, term.scrutineer)
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
      val elements = term.elements.map { lazy { eval(it) } }
      Value.ByteArrayOf(elements)
    }
    is Term.IntArray    -> Value.IntArray
    is Term.IntArrayOf  -> {
      val elements = term.elements.map { lazy { eval(it) } }
      Value.IntArrayOf(elements)
    }
    is Term.LongArray   -> Value.LongArray
    is Term.LongArrayOf -> {
      val elements = term.elements.map { lazy { eval(it) } }
      Value.LongArrayOf(elements)
    }
    is Term.List        -> {
      val element = lazy { eval(term.element) }
      Value.List(element)
    }
    is Term.ListOf      -> {
      val elements = term.elements.map { lazy { eval(it) } }
      Value.ListOf(elements)
    }
    is Term.Compound    -> {
      val elements = term.elements.mapValues { lazy { eval(it.value) } }
      Value.Compound(elements)
    }
    is Term.CompoundOf  -> {
      val elements = term.elements.mapValues { lazy { eval(it.value) } }
      Value.CompoundOf(elements)
    }
    is Term.Union       -> {
      val elements = term.elements.map { lazy { eval(it) } }
      Value.Union(elements)
    }
    is Term.Func        -> {
      val params = term.params.map { (_, type) -> lazy { eval(type) } }
      val result = Closure(this, term.params.map { (binder, _) -> binder }, term.result)
      Value.Func(params, result)
    }
    is Term.FuncOf      -> {
      val result = Closure(this, term.params, term.result)
      Value.FuncOf(result)
    }
    is Term.Apply       -> {
      val args = term.args.map { lazy { eval(it) } }
      when (val func = eval(term.func)) {
        is Value.FuncOf -> func.result(args)
        else            -> Value.Apply(func, args)
      }
    }
    is Term.Code        -> {
      val element = lazy { eval(term.element) }
      Value.Code(element)
    }
    is Term.CodeOf      -> {
      val element = lazy { eval(term.element) }
      Value.CodeOf(element)
    }
    is Term.Splice      -> {
      val element = lazy { eval(term.element) }
      Value.Splice(element)
    }
    is Term.Let         -> {
      val init = lazy { eval(term.init) }
      (this + bind(term.binder, init)).eval(term.body)
    }
    is Term.Var         -> this[term.level].value
    is Term.Def         -> eval(term.body)
    is Term.Meta        -> Value.Meta(term.index, term.source)
    is Term.Hole        -> Value.Hole
  }
}

fun bind(
  binder: Pattern,
  value: Lazy<Value>,
): List<Lazy<Value>> {
  val values = mutableListOf<Lazy<Value>>()
  fun go(
    binder: Pattern,
    value: Lazy<Value>,
  ) {
    when (binder) {
      is Pattern.IntOf      -> {}
      is Pattern.CompoundOf -> {
        val value = value.value
        if (value is Value.CompoundOf) {
          binder.elements.forEach { go(it.value, value.elements[it.key]!!) }
        }
      }
      is Pattern.Splice     -> {
        val value = value.value
        if (value is Value.CodeOf) {
          go(binder.element, value.element)
        }
      }
      is Pattern.Var        -> values += value
      is Pattern.Drop       -> {}
      is Pattern.Hole       -> {}
    }
  }
  go(binder, value)
  return values
}

fun match(
  binder: Pattern,
  value: Lazy<Value>,
): Boolean? {
  return when (binder) {
    is Pattern.IntOf      -> {
      val value = value.value as? Value.IntOf ?: return null
      binder.value == value.value
    }
    is Pattern.CompoundOf -> {
      val value = value.value as? Value.CompoundOf ?: return null
      binder.elements.all { (key, element) -> value.elements[key]?.let { match(element, it) } ?: false }
    }
    is Pattern.Splice     -> {
      val value = value.value as? Value.CodeOf ?: return null
      match(binder.element, value.element)
    }
    is Pattern.Var        -> true
    is Pattern.Drop       -> true
    is Pattern.Hole       -> null
  }
}

fun Int.quote(
  value: Value,
): Term {
  return when (value) {
    is Value.Tag         -> TODO()
    is Value.TagOf       -> TODO()
    is Value.Type        -> TODO()
    is Value.Bool        -> TODO()
    is Value.BoolOf      -> TODO()
    is Value.If          -> TODO()
    is Value.Is          -> TODO()
    is Value.Byte        -> TODO()
    is Value.ByteOf      -> TODO()
    is Value.Short       -> TODO()
    is Value.ShortOf     -> TODO()
    is Value.Int         -> TODO()
    is Value.IntOf       -> TODO()
    is Value.Long        -> TODO()
    is Value.LongOf      -> TODO()
    is Value.Float       -> TODO()
    is Value.FloatOf     -> TODO()
    is Value.Double      -> TODO()
    is Value.DoubleOf    -> TODO()
    is Value.String      -> TODO()
    is Value.StringOf    -> TODO()
    is Value.ByteArray   -> TODO()
    is Value.ByteArrayOf -> TODO()
    is Value.IntArray    -> TODO()
    is Value.IntArrayOf  -> TODO()
    is Value.LongArray   -> TODO()
    is Value.LongArrayOf -> TODO()
    is Value.List        -> TODO()
    is Value.ListOf      -> TODO()
    is Value.Compound    -> TODO()
    is Value.CompoundOf  -> TODO()
    is Value.Union       -> TODO()
    is Value.Func        -> TODO()
    is Value.FuncOf      -> TODO()
    is Value.Apply       -> TODO()
    is Value.Code        -> TODO()
    is Value.CodeOf      -> TODO()
    is Value.Splice      -> TODO()
    is Value.Var         -> TODO()
    is Value.Meta        -> TODO()
    is Value.Hole        -> TODO()
  }
}
