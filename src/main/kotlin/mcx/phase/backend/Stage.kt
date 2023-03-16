package mcx.phase.backend

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mcx.ast.Core.Definition
import mcx.ast.Core.Term
import mcx.ast.Lvl
import mcx.ast.Modifier
import mcx.ast.toIdx
import mcx.ast.toLvl
import mcx.phase.*

class Stage private constructor() {
  private val stagedDefinitions: MutableList<Definition> = mutableListOf()

  private fun stage(
    definition: Definition,
  ): List<Definition> {
    stageDefinition(definition)
    return stagedDefinitions
  }

  private fun stageDefinition(
    definition: Definition,
  ) {
    when (definition) {
      is Definition.Def -> {
        if (
          Modifier.INLINE !in definition.modifiers &&
          Modifier.CONST !in definition.modifiers &&
          Modifier.BUILTIN !in definition.modifiers
        ) {
          val type = stageTerm(definition.type)
          val body = stageTerm(definition.body!!)
          Definition.Def(definition.modifiers, definition.name, type, body)
        } else {
          null
        }
      }
    }?.also {
      stagedDefinitions += it
    }
  }

  private fun stageTerm(
    term: Term,
  ): Term {
    val env: Env = persistentListOf()
    return Lvl(env.size).quoteValue(env.evalTerm(term, 0), 0)
  }

  private fun Env.evalTerm(
    term: Term,
    stage: Int,
  ): Value {
    return when (term) {
      is Term.Tag         -> Value.Tag // ?
      is Term.TagOf       -> Value.TagOf(term.value)
      is Term.Type        -> {
        val tag = lazy { evalTerm(term.tag, stage) }
        Value.Type(tag)
      }
      is Term.Bool        -> Value.Bool
      is Term.BoolOf      -> Value.BoolOf(term.value)
      is Term.If          -> {
        if (stage == 0) {
          val condition = evalTerm(term.condition, stage)
          val thenBranch = lazy { evalTerm(term.thenBranch, stage) }
          val elseBranch = lazy { evalTerm(term.elseBranch, stage) }
          Value.If(condition, thenBranch, elseBranch)
        } else {
          when (val condition = evalTerm(term.condition, stage)) {
            is Value.BoolOf -> {
              if (condition.value) {
                evalTerm(term.thenBranch, stage)
              } else {
                evalTerm(term.elseBranch, stage)
              }
            }
            else            -> {
              val thenBranch = lazy { evalTerm(term.thenBranch, stage) }
              val elseBranch = lazy { evalTerm(term.elseBranch, stage) }
              Value.If(condition, thenBranch, elseBranch)
            }
          }
        }
      }
      is Term.Is          -> {
        if (stage == 0) {
          val scrutinee = lazy { evalTerm(term.scrutinee, stage) }
          Value.Is(scrutinee, term.scrutineer)
        } else {
          val scrutinee = lazy { evalTerm(term.scrutinee, stage) }
          when (val result = match(term.scrutineer, scrutinee)) {
            null -> Value.Is(scrutinee, term.scrutineer)
            else -> Value.BoolOf(result)
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
        val elements = term.elements.map { lazy { evalTerm(it, stage) } }
        Value.ByteArrayOf(elements)
      }
      is Term.IntArray    -> Value.IntArray
      is Term.IntArrayOf  -> {
        val elements = term.elements.map { lazy { evalTerm(it, stage) } }
        Value.IntArrayOf(elements)
      }
      is Term.LongArray   -> Value.LongArray
      is Term.LongArrayOf -> {
        val elements = term.elements.map { lazy { evalTerm(it, stage) } }
        Value.LongArrayOf(elements)
      }
      is Term.List        -> {
        val element = lazy { evalTerm(term.element, stage) }
        Value.List(element)
      }
      is Term.ListOf      -> {
        val elements = term.elements.map { lazy { evalTerm(it, stage) } }
        Value.ListOf(elements)
      }
      is Term.Compound    -> {
        val elements = term.elements.mapValues { lazy { evalTerm(it.value, stage) } }
        Value.Compound(elements)
      }
      is Term.CompoundOf  -> {
        val elements = term.elements.mapValues { lazy { evalTerm(it.value, stage) } }
        Value.CompoundOf(elements)
      }
      is Term.Union       -> {
        val elements = term.elements.map { lazy { evalTerm(it, stage) } }
        Value.Union(elements)
      }
      is Term.Func        -> {
        val params = term.params.map { (_, type) -> lazy { evalTerm(type, stage) } }
        val result = Closure(this, term.params.map { (binder, _) -> binder }, term.result)
        Value.Func(params, result)
      }
      is Term.FuncOf      -> {
        val result = Closure(this, term.params, term.result)
        Value.FuncOf(result)
      }
      is Term.Apply       -> {
        if (stage == 0) {
          val func = evalTerm(term.func, stage)
          val args = term.args.map { lazy { evalTerm(it, stage) } }
          Value.Apply(func, args)
        } else {
          val func = evalTerm(term.func, stage)
          val args = term.args.map { lazy { evalTerm(it, stage) } }
          when (func) {
            is Value.FuncOf -> func.result(args, stage)
            is Value.Def    -> lookupBuiltin(func.name)!!.eval(args) ?: Value.Apply(func, args)
            else            -> Value.Apply(func, args)
          }
        }
      }
      is Term.Code        -> {
        if (stage == 0) {
          unexpectedTerm(term)
        } else {
          val element = lazy { evalTerm(term.element, stage - 1) }
          Value.Code(element)
        }
      }
      is Term.CodeOf      -> {
        if (stage == 0) {
          unexpectedTerm(term)
        } else {
          val element = lazy { evalTerm(term.element, stage - 1) }
          Value.CodeOf(element)
        }
      }
      is Term.Splice      -> {
        if (stage == 0) {
          when (val element = evalTerm(term.element, 1)) {
            is Value.CodeOf -> element.element.value
            else            -> unexpectedTerm(Lvl(size).quoteValue(element, stage))
          }
        } else {
          when (val element = evalTerm(term.element, stage + 1)) {
            is Value.CodeOf -> element.element.value
            else            -> Value.Splice(element)
          }
        }
      }
      is Term.Let         -> {
        if (stage == 0) {
          val init = evalTerm(term.init, stage)
          val body = (this + bind(term.binder, lazyOf(init))).evalTerm(term.body, stage)
          Value.Let(term.binder, init, body)
        } else {
          val init = lazy { evalTerm(term.init, stage) }
          (this + bind(term.binder, init)).evalTerm(term.body, stage)
        }
      }
      is Term.Var         -> {
        if (stage == 0) {
          Value.Var(term.name, Lvl(size).toLvl(term.idx), Lvl(size).quoteValue(evalTerm(term.type, stage), stage))
        } else {
          this[Lvl(size).toLvl(term.idx).value].value
        }
      }
      is Term.Def         -> {
        if (stage == 0) {
          Value.Def(term.name, null)
        } else {
          term.body?.let { evalTerm(it, stage) } ?: Value.Def(term.name, null)
        }
      }
      is Term.Meta        -> unexpectedTerm(term)
      is Term.Hole        -> unexpectedTerm(term)
    }
  }

  // TODO: check stage
  private fun Lvl.quoteValue(
    value: Value,
    stage: Int,
  ): Term {
    return when (value) {
      is Value.Tag         -> Term.Tag
      is Value.TagOf       -> Term.TagOf(value.value)
      is Value.Type        -> {
        val tag = quoteValue(value.tag.value, stage)
        Term.Type(tag)
      }
      is Value.Bool        -> Term.Bool
      is Value.BoolOf      -> Term.BoolOf(value.value)
      is Value.If          -> {
        val condition = quoteValue(value.condition, stage)
        val thenBranch = quoteValue(value.thenBranch.value, stage)
        val elseBranch = quoteValue(value.elseBranch.value, stage)
        Term.If(condition, thenBranch, elseBranch)
      }
      is Value.Is          -> {
        val scrutinee = quoteValue(value.scrutinee.value, stage)
        Term.Is(scrutinee, value.scrutineer)
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
        val elements = value.elements.map { quoteValue(it.value, stage) }
        Term.ByteArrayOf(elements)
      }
      is Value.IntArray    -> Term.IntArray
      is Value.IntArrayOf  -> {
        val elements = value.elements.map { quoteValue(it.value, stage) }
        Term.IntArrayOf(elements)
      }
      is Value.LongArray   -> Term.LongArray
      is Value.LongArrayOf -> {
        val elements = value.elements.map { quoteValue(it.value, stage) }
        Term.LongArrayOf(elements)
      }
      is Value.List        -> {
        val element = quoteValue(value.element.value, stage)
        Term.List(element)
      }
      is Value.ListOf      -> {
        val elements = value.elements.map { quoteValue(it.value, stage) }
        Term.ListOf(elements)
      }
      is Value.Compound    -> {
        val elements = value.elements.mapValues { quoteValue(it.value.value, stage) }
        Term.Compound(elements)
      }
      is Value.CompoundOf  -> {
        val elements = value.elements.mapValues { quoteValue(it.value.value, stage) }
        Term.CompoundOf(elements)
      }
      is Value.Union       -> {
        val elements = value.elements.map { quoteValue(it.value, stage) }
        Term.Union(elements)
      }
      is Value.Func        -> {
        val params = (value.result.binders zip value.params).map { (binder, param) ->
          val param = quoteValue(param.value, stage)
          binder to param
        }
        val result = collect(value.result.binders).let { (this + it.size).quoteValue(value.result(it, stage), stage) }
        Term.Func(params, result)
      }
      is Value.FuncOf      -> {
        val result = collect(value.result.binders).let { (this + it.size).quoteValue(value.result(it, stage), stage) }
        Term.FuncOf(value.result.binders, result)
      }
      is Value.Apply       -> {
        val func = quoteValue(value.func, stage)
        val args = value.args.map { quoteValue(it.value, stage) }
        Term.Apply(func, args)
      }
      is Value.Code        -> {
        val element = quoteValue(value.element.value, stage - 1)
        Term.Code(element)
      }
      is Value.CodeOf      -> {
        val element = quoteValue(value.element.value, stage - 1)
        Term.CodeOf(element)
      }
      is Value.Splice      -> {
        val element = quoteValue(value.element, stage + 1)
        Term.Splice(element)
      }
      is Value.Let         -> {
        val init = quoteValue(value.init, stage)
        val body = collect(listOf(value.binder)).let { (this + it.size).quoteValue(value.body, stage) }
        Term.Let(value.binder, init, body)
      }
      is Value.Var         -> Term.Var(value.name, toIdx(value.lvl), value.type)
      is Value.Def         -> Term.Def(value.name, value.body)
      is Value.Meta        -> Term.Meta(value.index, value.source)
      is Value.Hole        -> Term.Hole
    }
  }

  private operator fun Closure.invoke(
    args: List<Lazy<Value>>,
    stage: Int,
  ): Value {
    return (env + (binders zip args).flatMap { (binder, arg) -> bind(binder, arg) }).evalTerm(body, stage)
  }

  companion object {
    private fun unexpectedTerm(term: Term): Nothing {
      error("unexpected term ${prettyTerm(term)} at stage 0")
    }

    operator fun invoke(
      context: Context,
      definition: Definition,
    ): List<Definition> =
      Stage().stage(definition)
  }
}
