package mcx.phase.backend

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mcx.ast.Core
import mcx.ast.Core.Definition
import mcx.ast.Core.Term
import mcx.ast.Core.Value
import mcx.ast.Modifier
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
          val body = stageTerm(definition.body!!)
          Definition.Def(definition.modifiers, definition.name, definition.type, body)
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
    val values = persistentListOf<Lazy<Value>>()
    return values.size.quote(values.evalTerm(term, 0))
  }

  private fun PersistentList<Lazy<Value>>.evalTerm(
    term: Term,
    stage: Int,
  ): Value {
    return when (term) {
      is Term.Tag         -> {
        if (stage == 0) {
          unexpectedTerm(term)
        } else {
          Value.Tag
        }
      }
      is Term.TagOf       -> Value.TagOf(term.value)
      is Term.Type        -> {
        val tag = lazy { evalTerm(term, stage) }
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
        val result = Core.Closure(this, term.params.map { (binder, _) -> binder }, term.result)
        Value.Func(params, result)
      }
      is Term.FuncOf      -> {
        val result = Core.Closure(this, term.params, term.result)
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
            is Value.FuncOf -> func.result(args)
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
            else            -> unexpectedTerm(size.quote(element))
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
          Value.Var(term.name, term.level)
        } else {
          this[term.level].value
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
