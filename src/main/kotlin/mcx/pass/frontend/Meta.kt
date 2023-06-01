package mcx.pass.frontend

import kotlinx.collections.immutable.plus
import mcx.ast.Core.Pattern
import mcx.ast.Core.Term
import mcx.ast.Lvl
import mcx.lsp.diagnostic
import mcx.pass.*
import mcx.util.toSubscript
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Range

@Suppress("NAME_SHADOWING")
class Meta {
  private val values: MutableList<Value?> = mutableListOf()
  private val unsolvedMetas: MutableSet<Pair<Int, Range>> = hashSetOf()

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

  fun checkSolved(
    diagnostics: MutableList<Diagnostic>,
    vararg terms: Term?,
  ): List<Term?> {
    unsolvedMetas.clear()
    val zonkedTerms = terms.map { it?.let { emptyEnv().zonkTerm(it) } }
    unsolvedMetas.forEach { (index, source) ->
      diagnostics += unsolvedMeta(index, source)
    }
    return zonkedTerms
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
      is Term.I8          -> term
      is Term.I8Of        -> term
      is Term.I16         -> term
      is Term.I16Of       -> term
      is Term.I32         -> term
      is Term.I32Of       -> term
      is Term.I64         -> term
      is Term.I64Of       -> term
      is Term.F32         -> term
      is Term.F32Of       -> term
      is Term.F64         -> term
      is Term.F64Of       -> term
      is Term.Str         -> term
      is Term.StrOf       -> term
      is Term.I8Array     -> term
      is Term.I8ArrayOf   -> {
        val elements = term.elements.map { zonkTerm(it) }
        Term.I8ArrayOf(elements)
      }
      is Term.I32Array    -> term
      is Term.I32ArrayOf  -> {
        val elements = term.elements.map { zonkTerm(it) }
        Term.I32ArrayOf(elements)
      }
      is Term.I64Array    -> term
      is Term.I64ArrayOf  -> {
        val elements = term.elements.map { zonkTerm(it) }
        Term.I64ArrayOf(elements)
      }
      is Term.Vec         -> {
        val element = zonkTerm(term.element)
        Term.Vec(element)
      }
      is Term.VecOf       -> {
        val elements = term.elements.map { zonkTerm(it) }
        Term.VecOf(elements)
      }
      is Term.Struct      -> {
        val elements = term.elements.mapValuesTo(linkedMapOf()) { zonkTerm(it.value) }
        Term.Struct(elements)
      }
      is Term.StructOf    -> {
        val elements = term.elements.mapValuesTo(linkedMapOf()) { zonkTerm(it.value) }
        Term.StructOf(elements)
      }
      is Term.Point       -> {
        val element = zonkTerm(term.element)
        val elementType = zonkTerm(term.elementType)
        Term.Point(element, elementType)
      }
      is Term.Union       -> {
        val elements = term.elements.map { zonkTerm(it) }
        Term.Union(elements)
      }
      is Term.Func        -> {
        var env = this
        val params = term.params.map { (param, type) ->
          val type = env.zonkTerm(type)
          val param = env.zonkPattern(param)
          env += env.next().collect(listOf(env.evalPattern(param)))
          param to type
        }
        val result = env.zonkTerm(term.result)
        Term.Func(term.open, params, result)
      }
      is Term.FuncOf      -> {
        var env = this
        val params = term.params.map { param ->
          val param = zonkPattern(param)
          env += env.next().collect(listOf(env.evalPattern(param)))
          param
        }
        val result = env.zonkTerm(term.result)
        Term.FuncOf(term.open, params, result)
      }
      is Term.Apply       -> {
        val func = zonkTerm(term.func)
        val args = term.args.map { zonkTerm(it) }
        val type = zonkTerm(term.type)
        Term.Apply(term.open, func, args, type)
      }
      is Term.Code        -> {
        val element = zonkTerm(term.element)
        Term.Code(element)
      }
      is Term.CodeOf      -> {
        val element = zonkTerm(term.element)
        Term.CodeOf(element)
      }
      is Term.Splice      -> {
        val element = zonkTerm(term.element)
        Term.Splice(element)
      }
      is Term.Command -> {
        val element = zonkTerm(term.element)
        val type = zonkTerm(term.type)
        Term.Command(element, type)
      }
      is Term.Let     -> {
        val binder = zonkPattern(term.binder)
        val init = zonkTerm(term.init)
        val body = zonkTerm(term.body)
        Term.Let(binder, init, body)
      }
      is Term.Var     -> {
        val type = zonkTerm(term.type)
        Term.Var(term.name, term.idx, type)
      }
      is Term.Def     -> Term.Def(term.def)
      is Term.Meta    -> {
        when (val solution = values.getOrNull(term.index)) {
          null -> {
            Term.Meta(term.index, term.source).also { unsolvedMetas += it.index to it.source }
          }
          else -> zonkTerm(next().quoteValue(solution))
        }
      }
      is Term.Hole    -> term
    }
  }

  fun Env.zonkPattern(pattern: Pattern<Term>): Pattern<Term> {
    return when (pattern) {
      is Pattern.I32Of      -> pattern
      is Pattern.StructOf   -> {
        val elements = pattern.elements.mapValuesTo(linkedMapOf()) { (_, element) -> zonkPattern(element) }
        Pattern.StructOf(elements)
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
      value1 is Value.Meta                                     -> {
        when (val solution1 = values[value1.index]) {
          null -> {
            values[value1.index] = value2
            true
          }
          else -> unifyValue(solution1, value2)
        }
      }
      value2 is Value.Meta                                     -> {
        when (val solution2 = values[value2.index]) {
          null -> {
            values[value2.index] = value1
            true
          }
          else -> unifyValue(value1, solution2)
        }
      }
      value1 is Value.Tag && value2 is Value.Tag               -> true
      value1 is Value.TagOf && value2 is Value.TagOf           -> value1.value == value2.value
      value1 is Value.Type && value2 is Value.Type             -> unifyValue(value1.element.value, value2.element.value)
      value1 is Value.Bool && value2 is Value.Bool             -> true
      value1 is Value.BoolOf && value2 is Value.BoolOf         -> value1.value == value2.value
      value1 is Value.If && value2 is Value.If                 -> {
        unifyValue(value1.condition, value2.condition) &&
        unifyValue(value1.thenBranch.value, value2.elseBranch.value) &&
        unifyValue(value1.elseBranch.value, value2.elseBranch.value)
      }
      value1 is Value.Is && value2 is Value.Is                 -> {
        unifyValue(value1.scrutinee.value, value2.scrutinee.value) &&
        value1.scrutineer == value2.scrutineer // TODO: unify patterns
      }
      value1 is Value.I8 && value2 is Value.I8                 -> true
      value1 is Value.I8Of && value2 is Value.I8Of             -> value1.value == value2.value
      value1 is Value.I16 && value2 is Value.I16               -> true
      value1 is Value.I16Of && value2 is Value.I16Of           -> value1.value == value2.value
      value1 is Value.I32 && value2 is Value.I32               -> true
      value1 is Value.I32Of && value2 is Value.I32Of           -> value1.value == value2.value
      value1 is Value.I64 && value2 is Value.I64               -> true
      value1 is Value.I64Of && value2 is Value.I64Of           -> value1.value == value2.value
      value1 is Value.F32 && value2 is Value.F32               -> true
      value1 is Value.F32Of && value2 is Value.F32Of           -> value1.value == value2.value
      value1 is Value.F64 && value2 is Value.F64               -> true
      value1 is Value.F64Of && value2 is Value.F64Of           -> value1.value == value2.value
      value1 is Value.Str && value2 is Value.Str               -> true
      value1 is Value.StrOf && value2 is Value.StrOf           -> value1.value == value2.value
      value1 is Value.I8Array && value2 is Value.I8Array       -> true
      value1 is Value.I8ArrayOf && value2 is Value.I8ArrayOf   -> {
        value1.elements.size == value2.elements.size &&
        (value1.elements zip value2.elements).all { (element1, element2) -> unifyValue(element1.value, element2.value) }
      }
      value1 is Value.I32Array && value2 is Value.I32Array     -> true
      value1 is Value.I32ArrayOf && value2 is Value.I32ArrayOf -> {
        value1.elements.size == value2.elements.size &&
        (value1.elements zip value2.elements).all { (element1, element2) -> unifyValue(element1.value, element2.value) }
      }
      value1 is Value.I64Array && value2 is Value.I64Array     -> true
      value1 is Value.I64ArrayOf && value2 is Value.I64ArrayOf -> {
        value1.elements.size == value2.elements.size &&
        (value1.elements zip value2.elements).all { (element1, element2) -> unifyValue(element1.value, element2.value) }
      }
      value1 is Value.Vec && value2 is Value.Vec               -> unifyValue(value1.element.value, value2.element.value)
      value1 is Value.VecOf && value2 is Value.VecOf           -> {
        value1.elements.size == value2.elements.size &&
        (value1.elements zip value2.elements).all { (element1, element2) -> unifyValue(element1.value, element2.value) }
      }
      value1 is Value.Struct && value2 is Value.Struct         -> {
        value1.elements.size == value2.elements.size &&
        value1.elements.all { (key1, element1) ->
          value2.elements[key1]?.let { element2 -> unifyValue(element1.value, element2.value) } ?: false
        }
      }
      value1 is Value.StructOf && value2 is Value.StructOf     -> {
        value1.elements.size == value2.elements.size &&
        value1.elements.all { (key1, element1) ->
          value2.elements[key1]?.let { element2 -> unifyValue(element1.value, element2.value) } ?: false
        }
      }
      value1 is Value.Point && value2 is Value.Point           -> unifyValue(value1.element.value, value2.element.value)
      value1 is Value.Union && value2 is Value.Union           -> value1.elements.isEmpty() && value2.elements.isEmpty() // TODO
      value1 is Value.Func && value2 is Value.Func             -> {
        value1.open == value2.open &&
        value1.params.size == value2.params.size &&
        (value1.params zip value2.params).all { (param1, param2) -> unifyValue(param1.value, param2.value) } &&
        unifyValue(evalClosure(value1.result), evalClosure(value2.result))
      }
      value1 is Value.FuncOf && value2 is Value.FuncOf         -> {
        value1.open == value2.open &&
        unifyValue(evalClosure(value1.result), evalClosure(value2.result))
      }
      value1 is Value.Apply && value2 is Value.Apply           -> {
        unifyValue(value1.func, value2.func) &&
        value1.args.size == value2.args.size &&
        (value1.args zip value2.args).all { (arg1, arg2) -> unifyValue(arg1.value, arg2.value) }
      }
      value1 is Value.Code && value2 is Value.Code             -> unifyValue(value1.element.value, value2.element.value)
      value1 is Value.CodeOf && value2 is Value.CodeOf         -> unifyValue(value1.element.value, value2.element.value)
      value1 is Value.Splice && value2 is Value.Splice         -> unifyValue(value1.element, value2.element)
      value1 is Value.Command && value2 is Value.Command       -> unifyValue(value1.element.value, value2.element.value)
      value1 is Value.Var && value2 is Value.Var               -> value1.lvl == value2.lvl
      value1 is Value.Def && value2 is Value.Def               -> value1.def.name == value2.def.name // TODO: check body
      value1 is Value.Hole || value2 is Value.Hole             -> true // ?
      else                                                     -> false
    }
  }

  companion object {
    private fun unsolvedMeta(
      index: Int,
      range: Range,
    ): Diagnostic {
      return diagnostic(
        range,
        "unsolved meta: ?${index.toSubscript()}",
        DiagnosticSeverity.Error,
      )
    }
  }
}
