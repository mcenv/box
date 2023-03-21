package mcx.phase.backend

import kotlinx.collections.immutable.plus
import mcx.ast.Core.Definition
import mcx.ast.Core.Pattern
import mcx.ast.Core.Term
import mcx.ast.Lvl
import mcx.ast.Modifier
import mcx.ast.toIdx
import mcx.ast.toLvl
import mcx.phase.*

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
          Definition.Def(definition.modifiers, definition.name, type, body)
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
    return Lvl(size).quoteValue(evalTerm(term, phase), phase)
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
      is Term.Bool        -> Value.Bool
      is Term.BoolOf      -> Value.BoolOf(term.value)
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
            Value.If(condition, thenBranch, elseBranch)
          }
        }
      }
      is Term.Is          -> {
        val scrutinee = lazy { evalTerm(term.scrutinee, phase) }
        val scrutineer = evalPattern(term.scrutineer, phase)
        when (phase) {
          Phase.WORLD -> Value.Is(scrutinee, scrutineer)
          Phase.CONST -> {
            when (val result = scrutineer matches scrutinee) {
              null -> Value.Is(scrutinee, scrutineer)
              else -> Value.BoolOf(result)
            }
          }
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
        val elements = term.elements.map { lazy { evalTerm(it, phase) } }
        Value.ByteArrayOf(elements)
      }
      is Term.IntArray    -> Value.IntArray
      is Term.IntArrayOf  -> {
        val elements = term.elements.map { lazy { evalTerm(it, phase) } }
        Value.IntArrayOf(elements)
      }
      is Term.LongArray   -> Value.LongArray
      is Term.LongArrayOf -> {
        val elements = term.elements.map { lazy { evalTerm(it, phase) } }
        Value.LongArrayOf(elements)
      }
      is Term.List        -> {
        val element = lazy { evalTerm(term.element, phase) }
        Value.List(element)
      }
      is Term.ListOf      -> {
        val elements = term.elements.map { lazy { evalTerm(it, phase) } }
        Value.ListOf(elements)
      }
      is Term.Compound    -> {
        val elements = term.elements.mapValues { lazy { evalTerm(it.value, phase) } }
        Value.Compound(elements)
      }
      is Term.CompoundOf  -> {
        val elements = term.elements.mapValues { lazy { evalTerm(it.value, phase) } }
        Value.CompoundOf(elements)
      }
      is Term.Union       -> {
        val elements = term.elements.map { lazy { evalTerm(it, phase) } }
        Value.Union(elements)
      }
      is Term.Func        -> {
        val params = term.params.map { (_, type) -> lazy { evalTerm(type, phase) } }
        val binders = term.params.map { (param, _) -> evalPattern(param, phase) }
        val result = Closure(this, binders, term.result)
        Value.Func(params, result)
      }
      is Term.FuncOf      -> {
        val binders = term.params.map { evalPattern(it, phase) }
        val result = Closure(this, binders, term.result)
        Value.FuncOf(result)
      }
      is Term.Apply       -> {
        val func = evalTerm(term.func, phase)
        val args = term.args.map { lazy { evalTerm(it, phase) } }
        when (phase) {
          Phase.WORLD -> null
          Phase.CONST -> {
            when (func) {
              is Value.FuncOf -> func.result(args, phase)
              is Value.Def    -> lookupBuiltin(func.name)!!.eval(args)
              else            -> null
            }
          }
        } ?: Value.Apply(func, args)
      }
      is Term.Code        -> {
        requireConst(term, phase)
        val element = lazy { evalTerm(term.element, Phase.WORLD) }
        Value.Code(element)
      }
      is Term.CodeOf      -> {
        requireConst(term, phase)
        val element = lazy { evalTerm(term.element, Phase.WORLD) }
        Value.CodeOf(element)
      }
      is Term.Splice      -> {
        requireWorld(term, phase)
        val element = evalTerm(term.element, Phase.CONST) as Value.CodeOf
        element.element.value
      }
      is Term.Let         -> {
        val init = lazy { evalTerm(term.init, phase) }
        val binder = evalPattern(term.binder, phase)
        (this + (binder binds init)).evalTerm(term.body, phase)
      }
      is Term.Var         -> {
        val lvl = Lvl(size).toLvl(term.idx)
        when (phase) {
          Phase.WORLD -> {
            val type = evalTerm(term.type, phase)
            Value.Var(term.name, lvl, type)
          }
          Phase.CONST -> this[lvl.value].value
        }
      }
      is Term.Def         -> {
        when (phase) {
          Phase.WORLD -> null
          Phase.CONST -> term.body?.let { evalTerm(it, phase) }
        } ?: Value.Def(term.name, null)
      }
      is Term.Meta        -> unexpectedTerm(term)
      is Term.Hole        -> unexpectedTerm(term)
    }
  }

  private fun Lvl.quoteValue(
    value: Value,
    phase: Phase,
  ): Term {
    return when (value) {
      is Value.Tag         -> Term.Tag
      is Value.TagOf       -> Term.TagOf(value.value)
      is Value.Type        -> {
        val tag = quoteValue(value.element.value, Phase.CONST)
        Term.Type(tag)
      }
      is Value.Bool        -> Term.Bool
      is Value.BoolOf      -> Term.BoolOf(value.value)
      is Value.If          -> {
        val condition = quoteValue(value.condition, phase)
        val thenBranch = quoteValue(value.thenBranch.value, phase)
        val elseBranch = quoteValue(value.elseBranch.value, phase)
        Term.If(condition, thenBranch, elseBranch)
      }
      is Value.Is          -> {
        val scrutinee = quoteValue(value.scrutinee.value, phase)
        val scrutineer = quotePattern(value.scrutineer, phase)
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
        val elements = value.elements.map { quoteValue(it.value, phase) }
        Term.ByteArrayOf(elements)
      }
      is Value.IntArray    -> Term.IntArray
      is Value.IntArrayOf  -> {
        val elements = value.elements.map { quoteValue(it.value, phase) }
        Term.IntArrayOf(elements)
      }
      is Value.LongArray   -> Term.LongArray
      is Value.LongArrayOf -> {
        val elements = value.elements.map { quoteValue(it.value, phase) }
        Term.LongArrayOf(elements)
      }
      is Value.List        -> {
        val element = quoteValue(value.element.value, phase)
        Term.List(element)
      }
      is Value.ListOf      -> {
        val elements = value.elements.map { quoteValue(it.value, phase) }
        Term.ListOf(elements)
      }
      is Value.Compound    -> {
        val elements = value.elements.mapValues { quoteValue(it.value.value, phase) }
        Term.Compound(elements)
      }
      is Value.CompoundOf  -> {
        val elements = value.elements.mapValues { quoteValue(it.value.value, phase) }
        Term.CompoundOf(elements)
      }
      is Value.Union       -> {
        val elements = value.elements.map { quoteValue(it.value, phase) }
        Term.Union(elements)
      }
      is Value.Func        -> {
        val params = (value.result.binders zip value.params).map { (binder, param) ->
          val binder = quotePattern(binder, phase)
          val param = quoteValue(param.value, phase)
          binder to param
        }
        val result = quoteClosure(value.result, phase)
        Term.Func(params, result)
      }
      is Value.FuncOf      -> {
        val params = value.result.binders.map { quotePattern(it, phase) }
        val result = quoteClosure(value.result, phase)
        Term.FuncOf(params, result)
      }
      is Value.Apply       -> {
        val func = quoteValue(value.func, phase)
        val args = value.args.map { quoteValue(it.value, phase) }
        Term.Apply(func, args)
      }
      is Value.Code        -> {
        val element = quoteValue(value.element.value, Phase.WORLD)
        Term.Code(element)
      }
      is Value.CodeOf      -> {
        val element = quoteValue(value.element.value, Phase.WORLD)
        Term.CodeOf(element)
      }
      is Value.Splice      -> {
        val element = quoteValue(value.element, Phase.CONST)
        Term.Splice(element)
      }
      is Value.Var         -> {
        val type = quoteValue(value.type, phase)
        Term.Var(value.name, toIdx(value.lvl), type)
      }
      is Value.Def         -> Term.Def(value.name, value.body)
      is Value.Meta        -> Term.Meta(value.index, value.source)
      is Value.Hole        -> Term.Hole
    }
  }

  private fun Env.evalPattern(
    pattern: Pattern<Term>,
    phase: Phase,
  ): Pattern<Value> {
    return when (pattern) {
      is Pattern.IntOf      -> pattern
      is Pattern.CompoundOf -> {
        val elements = pattern.elements.map { (key, element) -> key to evalPattern(element, phase) }
        Pattern.CompoundOf(elements)
      }
      is Pattern.Var        -> {
        val type = evalTerm(pattern.type, phase)
        Pattern.Var(pattern.name, type)
      }
      is Pattern.Drop       -> {
        val type = evalTerm(pattern.type, phase)
        Pattern.Drop(type)
      }
      is Pattern.Hole       -> unexpectedPattern(pattern)
    }
  }

  private fun Lvl.quotePattern(
    pattern: Pattern<Value>,
    phase: Phase,
  ): Pattern<Term> {
    return when (pattern) {
      is Pattern.IntOf      -> pattern
      is Pattern.CompoundOf -> {
        val elements = pattern.elements.map { (key, element) -> key to quotePattern(element, phase) }
        Pattern.CompoundOf(elements)
      }
      is Pattern.Var        -> {
        val type = quoteValue(pattern.type, phase)
        Pattern.Var(pattern.name, type)
      }
      is Pattern.Drop       -> {
        val type = quoteValue(pattern.type, phase)
        Pattern.Drop(type)
      }
      is Pattern.Hole       -> unexpectedPattern(pattern)
    }
  }

  private fun Lvl.evalClosure(
    closure: Closure,
    phase: Phase,
  ): Value {
    return closure(collect(closure.binders), phase)
  }

  private fun Lvl.quoteClosure(
    closure: Closure,
    phase: Phase,
  ): Term {
    return collect(closure.binders).let { (this + it.size).quoteValue(closure(it, phase), phase) }
  }

  operator fun Closure.invoke(
    args: List<Lazy<Value>>,
    phase: Phase,
  ): Value {
    return (env + (binders zip args).flatMap { (binder, value) -> binder binds value }).evalTerm(body, phase)
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
      error("unexpected term: ${prettyTerm(term)}")
    }

    private fun unexpectedPattern(pattern: Pattern<*>): Nothing {
      error("unexpected term: ${prettyPattern(pattern)}")
    }

    operator fun invoke(
      context: Context,
      definition: Definition,
    ): Definition? {
      return Stage().stageDefinition(definition)
    }
  }
}
