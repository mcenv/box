package mcx.phase.frontend

import kotlinx.collections.immutable.*
import mcx.ast.Core.Pattern
import mcx.ast.Core.Term
import mcx.ast.Lvl
import mcx.phase.*
import org.eclipse.lsp4j.Range

@Suppress("NAME_SHADOWING")
class Meta {
  private val values: MutableList<Value?> = mutableListOf()
  private val _unsolvedMetas: MutableSet<Pair<Int, Range>> = hashSetOf()
  val unsolvedMetas: Set<Pair<Int, Range>> get() = _unsolvedMetas

  fun freshValue(source: Range): Value {
    return Value.Meta(values.size, source).also { values += null }
  }

  fun freshType(source: Range): Value {
    val tag = lazy { freshValue(source) }
    return Value.Type(tag)
  }

  tailrec fun forceValue(value: Value): Value {
    return when (value) {
      is Value.Meta ->
        when (val forced = values.getOrNull(value.index)) {
          null -> value
          else -> forceValue(forced)
        }
      else          -> value
    }
  }

  fun resetUnsolvedMetas() {
    _unsolvedMetas.clear()
  }

  fun Env.zonkTerm(term: Term): Term {
    return when (term) {
      is Term.Tag         -> term
      is Term.TagOf       -> term
      is Term.Type        -> {
        val tag = zonkTerm(term.element)
        Term.Type(tag)
      }
      is Term.Bool        -> term
      is Term.BoolOf      -> term
      is Term.If          -> {
        val condition = zonkTerm(term.condition)
        val thenBranch = zonkTerm(term.thenBranch)
        val elseBranch = zonkTerm(term.elseBranch)
        Term.If(condition, thenBranch, elseBranch)
      }
      is Term.Is          -> {
        val scrutinee = zonkTerm(term.scrutinee)
        val scrutineer = zonkPattern(term.scrutineer)
        Term.Is(scrutinee, scrutineer)
      }
      is Term.Byte        -> term
      is Term.ByteOf      -> term
      is Term.Short       -> term
      is Term.ShortOf     -> term
      is Term.Int         -> term
      is Term.IntOf       -> term
      is Term.Long        -> term
      is Term.LongOf      -> term
      is Term.Float       -> term
      is Term.FloatOf     -> term
      is Term.Double      -> term
      is Term.DoubleOf    -> term
      is Term.String      -> term
      is Term.StringOf    -> term
      is Term.ByteArray   -> term
      is Term.ByteArrayOf -> {
        val elements = term.elements.map { zonkTerm(it) }
        Term.ByteArrayOf(elements)
      }
      is Term.IntArray    -> term
      is Term.IntArrayOf  -> {
        val elements = term.elements.map { zonkTerm(it) }
        Term.IntArrayOf(elements)
      }
      is Term.LongArray   -> term
      is Term.LongArrayOf -> {
        val elements = term.elements.map { zonkTerm(it) }
        Term.LongArrayOf(elements)
      }
      is Term.List        -> {
        val element = zonkTerm(term.element)
        Term.List(element)
      }
      is Term.ListOf     -> {
        val elements = term.elements.map { zonkTerm(it) }
        Term.ListOf(elements)
      }
      is Term.Compound    -> {
        val elements = term.elements.mapValues { zonkTerm(it.value) }
        Term.Compound(elements)
      }
      is Term.CompoundOf -> {
        val elements = term.elements.mapValues { zonkTerm(it.value) }
        Term.CompoundOf(elements)
      }
      is Term.Union      -> {
        val elements = term.elements.map { zonkTerm(it) }
        Term.Union(elements)
      }
      is Term.Func       -> {
        val params = term.params.map { (param, type) ->
          val param = zonkPattern(param)
          val type = zonkTerm(type)
          param to type
        }
        val result = (this + Lvl(size).collect(params.map { (param, _) -> evalPattern(param) })).zonkTerm(term.result)
        Term.Func(params, result)
      }
      is Term.FuncOf     -> {
        val params = term.params.map { zonkPattern(it) }
        val result = (this + Lvl(size).collect(params.map { evalPattern(it) })).zonkTerm(term.result)
        Term.FuncOf(params, result)
      }
      is Term.Apply      -> {
        val func = zonkTerm(term.func)
        val args = term.args.map { zonkTerm(it) }
        Term.Apply(func, args)
      }
      is Term.Code       -> {
        val element = zonkTerm(term.element)
        Term.Code(element)
      }
      is Term.CodeOf     -> {
        val element = zonkTerm(term.element)
        Term.CodeOf(element)
      }
      is Term.Splice     -> {
        val element = zonkTerm(term.element)
        Term.Splice(element)
      }
      is Term.Let        -> {
        val binder = zonkPattern(term.binder)
        val init = zonkTerm(term.init)
        val body = zonkTerm(term.body)
        Term.Let(binder, init, body)
      }
      is Term.Var        -> {
        val type = zonkTerm(term.type)
        Term.Var(term.name, term.idx, type)
      }
      is Term.Def        -> {
        Term.Def(term.name, term.body)
      }
      is Term.Meta       -> {
        when (val solution = values.getOrNull(term.index)) {
          null -> {
            Term.Meta(term.index, term.source).also { _unsolvedMetas += it.index to it.source }
          }
          else -> zonkTerm(Lvl(size).quoteValue(solution))
        }
      }
      is Term.Hole        -> term
    }
  }

  fun Env.zonkPattern(pattern: Pattern<Term>): Pattern<Term> {
    return when (pattern) {
      is Pattern.IntOf      -> pattern
      is Pattern.CompoundOf -> {
        val elements = pattern.elements.map { (key, element) -> key to zonkPattern(element) }
        Pattern.CompoundOf(elements)
      }
      is Pattern.Var        -> {
        val type = zonkTerm(pattern.type)
        Pattern.Var(pattern.name, type)
      }
      is Pattern.Drop       -> {
        val type = zonkTerm(pattern.type)
        Pattern.Drop(type)
      }
      is Pattern.Hole       -> pattern
    }
  }

  fun Lvl.unifyValue(
    value1: Value,
    value2: Value,
  ): Boolean {
    val value1 = forceValue(value1)
    val value2 = forceValue(value2)
    return when {
      value1 is Value.Meta                                       -> {
        when (val solution1 = values[value1.index]) {
          null -> {
            values[value1.index] = value2
            true
          }
          else -> unifyValue(solution1, value2)
        }
      }
      value2 is Value.Meta                                       -> {
        when (val solution2 = values[value2.index]) {
          null -> {
            values[value2.index] = value1
            true
          }
          else -> unifyValue(value1, solution2)
        }
      }
      value1 is Value.Tag && value2 is Value.Tag                 -> true
      value1 is Value.TagOf && value2 is Value.TagOf             -> value1.value == value2.value
      value1 is Value.Type && value2 is Value.Type               -> unifyValue(value1.element.value, value2.element.value)
      value1 is Value.Bool && value2 is Value.Bool               -> true
      value1 is Value.BoolOf && value2 is Value.BoolOf           -> value1.value == value2.value
      value1 is Value.If && value2 is Value.If                   -> {
        unifyValue(value1.condition, value2.condition) &&
        unifyValue(value1.thenBranch.value, value2.elseBranch.value) &&
        unifyValue(value1.elseBranch.value, value2.elseBranch.value)
      }
      value1 is Value.Is && value2 is Value.Is                   -> {
        unifyValue(value1.scrutinee.value, value2.scrutinee.value) &&
        value1.scrutineer == value2.scrutineer // TODO: unify patterns
      }
      value1 is Value.Byte && value2 is Value.Byte               -> true
      value1 is Value.ByteOf && value2 is Value.ByteOf           -> value1.value == value2.value
      value1 is Value.Short && value2 is Value.Short             -> true
      value1 is Value.ShortOf && value2 is Value.ShortOf         -> value1.value == value2.value
      value1 is Value.Int && value2 is Value.Int                 -> true
      value1 is Value.IntOf && value2 is Value.IntOf             -> value1.value == value2.value
      value1 is Value.Long && value2 is Value.Long               -> true
      value1 is Value.LongOf && value2 is Value.LongOf           -> value1.value == value2.value
      value1 is Value.Float && value2 is Value.Float             -> true
      value1 is Value.FloatOf && value2 is Value.FloatOf         -> value1.value == value2.value
      value1 is Value.Double && value2 is Value.Double           -> true
      value1 is Value.DoubleOf && value2 is Value.DoubleOf       -> value1.value == value2.value
      value1 is Value.String && value2 is Value.String           -> true
      value1 is Value.StringOf && value2 is Value.StringOf       -> value1.value == value2.value
      value1 is Value.ByteArray && value2 is Value.ByteArray     -> true
      value1 is Value.ByteArrayOf && value2 is Value.ByteArrayOf -> {
        value1.elements.size == value2.elements.size &&
        (value1.elements zip value2.elements).all { (element1, element2) -> unifyValue(element1.value, element2.value) }
      }
      value1 is Value.IntArray && value2 is Value.IntArray       -> true
      value1 is Value.IntArrayOf && value2 is Value.IntArrayOf   -> {
        value1.elements.size == value2.elements.size &&
        (value1.elements zip value2.elements).all { (element1, element2) -> unifyValue(element1.value, element2.value) }
      }
      value1 is Value.LongArray && value2 is Value.LongArray     -> true
      value1 is Value.LongArrayOf && value2 is Value.LongArrayOf -> {
        value1.elements.size == value2.elements.size &&
        (value1.elements zip value2.elements).all { (element1, element2) -> unifyValue(element1.value, element2.value) }
      }
      value1 is Value.List && value2 is Value.List               -> unifyValue(value1.element.value, value2.element.value)
      value1 is Value.ListOf && value2 is Value.ListOf           -> {
        value1.elements.size == value2.elements.size &&
        (value1.elements zip value2.elements).all { (element1, element2) -> unifyValue(element1.value, element2.value) }
      }
      value1 is Value.Compound && value2 is Value.Compound       -> {
        value1.elements.size == value2.elements.size &&
        value1.elements.all { (key1, element1) ->
          when (val element2 = value2.elements[key1]) {
            null -> false
            else -> unifyValue(element1.value, element2.value)
          }
        }
      }
      value1 is Value.Union && value2 is Value.Union             -> value1.elements.isEmpty() && value2.elements.isEmpty() // TODO
      value1 is Value.Func && value2 is Value.Func               -> {
        value1.params.size == value2.params.size &&
        (value1.params zip value2.params).all { (param1, param2) -> unifyValue(param1.value, param2.value) } &&
        unifyValue(evalClosure(value1.result), evalClosure(value2.result))
      }
      value1 is Value.FuncOf && value2 is Value.FuncOf           -> {
        unifyValue(evalClosure(value1.result), evalClosure(value2.result))
      }
      value1 is Value.Apply && value2 is Value.Apply             -> {
        unifyValue(value1.func, value2.func) &&
        value1.args.size == value2.args.size &&
        (value1.args zip value2.args).all { (arg1, arg2) -> unifyValue(arg1.value, arg2.value) }
      }
      value1 is Value.Code && value2 is Value.Code               -> unifyValue(value1.element.value, value2.element.value)
      value1 is Value.CodeOf && value2 is Value.CodeOf           -> unifyValue(value1.element.value, value2.element.value)
      value1 is Value.Splice && value2 is Value.Splice           -> unifyValue(value1.element, value2.element)
      value1 is Value.Var && value2 is Value.Var                 -> value1.lvl == value2.lvl
      // TODO: unify [Value.Def]s
      value1 is Value.Hole || value2 is Value.Hole               -> true // ?
      else                                                       -> false
    }
  }
}
