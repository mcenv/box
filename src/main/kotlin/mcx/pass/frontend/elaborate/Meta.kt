package mcx.pass.frontend.elaborate

import mcx.ast.Core.Term
import mcx.ast.Lvl
import mcx.lsp.diagnostic
import mcx.pass.Value
import mcx.util.collections.mapWith
import mcx.util.toSubscript
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Range

@Suppress("NAME_SHADOWING")
class Meta {
  private val values: MutableList<Value?> = mutableListOf()
  private val unsolvedMetas: MutableSet<Pair<Int, Range>> = hashSetOf()

  fun freshValue(
    source: Range,
    type: Lazy<Value> = lazy { freshType(source) },
  ): Value {
    return Value.Meta(values.size, source, type).also { values += null }
  }

  fun freshType(source: Range): Value {
    val tag = lazy { freshValue(source, Value.Tag.LAZY) }
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
    val zonkedTerms = terms.map { it?.let { Lvl(0).zonkTerm(it) } }
    unsolvedMetas.forEach { (index, source) ->
      diagnostics += unsolvedMeta(index, source)
    }
    return zonkedTerms
  }

  fun Lvl.zonkTerm(term: Term): Term {
    return when (term) {
      is Term.Tag        -> {
        term
      }

      is Term.TagOf      -> {
        term
      }

      is Term.Type       -> {
        val tag = zonkTerm(term.element)
        Term.Type(tag)
      }

      is Term.Bool       -> {
        term
      }

      is Term.BoolOf     -> {
        term
      }

      is Term.If         -> {
        val condition = zonkTerm(term.condition)
        val thenBranch = zonkTerm(term.thenBranch)
        val elseBranch = zonkTerm(term.elseBranch)
        val type = zonkTerm(term.type)
        Term.If(condition, thenBranch, elseBranch, type)
      }

      is Term.I8         -> {
        term
      }

      is Term.I8Of       -> {
        term
      }

      is Term.I16        -> {
        term
      }

      is Term.I16Of      -> {
        term
      }

      is Term.I32        -> {
        term
      }

      is Term.I32Of      -> {
        term
      }

      is Term.I64        -> {
        term
      }

      is Term.I64Of      -> {
        term
      }

      is Term.F32        -> {
        term
      }

      is Term.F32Of      -> {
        term
      }

      is Term.F64        -> {
        term
      }

      is Term.F64Of      -> {
        term
      }

      is Term.Str        -> {
        term
      }

      is Term.StrOf      -> {
        term
      }

      is Term.I8Array    -> {
        term
      }

      is Term.I8ArrayOf  -> {
        val elements = term.elements.map { zonkTerm(it) }
        Term.I8ArrayOf(elements)
      }

      is Term.I32Array   -> {
        term
      }

      is Term.I32ArrayOf -> {
        val elements = term.elements.map { zonkTerm(it) }
        Term.I32ArrayOf(elements)
      }

      is Term.I64Array   -> {
        term
      }

      is Term.I64ArrayOf -> {
        val elements = term.elements.map { zonkTerm(it) }
        Term.I64ArrayOf(elements)
      }

      is Term.Vec      -> {
        val element = zonkTerm(term.element)
        Term.Vec(element)
      }

      is Term.VecOf    -> {
        val elements = term.elements.map { zonkTerm(it) }
        val type = zonkTerm(term.type)
        Term.VecOf(elements, type)
      }

      is Term.Struct   -> {
        val elements = term.elements.mapValuesTo(linkedMapOf()) { zonkTerm(it.value) }
        Term.Struct(elements)
      }

      is Term.StructOf -> {
        val elements = term.elements.mapValuesTo(linkedMapOf()) { zonkTerm(it.value) }
        val type = zonkTerm(term.type)
        Term.StructOf(elements, type)
      }

      is Term.Ref      -> {
        val element = zonkTerm(term.element)
        Term.Ref(element)
      }

      is Term.RefOf    -> {
        val element = zonkTerm(term.element)
        val type = zonkTerm(term.type)
        Term.RefOf(element, type)
      }

      is Term.Point    -> {
        val element = zonkTerm(term.element)
        val type = zonkTerm(term.type)
        Term.Point(element, type)
      }

      is Term.Union    -> {
        val elements = term.elements.map { zonkTerm(it) }
        val type = zonkTerm(term.type)
        Term.Union(elements, type)
      }

      is Term.Func     -> {
        val (lvl, params) = term.params.mapWith(this) { transform, (param, type) ->
          val type = zonkTerm(type)
          transform(this + 1)
          param to type
        }
        val result = lvl.zonkTerm(term.result)
        Term.Func(term.open, params, result)
      }

      is Term.FuncOf     -> {
        val params = term.params
        val result = (this + params.size).zonkTerm(term.result)
        val type = zonkTerm(term.type)
        Term.FuncOf(term.open, params, result, type)
      }

      is Term.Apply      -> {
        val func = zonkTerm(term.func)
        val args = term.args.map { zonkTerm(it) }
        val type = zonkTerm(term.type)
        Term.Apply(term.open, func, args, type)
      }

      is Term.Code       -> {
        val element = zonkTerm(term.element)
        Term.Code(element)
      }

      is Term.CodeOf     -> {
        val element = zonkTerm(term.element)
        val type = zonkTerm(term.type)
        Term.CodeOf(element, type)
      }

      is Term.Splice   -> {
        val element = zonkTerm(term.element)
        val type = zonkTerm(term.type)
        Term.Splice(element, type)
      }

      is Term.Command  -> {
        val element = zonkTerm(term.element)
        val type = zonkTerm(term.type)
        Term.Command(element, type)
      }

      is Term.Let      -> {
        val init = zonkTerm(term.init)
        val body = zonkTerm(term.body)
        val type = zonkTerm(term.type)
        Term.Let(term.binder, init, body, type)
      }

      is Term.Match    -> {
        val scrutinee = zonkTerm(term.scrutinee)
        val branches = term.branches.map { (pattern, body) ->
          val body = zonkTerm(body)
          pattern to body
        }
        val type = zonkTerm(term.type)
        Term.Match(scrutinee, branches, type)
      }

      is Term.Proj     -> {
        val target = zonkTerm(term.target)
        val type = zonkTerm(term.type)
        Term.Proj(target, term.projection, type)
      }

      is Term.Var      -> {
        val type = zonkTerm(term.type)
        Term.Var(term.name, term.idx, type)
      }

      is Term.Def      -> {
        val type = zonkTerm(term.type)
        Term.Def(term.def, type)
      }

      is Term.Meta     -> {
        val solution = values.getOrNull(term.index)
        if (solution == null || (solution is Value.Meta && term.index == solution.index)) {
          val type = zonkTerm(term.type)
          Term.Meta(term.index, term.source, type).also {
            unsolvedMetas += it.index to it.source
          }
        } else {
          zonkTerm(quoteValue(solution))
        }
      }

      is Term.Hole     -> {
        term
      }
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
      value1 is Value.TagOf && value2 is Value.TagOf           -> value1.repr == value2.repr
      value1 is Value.Type && value2 is Value.Type             -> unifyValue(value1.element.value, value2.element.value)
      value1 is Value.Bool && value2 is Value.Bool             -> true
      value1 is Value.BoolOf && value2 is Value.BoolOf         -> value1.value == value2.value
      value1 is Value.If && value2 is Value.If                 -> {
        unifyValue(value1.condition, value2.condition) &&
        unifyValue(value1.thenBranch.value, value2.elseBranch.value) &&
        unifyValue(value1.elseBranch.value, value2.elseBranch.value)
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
      value1 is Value.Ref && value2 is Value.Ref               -> unifyValue(value1.element.value, value2.element.value)
      value1 is Value.RefOf && value2 is Value.RefOf           -> unifyValue(value1.element.value, value2.element.value)
      value1 is Value.Point && value2 is Value.Point           -> unifyValue(value1.element.value, value2.element.value)
      value1 is Value.Union && value2 is Value.Union           -> value1.elements.isEmpty() && value2.elements.isEmpty() // TODO
      value1 is Value.Func && value2 is Value.Func             -> {
        value1.open == value2.open &&
        value1.params.size == value2.params.size &&
        (value1.params zip value2.params).all { (param1, param2) ->
          unifyValue(param1.second.value, param2.second.value) // TODO
        } &&
        unifyValue(
          value1.result.open(this, value1.params.map { it.second }),
          value2.result.open(this, value2.params.map { it.second }),
        )
      }
      value1 is Value.FuncOf && value2 is Value.FuncOf         -> {
        value1.open == value2.open &&
        unifyValue(
          value1.result.open(this, (value1.type.value as Value.Func).params.map { it.second }),
          value2.result.open(this, (value2.type.value as Value.Func).params.map { it.second }),
        )
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