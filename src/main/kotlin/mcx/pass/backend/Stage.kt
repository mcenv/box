package mcx.pass.backend

import kotlinx.collections.immutable.plus
import mcx.ast.Core.Definition
import mcx.ast.Core.Term
import mcx.ast.Lvl
import mcx.ast.Modifier
import mcx.ast.toIdx
import mcx.ast.toLvl
import mcx.pass.*
import mcx.util.collections.mapWith
import mcx.util.map

@Suppress("NAME_SHADOWING")
class Stage private constructor() {
  private fun stageDefinition(
    definition: Definition,
  ): Definition? {
    return when (definition) {
      is Definition.Def  -> {
        if (Modifier.CONST in definition.modifiers) {
          null
        } else {
          val type = stageTerm(definition.type)
          val body = definition.body?.let { stageTerm(it) }
          Definition.Def(definition.doc, definition.annotations, definition.modifiers, definition.name, type, body)
        }
      }
    }
  }

  private fun stageTerm(
    term: Term,
  ): Term {
    return emptyEnv().normalizeTerm(term, Phase.WORLD)
  }

  private fun Env.normalizeTerm(
    term: Term,
    phase: Phase,
  ): Term {
    return next().quoteValue(evalTerm(term, phase), phase)
  }

  private fun Env.evalTerm(
    term: Term,
    phase: Phase,
  ): Value {
    return when (term) {
      is Term.Tag         -> {
        requireConst(term, phase)
        Value.Tag
      }

      is Term.TagOf       -> {
        requireConst(term, phase)
        Value.TagOf(term.value)
      }

      is Term.Type        -> {
        val tag = lazy { evalTerm(term.element, Phase.CONST) }
        Value.Type(tag)
      }

      is Term.Bool        -> {
        Value.Bool
      }

      is Term.BoolOf      -> {
        Value.BoolOf(term.value)
      }

      is Term.If          -> {
        val condition = evalTerm(term.condition, phase)
        when {
          phase == Phase.CONST && condition is Value.BoolOf -> {
            if (condition.value) {
              evalTerm(term.thenBranch, phase)
            } else {
              evalTerm(term.elseBranch, phase)
            }
          }
          else                                              -> {
            val thenBranch = lazy { evalTerm(term.thenBranch, phase) }
            val elseBranch = lazy { evalTerm(term.elseBranch, phase) }
            val type = term.type.map { evalTerm(it, phase) }
            Value.If(condition, thenBranch, elseBranch, type)
          }
        }
      }

      is Term.I8          -> {
        Value.I8
      }

      is Term.I8Of        -> {
        Value.I8Of(term.value)
      }

      is Term.I16         -> {
        Value.I16
      }

      is Term.I16Of       -> {
        Value.I16Of(term.value)
      }

      is Term.I32         -> {
        Value.I32
      }

      is Term.I32Of       -> {
        Value.I32Of(term.value)
      }

      is Term.I64         -> {
        Value.I64
      }

      is Term.I64Of       -> {
        Value.I64Of(term.value)
      }

      is Term.F32         -> {
        Value.F32
      }

      is Term.F32Of       -> {
        Value.F32Of(term.value)
      }

      is Term.F64         -> {
        Value.F64
      }

      is Term.F64Of       -> {
        Value.F64Of(term.value)
      }

      is Term.Str         -> {
        Value.Str
      }

      is Term.StrOf       -> {
        Value.StrOf(term.value)
      }

      is Term.I8Array     -> {
        Value.I8Array
      }

      is Term.I8ArrayOf   -> {
        val elements = term.elements.map { lazy { evalTerm(it, phase) } }
        Value.I8ArrayOf(elements)
      }

      is Term.I32Array    -> {
        Value.I32Array
      }

      is Term.I32ArrayOf  -> {
        val elements = term.elements.map { lazy { evalTerm(it, phase) } }
        Value.I32ArrayOf(elements)
      }

      is Term.I64Array    -> {
        Value.I64Array
      }

      is Term.I64ArrayOf  -> {
        val elements = term.elements.map { lazy { evalTerm(it, phase) } }
        Value.I64ArrayOf(elements)
      }

      is Term.Vec      -> {
        val element = lazy { evalTerm(term.element, phase) }
        Value.Vec(element)
      }

      is Term.VecOf    -> {
        val elements = term.elements.map { lazy { evalTerm(it, phase) } }
        val type = term.type.map { evalTerm(it, phase) }
        Value.VecOf(elements, type)
      }

      is Term.Struct   -> {
        val elements = term.elements.mapValuesTo(linkedMapOf()) { lazy { evalTerm(it.value, phase) } }
        Value.Struct(elements)
      }

      is Term.StructOf -> {
        val elements = term.elements.mapValuesTo(linkedMapOf()) { lazy { evalTerm(it.value, phase) } }
        val type = term.type.map { evalTerm(it, phase) }
        Value.StructOf(elements, type)
      }

      is Term.Ref      -> {
        val element = lazy { evalTerm(term.element, phase) }
        Value.Ref(element)
      }

      is Term.RefOf    -> {
        val element = lazy { evalTerm(term.element, phase) }
        val type = term.type.map { evalTerm(it, phase) }
        Value.RefOf(element, type)
      }

      is Term.Point    -> {
        val element = lazy { evalTerm(term.element, phase) }
        val type = term.type.map { evalTerm(it, phase) }
        Value.Point(element, type)
      }

      is Term.Union    -> {
        val elements = term.elements.map { lazy { evalTerm(it, phase) } }
        val type = term.type.map { evalTerm(it, phase) }
        Value.Union(elements, type)
      }

      is Term.Func     -> {
        val (_, params) = term.params.mapWith(this) { modify, (param, type) ->
          val type = lazy { evalTerm(type, phase) }
          modify(this + lazyOf(Value.Var("#${next()}", next(), type)))
          param to type
        }
        val result = Closure(this, term.result)
        Value.Func(term.open, params, result)
      }

      is Term.FuncOf     -> {
        val result = Closure(this, term.result)
        val type = term.type.map { evalTerm(it, phase) }
        Value.FuncOf(term.open, term.params, result, type)
      }

      is Term.Apply      -> {
        val func = evalTerm(term.func, phase)
        val args = term.args.map { lazy { evalTerm(it, phase) } }
        when (phase) {
          Phase.WORLD -> null
          Phase.CONST -> {
            when (func) {
              is Value.FuncOf -> func.result(args, phase)
              is Value.Def    -> lookupBuiltin(func.def.name)!!.eval(args)
              else            -> null
            }
          }
        } ?: run {
          val type = term.type.map { evalTerm(it, phase) }
          Value.Apply(term.open, func, args, type)
        }
      }

      is Term.Code       -> {
        requireConst(term, phase)
        val element = lazy { evalTerm(term.element, Phase.WORLD) }
        Value.Code(element)
      }

      is Term.CodeOf     -> {
        requireConst(term, phase)
        val element = lazy { evalTerm(term.element, Phase.WORLD) }
        val type = term.type.map { evalTerm(it, phase) }
        Value.CodeOf(element, type)
      }

      is Term.Splice     -> {
        requireWorld(term, phase)
        val element = evalTerm(term.element, Phase.CONST) as Value.CodeOf
        element.element.value
      }

      is Term.Command    -> {
        requireWorld(term, phase)
        val element = lazy { evalTerm(term.element, Phase.CONST) }
        val type = term.type.map { evalTerm(it, phase /* ? */) }
        Value.Command(element, type)
      }

      is Term.Let        -> {
        when (phase) {
          Phase.WORLD -> {
            val init = lazy { evalTerm(term.init, phase) }
            val body = lazy { evalTerm(term.body, phase) }
            val type = term.type.map { evalTerm(it, phase) }
            Value.Let(term.binder, init, body, type)
          }
          Phase.CONST -> {
            val init = lazy { evalTerm(term.init, phase) }
            (this + init).evalTerm(term.body, phase)
          }
        }
      }

      is Term.Var        -> {
        val lvl = next().toLvl(term.idx)
        when (phase) {
          Phase.WORLD -> {
            val type = term.type.map { evalTerm(it, phase) }
            Value.Var(term.name, lvl, type)
          }
          Phase.CONST -> this[lvl.value].value
        }
      }

      is Term.Def         -> {
        when (phase) {
          Phase.WORLD -> null
          Phase.CONST -> term.def.body?.let { evalTerm(it, phase) }
        } ?: run {
          val type = term.type.map { evalTerm(it, phase) }
          Value.Def(term.def, type)
        }
      }

      is Term.Meta        -> {
        unexpectedTerm(term)
      }

      is Term.Hole        -> {
        unexpectedTerm(term)
      }
    }
  }

  private fun Lvl.quoteValue(
    value: Value,
    phase: Phase,
  ): Term {
    return when (value) {
      is Value.Tag         -> {
        Term.Tag
      }

      is Value.TagOf       -> {
        Term.TagOf(value.value)
      }

      is Value.Type        -> {
        val tag = quoteValue(value.element.value, Phase.CONST)
        Term.Type(tag)
      }

      is Value.Bool        -> {
        Term.Bool
      }

      is Value.BoolOf      -> {
        Term.BoolOf(value.value)
      }

      is Value.If          -> {
        val condition = quoteValue(value.condition, phase)
        val thenBranch = quoteValue(value.thenBranch.value, phase)
        val elseBranch = quoteValue(value.elseBranch.value, phase)
        val type = value.type.map { quoteValue(it, phase) }
        Term.If(condition, thenBranch, elseBranch, type)
      }

      is Value.I8          -> {
        Term.I8
      }

      is Value.I8Of        -> {
        Term.I8Of(value.value)
      }

      is Value.I16         -> {
        Term.I16
      }

      is Value.I16Of       -> {
        Term.I16Of(value.value)
      }

      is Value.I32         -> {
        Term.I32
      }

      is Value.I32Of       -> {
        Term.I32Of(value.value)
      }

      is Value.I64         -> {
        Term.I64
      }

      is Value.I64Of       -> {
        Term.I64Of(value.value)
      }

      is Value.F32         -> {
        Term.F32
      }

      is Value.F32Of       -> {
        Term.F32Of(value.value)
      }

      is Value.F64         -> {
        Term.F64
      }

      is Value.F64Of       -> {
        Term.F64Of(value.value)
      }

      is Value.Str         -> {
        Term.Str
      }

      is Value.StrOf       -> {
        Term.StrOf(value.value)
      }

      is Value.I8Array     -> {
        Term.I8Array
      }

      is Value.I8ArrayOf   -> {
        val elements = value.elements.map { quoteValue(it.value, phase) }
        Term.I8ArrayOf(elements)
      }

      is Value.I32Array    -> {
        Term.I32Array
      }

      is Value.I32ArrayOf  -> {
        val elements = value.elements.map { quoteValue(it.value, phase) }
        Term.I32ArrayOf(elements)
      }

      is Value.I64Array    -> {
        Term.I64Array
      }

      is Value.I64ArrayOf  -> {
        val elements = value.elements.map { quoteValue(it.value, phase) }
        Term.I64ArrayOf(elements)
      }

      is Value.Vec      -> {
        val element = quoteValue(value.element.value, phase)
        Term.Vec(element)
      }

      is Value.VecOf    -> {
        val elements = value.elements.map { quoteValue(it.value, phase) }
        val type = value.type.map { quoteValue(it, phase) }
        Term.VecOf(elements, type)
      }

      is Value.Struct   -> {
        val elements = value.elements.mapValuesTo(linkedMapOf()) { quoteValue(it.value.value, phase) }
        Term.Struct(elements)
      }

      is Value.StructOf -> {
        val elements = value.elements.mapValuesTo(linkedMapOf()) { quoteValue(it.value.value, phase) }
        val type = value.type.map { quoteValue(it, phase) }
        Term.StructOf(elements, type)
      }

      is Value.Ref      -> {
        val element = quoteValue(value.element.value, phase)
        Term.Ref(element)
      }

      is Value.RefOf    -> {
        val element = quoteValue(value.element.value, phase)
        val type = value.type.map { quoteValue(it, phase) }
        Term.RefOf(element, type)
      }

      is Value.Point    -> {
        val element = quoteValue(value.element.value, phase)
        val type = value.type.map { quoteValue(it, phase) }
        Term.Point(element, type)
      }

      is Value.Union    -> {
        val elements = value.elements.map { quoteValue(it.value, phase) }
        val type = value.type.map { quoteValue(it, phase) }
        Term.Union(elements, type)
      }

      is Value.Func     -> {
        val params = value.params.mapIndexed { i, (pattern, type) ->
          pattern to (this + i).quoteValue(type.value, phase)
        }
        val result = (this + value.params.size).quoteValue(
          value.result.open(this, value.params.map { (_, type) -> type }, phase),
          phase,
        )
        Term.Func(value.open, params, result)
      }

      is Value.FuncOf      -> {
        val result = (this + value.params.size).quoteValue(
          value.result.open(
            this,
            (value.type.value as Value.Func /* TODO: unify */).params.map { (_, type) -> type },
            phase,
          ),
          phase,
        )
        val type = value.type.map { quoteValue(it, phase) }
        Term.FuncOf(value.open, value.params, result, type)
      }

      is Value.Apply       -> {
        val func = quoteValue(value.func, phase)
        val args = value.args.map { quoteValue(it.value, phase) }
        val type = value.type.map { quoteValue(it, phase) }
        Term.Apply(value.open, func, args, type)
      }

      is Value.Code        -> {
        val element = quoteValue(value.element.value, Phase.WORLD)
        Term.Code(element)
      }

      is Value.CodeOf      -> {
        val element = quoteValue(value.element.value, Phase.WORLD)
        val type = value.type.map { quoteValue(it, phase) }
        Term.CodeOf(element, type)
      }

      is Value.Splice      -> {
        val element = quoteValue(value.element, Phase.CONST)
        val type = value.type.map { quoteValue(it, phase) }
        Term.Splice(element, type)
      }

      is Value.Command -> {
        val element = quoteValue(value.element.value, Phase.CONST)
        val type = value.type.map { quoteValue(it, phase) }
        Term.Command(element, type)
      }

      is Value.Let     -> {
        val init = quoteValue(value.init.value, phase)
        val body = quoteValue(value.body.value, phase)
        val type = value.type.map { quoteValue(it, phase) }
        Term.Let(value.binder, init, body, type)
      }

      is Value.Var         -> {
        val type = value.type.map { quoteValue(it, phase) }
        Term.Var(value.name, toIdx(value.lvl), type)
      }

      is Value.Def         -> {
        val type = value.type.map { quoteValue(it, phase) }
        Term.Def(value.def, type)
      }

      is Value.Meta        -> {
        val type = value.type.map { quoteValue(it, phase) }
        Term.Meta(value.index, value.source, type)
      }

      is Value.Hole        -> {
        Term.Hole
      }
    }
  }

  private operator fun Closure.invoke(
    args: List<Lazy<Value>>,
    phase: Phase,
  ): Value {
    return (env + args).evalTerm(body, phase)
  }

  private fun Closure.open(
    next: Lvl,
    types: List<Lazy<Value>>,
    phase: Phase,
  ): Value {
    return this(types.mapIndexed { i, type ->
      lazyOf(Value.Var("#${next + i}", next + i, type))
    }, phase)
  }

  companion object {
    private fun requireWorld(
      term: Term,
      phase: Phase,
    ) {
      if (phase != Phase.WORLD) {
        unexpectedTerm(term)
      }
    }

    private fun requireConst(
      term: Term,
      phase: Phase,
    ) {
      if (phase != Phase.CONST) {
        unexpectedTerm(term)
      }
    }

    private fun unexpectedTerm(term: Term): Nothing {
      error("Unexpected term: ${prettyTerm(term)}")
    }

    operator fun invoke(
      context: Context,
      definition: Definition,
    ): Definition? {
      return Stage().stageDefinition(definition)
    }
  }
}
