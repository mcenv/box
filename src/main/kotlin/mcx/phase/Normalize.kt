@file:Suppress("NAME_SHADOWING")

package mcx.phase

import kotlinx.collections.immutable.*
import mcx.ast.Core.Pattern
import mcx.ast.Core.Term
import mcx.ast.Lvl
import mcx.ast.toIdx
import mcx.ast.toLvl

operator fun Closure.invoke(
  args: List<Lazy<Value>>,
): Value {
  return (env + (binders zip args).flatMap { (binder, arg) -> bind(binder, arg) }).eval(body)
}

fun Lvl.collect(
  patterns: List<Pattern>,
): List<Lazy<Value>> {
  val vars = mutableListOf<Lazy<Value>>()
  fun go(pattern: Pattern) {
    when (pattern) {
      is Pattern.IntOf      -> {}
      is Pattern.CompoundOf -> {}
      is Pattern.CodeOf     -> go(pattern.element)
      is Pattern.Var        -> vars += lazyOf(Value.Var(pattern.name, this + vars.size))
      is Pattern.Drop       -> {}
      is Pattern.Hole       -> {}
    }
  }
  patterns.forEach { go(it) }
  return vars
}

fun Env.eval(
  term: Term,
): Value {
  return when (term) {
    is Term.Tag         -> Value.Tag
    is Term.TagOf       -> Value.TagOf(term.value)
    is Term.Type        -> {
      val tag = lazy { eval(term.tag) }
      Value.Type(tag)
    }
    is Term.Bool        -> Value.Bool
    is Term.BoolOf      -> Value.BoolOf(term.value)
    is Term.If          -> {
      when (val condition = eval(term.condition)) {
        is Value.BoolOf -> {
          if (condition.value) {
            eval(term.thenBranch)
          } else {
            eval(term.elseBranch)
          }
        }
        else            -> {
          val thenBranch = lazy { eval(term.thenBranch) }
          val elseBranch = lazy { eval(term.elseBranch) }
          Value.If(condition, thenBranch, elseBranch)
        }
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
      val func = eval(term.func)
      val args = term.args.map { lazy { eval(it) } }
      when (func) {
        is Value.FuncOf -> func.result(args)
        is Value.Def    -> lookupBuiltin(func.name)!!.eval(args) ?: Value.Apply(func, args)
        else            -> Value.Apply(func, args)
      }
    }
    is Term.Code        -> {
      val element = lazy { eval(term.element) }
      Value.Code(element)
    }
    is Term.CodeOf -> {
      val element = lazy { eval(term.element) }
      Value.CodeOf(element)
    }
    is Term.Splice -> {
      when (val element = eval(term.element)) {
        is Value.CodeOf -> element.element.value
        else            -> Value.Splice(element)
      }
    }
    is Term.Let    -> {
      val init = lazy { eval(term.init) }
      (this + bind(term.binder, init)).eval(term.body)
    }
    is Term.Var    -> this[Lvl(size).toLvl(term.idx).value].value
    is Term.Def    -> term.body?.let { eval(it) } ?: Value.Def(term.name, null)
    is Term.Meta   -> Value.Meta(term.index, term.source)
    is Term.Hole   -> Value.Hole
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
          binder.elements.forEach { (key, element) -> go(element, value.elements[key]!!) }
        }
      }
      is Pattern.CodeOf     -> {
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
    is Pattern.CodeOf     -> {
      val value = value.value as? Value.CodeOf ?: return null
      match(binder.element, value.element)
    }
    is Pattern.Var        -> true
    is Pattern.Drop       -> true
    is Pattern.Hole       -> null
  }
}

fun Lvl.quote(
  value: Value,
): Term {
  return when (value) {
    is Value.Tag     -> Term.Tag
    is Value.TagOf   -> Term.TagOf(value.value)
    is Value.Type    -> {
      val tag = quote(value.tag.value)
      Term.Type(tag)
    }
    is Value.Bool    -> Term.Bool
    is Value.BoolOf  -> Term.BoolOf(value.value)
    is Value.If      -> {
      val condition = quote(value.condition)
      val thenBranch = quote(value.thenBranch.value)
      val elseBranch = quote(value.elseBranch.value)
      Term.If(condition, thenBranch, elseBranch)
    }
    is Value.Is      -> {
      val scrutinee = quote(value.scrutinee.value)
      Term.Is(scrutinee, value.scrutineer)
    }
    is Value.Byte    -> Term.Byte
    is Value.ByteOf  -> Term.ByteOf(value.value)
    is Value.Short   -> Term.Short
    is Value.ShortOf -> Term.ShortOf(value.value)
    is Value.Int     -> Term.Int
    is Value.IntOf   -> Term.IntOf(value.value)
    is Value.Long    -> Term.Long
    is Value.LongOf  -> Term.LongOf(value.value)
    is Value.Float   -> Term.Float
    is Value.FloatOf -> Term.FloatOf(value.value)
    is Value.Double  -> Term.Double
    is Value.DoubleOf    -> Term.DoubleOf(value.value)
    is Value.String      -> Term.String
    is Value.StringOf    -> Term.StringOf(value.value)
    is Value.ByteArray   -> Term.ByteArray
    is Value.ByteArrayOf -> {
      val elements = value.elements.map { quote(it.value) }
      Term.ByteArrayOf(elements)
    }
    is Value.IntArray    -> Term.IntArray
    is Value.IntArrayOf  -> {
      val elements = value.elements.map { quote(it.value) }
      Term.IntArrayOf(elements)
    }
    is Value.LongArray   -> Term.LongArray
    is Value.LongArrayOf -> {
      val elements = value.elements.map { quote(it.value) }
      Term.LongArrayOf(elements)
    }
    is Value.List        -> {
      val element = quote(value.element.value)
      Term.List(element)
    }
    is Value.ListOf      -> {
      val elements = value.elements.map { quote(it.value) }
      Term.ListOf(elements)
    }
    is Value.Compound    -> {
      val elements = value.elements.mapValues { quote(it.value.value) }
      Term.Compound(elements)
    }
    is Value.CompoundOf  -> {
      val elements = value.elements.mapValues { quote(it.value.value) }
      Term.CompoundOf(elements)
    }
    is Value.Union       -> {
      val elements = value.elements.map { quote(it.value) }
      Term.Union(elements)
    }
    is Value.Func    -> {
      val params = (value.result.binders zip value.params).map { (binder, param) ->
        val param = quote(param.value)
        binder to param
      }
      val result = collect(value.result.binders).let { (this + it.size).quote(value.result(it)) }
      Term.Func(params, result)
    }
    is Value.FuncOf  -> {
      val result = collect(value.result.binders).let { (this + it.size).quote(value.result(it)) }
      Term.FuncOf(value.result.binders, result)
    }
    is Value.Apply   -> {
      val func = quote(value.func)
      val args = value.args.map { quote(it.value) }
      Term.Apply(func, args)
    }
    is Value.Code    -> {
      val element = quote(value.element.value)
      Term.Code(element)
    }
    is Value.CodeOf  -> {
      val element = quote(value.element.value)
      Term.CodeOf(element)
    }
    is Value.Splice  -> {
      val element = quote(value.element)
      Term.Splice(element)
    }
    is Value.Let     -> {
      val init = quote(value.init)
      val body = collect(listOf(value.binder)).let { (this + it.size).quote(value.body) }
      Term.Let(value.binder, init, body)
    }
    is Value.Var     -> Term.Var(value.name, toIdx(value.lvl))
    is Value.Def     -> Term.Def(value.name, value.body)
    is Value.Meta    -> Term.Meta(value.index, value.source)
    is Value.Hole    -> Term.Hole
  }
}
