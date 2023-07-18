package mcx.pass.backend

import kotlinx.collections.immutable.plus
import mcx.ast.Core.Definition
import mcx.ast.Core.Term
import mcx.ast.common.*
import mcx.pass.*
import mcx.pass.frontend.elaborate.emptyEnv
import mcx.pass.frontend.elaborate.matches
import mcx.pass.frontend.elaborate.next
import mcx.util.collections.mapWith

@Suppress("NAME_SHADOWING")
class Stage private constructor() {
  private fun stageDefinition(
    definition: Definition,
  ): Definition? {
    return when (definition) {
      is Definition.Def -> {
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
      is Term.Tag     -> {
        requireConst(term, phase)
        Value.Tag
      }

      is Term.TagOf   -> {
        requireConst(term, phase)
        Value.TagOf(term.repr)
      }

      is Term.Type    -> {
        val tag = lazy { evalTerm(term.element, Phase.CONST) }
        Value.Type(tag)
      }

      is Term.Unit    -> {
        Value.Unit
      }

      is Term.UnitOf  -> {
        Value.UnitOf
      }

      is Term.Bool    -> {
        Value.Bool
      }

      is Term.BoolOf  -> {
        Value.BoolOf(term.value)
      }

      is Term.I8      -> {
        Value.I8
      }

      is Term.I8Of    -> {
        Value.I8Of(term.value)
      }

      is Term.I16     -> {
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

      is Term.Wtf16      -> {
        Value.Wtf16
      }

      is Term.Wtf16Of    -> {
        Value.Wtf16Of(term.value)
      }

      is Term.I8Array    -> {
        Value.I8Array
      }

      is Term.I8ArrayOf  -> {
        val elements = term.elements.map { lazy { evalTerm(it, phase) } }
        Value.I8ArrayOf(elements)
      }

      is Term.I32Array   -> {
        Value.I32Array
      }

      is Term.I32ArrayOf -> {
        val elements = term.elements.map { lazy { evalTerm(it, phase) } }
        Value.I32ArrayOf(elements)
      }

      is Term.I64Array   -> {
        Value.I64Array
      }

      is Term.I64ArrayOf -> {
        val elements = term.elements.map { lazy { evalTerm(it, phase) } }
        Value.I64ArrayOf(elements)
      }

      is Term.List       -> {
        val element = lazy { evalTerm(term.element, phase) }
        Value.List(element)
      }

      is Term.ListOf     -> {
        val elements = term.elements.map { lazy { evalTerm(it, phase) } }
        val type = lazy { evalTerm(term.type, phase) }
        Value.ListOf(elements, type)
      }

      is Term.Compound   -> {
        val elements = term.elements.mapValuesTo(linkedMapOf()) { lazy { evalTerm(it.value, phase) } }
        Value.Compound(elements)
      }

      is Term.CompoundOf -> {
        val elements = term.elements.mapValuesTo(linkedMapOf()) { lazy { evalTerm(it.value, phase) } }
        val type = lazy { evalTerm(term.type, phase) }
        Value.CompoundOf(elements, type)
      }

      is Term.Point      -> {
        val element = lazy { evalTerm(term.element, phase) }
        val type = lazy { evalTerm(term.type, phase) }
        Value.Point(element, type)
      }

      is Term.Union      -> {
        val elements = term.elements.map { lazy { evalTerm(it, phase) } }
        val type = lazy { evalTerm(term.type, phase) }
        Value.Union(elements, type)
      }

      is Term.Func       -> {
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
        val type = lazy { evalTerm(term.type, phase) }
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
          val type = lazy { evalTerm(term.type, phase) }
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
        val type = lazy { evalTerm(term.type, phase) }
        Value.CodeOf(element, type)
      }

      is Term.Splice     -> {
        requireWorld(term, phase)
        val element = evalTerm(term.element, Phase.CONST) as Value.CodeOf
        element.element.value
      }

      is Term.Command -> {
        requireWorld(term, phase)
        val element = lazy { evalTerm(term.element, Phase.CONST) }
        val type = lazy { evalTerm(term.type, phase /* ? */) }
        Value.Command(element, type)
      }

      is Term.Let     -> {
        when (phase) {
          Phase.WORLD -> {
            val init = lazy { evalTerm(term.init, phase) }
            val body = lazy { (this + init).evalTerm(term.body, phase) }
            val type = lazy { evalTerm(term.type, phase) }
            Value.Let(term.binder, init, body, type)
          }
          Phase.CONST -> {
            val init = lazy { evalTerm(term.init, phase) }
            (this + init).evalTerm(term.body, phase)
          }
        }
      }

      is Term.If      -> {
        when (phase) {
          Phase.WORLD -> {
            val scrutinee = lazy { evalTerm(term.scrutinee, phase) }
            val branches = term.branches.map { (pattern, body) ->
              val body = lazy { evalTerm(body, phase) }
              pattern to body
            }
            val type = lazy { evalTerm(term.type, phase) }
            Value.If(scrutinee, branches, type)
          }
          Phase.CONST -> {
            val scrutinee = lazy { evalTerm(term.scrutinee, phase) }
            var matchedIndex = -1
            val branches = term.branches.mapIndexed { index, (pattern, body) ->
              val body = lazy { evalTerm(body, phase) }
              if (matchedIndex == -1 && pattern matches scrutinee) {
                matchedIndex = index
              }
              pattern to body
            }
            when (matchedIndex) {
              -1   -> {
                val type = lazy { evalTerm(term.type, phase) }
                Value.If(scrutinee, branches, type)
              }
              else -> {
                val (_, body) = term.branches[matchedIndex]
                (this + scrutinee).evalTerm(body, phase)
              }
            }
          }
        }
      }

      is Term.Project    -> {
        val target = evalTerm(term.target, phase)
        when (phase) {
          Phase.WORLD -> {
            val type = lazy { evalTerm(term.type, phase) }
            Value.Project(target, term.projs, type)
          }
          Phase.CONST -> {
            term.projs.foldIndexed(target) { index, acc, proj ->
              when (acc) {
                is Value.ListOf     -> acc.elements[(proj as Proj.ListOf).index].value
                is Value.CompoundOf -> acc.elements[(proj as Proj.CompoundOf).name]!!.value
                else                -> {
                  if (index == term.projs.lastIndex) {
                    acc
                  } else {
                    val type = lazy { evalTerm(term.type, phase) }
                    Value.Project(acc, term.projs.drop(index), type)
                  }
                }
              }
            }
          }
        }
      }

      is Term.Var     -> {
        val lvl = term.idx.toLvl(next())
        when (phase) {
          Phase.WORLD -> {
            val type = lazy { evalTerm(term.type, phase) }
            Value.Var(term.name, lvl, type)
          }
          Phase.CONST -> {
            this[lvl.value].value
          }
        }
      }

      is Term.Def     -> {
        when (phase) {
          Phase.WORLD -> null
          Phase.CONST -> {
            if (Modifier.BUILTIN in term.def.modifiers) {
              null
            } else {
              term.def.body?.let { evalTerm(it, phase) }
            }
          }
        } ?: run {
          val type = lazy { evalTerm(term.type, phase) }
          Value.Def(term.def, type)
        }
      }

      is Term.Meta       -> {
        unexpectedTerm(term)
      }

      is Term.Hole       -> {
        unexpectedTerm(term)
      }
    }
  }

  private fun Lvl.quoteValue(
    value: Value,
    phase: Phase,
  ): Term {
    return when (value) {
      is Value.Tag     -> {
        Term.Tag
      }

      is Value.TagOf   -> {
        Term.TagOf(value.repr)
      }

      is Value.Type    -> {
        val tag = quoteValue(value.element.value, Phase.CONST)
        Term.Type(tag)
      }

      is Value.Unit    -> {
        Term.Unit
      }

      is Value.UnitOf  -> {
        Term.UnitOf
      }

      is Value.Bool    -> {
        Term.Bool
      }

      is Value.BoolOf  -> {
        Term.BoolOf(value.value)
      }

      is Value.I8      -> {
        Term.I8
      }

      is Value.I8Of    -> {
        Term.I8Of(value.value)
      }

      is Value.I16     -> {
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

      is Value.Wtf16      -> {
        Term.Wtf16
      }

      is Value.Wtf16Of    -> {
        Term.Wtf16Of(value.value)
      }

      is Value.I8Array    -> {
        Term.I8Array
      }

      is Value.I8ArrayOf  -> {
        val elements = value.elements.map { quoteValue(it.value, phase) }
        Term.I8ArrayOf(elements)
      }

      is Value.I32Array   -> {
        Term.I32Array
      }

      is Value.I32ArrayOf -> {
        val elements = value.elements.map { quoteValue(it.value, phase) }
        Term.I32ArrayOf(elements)
      }

      is Value.I64Array   -> {
        Term.I64Array
      }

      is Value.I64ArrayOf -> {
        val elements = value.elements.map { quoteValue(it.value, phase) }
        Term.I64ArrayOf(elements)
      }

      is Value.List       -> {
        val element = quoteValue(value.element.value, phase)
        Term.List(element)
      }

      is Value.ListOf     -> {
        val elements = value.elements.map { quoteValue(it.value, phase) }
        val type = quoteValue(value.type.value, phase)
        Term.ListOf(elements, type)
      }

      is Value.Compound   -> {
        val elements = value.elements.mapValuesTo(linkedMapOf()) { quoteValue(it.value.value, phase) }
        Term.Compound(elements)
      }

      is Value.CompoundOf -> {
        val elements = value.elements.mapValuesTo(linkedMapOf()) { quoteValue(it.value.value, phase) }
        val type = quoteValue(value.type.value, phase)
        Term.CompoundOf(elements, type)
      }

      is Value.Point      -> {
        val element = quoteValue(value.element.value, phase)
        val type = quoteValue(value.type.value, phase)
        Term.Point(element, type)
      }

      is Value.Union      -> {
        val elements = value.elements.map { quoteValue(it.value, phase) }
        val type = quoteValue(value.type.value, phase)
        Term.Union(elements, type)
      }

      is Value.Func       -> {
        val params = value.params.mapIndexed { i, (pattern, type) ->
          pattern to (this + i).quoteValue(type.value, phase)
        }
        val result = (this + value.params.size).quoteValue(
          value.result.open(this, value.params.map { (_, type) -> type }, phase),
          phase,
        )
        Term.Func(value.open, params, result)
      }

      is Value.FuncOf     -> {
        val result = (this + value.params.size).quoteValue(
          value.result.open(
            this,
            (value.type.value as Value.Func /* TODO: unify */).params.map { (_, type) -> type },
            phase,
          ),
          phase,
        )
        val type = quoteValue(value.type.value, phase)
        Term.FuncOf(value.open, value.params, result, type)
      }

      is Value.Apply      -> {
        val func = quoteValue(value.func, phase)
        val args = value.args.map { quoteValue(it.value, phase) }
        val type = quoteValue(value.type.value, phase)
        Term.Apply(value.open, func, args, type)
      }

      is Value.Code       -> {
        val element = quoteValue(value.element.value, Phase.WORLD)
        Term.Code(element)
      }

      is Value.CodeOf     -> {
        val element = quoteValue(value.element.value, Phase.WORLD)
        val type = quoteValue(value.type.value, phase)
        Term.CodeOf(element, type)
      }

      is Value.Splice  -> {
        val element = quoteValue(value.element, Phase.CONST)
        val type = quoteValue(value.type.value, phase)
        Term.Splice(element, type)
      }

      is Value.Command -> {
        val element = quoteValue(value.element.value, Phase.CONST)
        val type = quoteValue(value.type.value, phase)
        Term.Command(element, type)
      }

      is Value.Let     -> {
        val init = quoteValue(value.init.value, phase)
        val body = quoteValue(value.body.value, phase)
        val type = quoteValue(value.type.value, phase)
        Term.Let(value.binder, init, body, type)
      }

      is Value.If      -> {
        val scrutinee = quoteValue(value.scrutinee.value, phase)
        val branches = value.branches.map { (pattern, body) ->
          val body = (this + 1).quoteValue(body.value, phase)
          pattern to body
        }
        val type = quoteValue(value.type.value, phase)
        Term.If(scrutinee, branches, type)
      }

      is Value.Project -> {
        val target = quoteValue(value.target, phase)
        val type = quoteValue(value.type.value, phase)
        Term.Project(target, value.projs, type)
      }

      is Value.Var     -> {
        val type = quoteValue(value.type.value, phase)
        Term.Var(value.name, value.lvl.toIdx(this), type)
      }

      is Value.Def     -> {
        val type = quoteValue(value.type.value, phase)
        Term.Def(value.def, type)
      }

      is Value.Meta    -> {
        val type = quoteValue(value.type.value, phase)
        Term.Meta(value.index, value.source, type)
      }

      is Value.Hole    -> {
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
      definition: Definition,
    ): Definition? {
      return Stage().stageDefinition(definition)
    }
  }
}
