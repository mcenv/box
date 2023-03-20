package mcx.phase.frontend

import kotlinx.collections.immutable.*
import mcx.ast.*
import mcx.lsp.contains
import mcx.lsp.diagnostic
import mcx.phase.*
import mcx.phase.frontend.Elaborate.Ctx.Companion.emptyCtx
import mcx.util.toSubscript
import org.eclipse.lsp4j.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import mcx.ast.Core as C
import mcx.ast.Resolved as R

@Suppress("NAME_SHADOWING", "NOTHING_TO_INLINE")
class Elaborate private constructor(
  dependencies: List<C.Module>,
  private val input: Resolve.Result,
  private val position: Position?,
) {
  private val meta: Meta = Meta()
  private val diagnostics: MutableList<Diagnostic> = mutableListOf()
  private var varCompletionItems: MutableList<CompletionItem> = mutableListOf()
  private var definitionCompletionItems: MutableList<CompletionItem> = mutableListOf()
  private var hover: (() -> String)? = null
  private val definitions: MutableMap<DefinitionLocation, C.Definition> = dependencies.flatMap { dependency -> dependency.definitions.map { it.name to it } }.toMap().toMutableMap()

  private fun elaborate(): Result {
    val module = elaborateModule(input.module)
    return Result(
      module,
      input.diagnostics + diagnostics,
      varCompletionItems + definitionCompletionItems,
      hover,
    )
  }

  private fun elaborateModule(
    module: R.Module,
  ): C.Module {
    val definitions = module.definitions.values.mapNotNull { elaborateDefinition(it) }
    return C.Module(module.name, definitions)
  }

  private fun elaborateDefinition(
    definition: R.Definition,
  ): C.Definition? {
    if (definition is R.Definition.Hole) {
      return null
    }
    val modifiers = definition.modifiers.map { it.value }
    val name = definition.name.value
    return when (definition) {
      is R.Definition.Def  -> {
        val ctx = emptyCtx()
        val phase = getPhase(modifiers)
        val type = ctx.checkTerm(definition.type, phase, meta.freshType(definition.type.range))
        if (Modifier.REC in modifiers) {
          definitions[name] = C.Definition.Def(modifiers, name, type, null)
        }
        val body = definition.body?.let { ctx.checkTerm(it, phase, ctx.freeze().eval(type)) }
        with(meta) {
          resetUnsolvedMetas()
          val type = Lvl(0).zonkTerm(type)
          val body = body?.let { Lvl(0).zonkTerm(it) }
          meta.unsolvedMetas.forEach { (index, source) ->
            diagnostics += unsolvedMeta(index, source)
          }
          C.Definition.Def(modifiers, name, type, body)
        }
      }
      is R.Definition.Hole -> error("unreachable")
    }.also { definitions[name] = it }
  }

  private inline fun Ctx.synthTerm(
    term: R.Term,
    phase: Phase,
  ): C.Term {
    return elaborateTerm(term, phase, null)
  }

  private inline fun Ctx.checkTerm(
    term: R.Term,
    phase: Phase,
    type: C.Value,
  ): C.Term {
    return elaborateTerm(term, phase, type)
  }

  private fun Ctx.elaborateTerm(
    term: R.Term,
    phase: Phase,
    type: C.Value?,
  ): C.Term {
    val type = type?.let { meta.forceValue(type) }
    return when {
      term is R.Term.Tag && phase == Phase.CONST && synth(type)                  -> C.Term.Tag
      term is R.Term.TagOf && phase == Phase.CONST && synth(type)                -> C.Term.TagOf(term.value)
      term is R.Term.Type && synth(type)                                         -> {
        val tag = checkTerm(term.element, Phase.CONST, C.Value.Tag)
        C.Term.Type(tag)
      }
      term is R.Term.Bool && synth(type)                                         -> C.Term.Bool
      term is R.Term.BoolOf && synth(type)                                       -> C.Term.BoolOf(term.value)
      term is R.Term.If && match<C.Value>(type)                                  -> {
        val condition = checkTerm(term.condition, phase, C.Value.Bool)
        val thenBranch = elaborateTerm(term.thenBranch, phase, type)
        val elseBranch = elaborateTerm(term.elseBranch, phase, type)
        val type = type ?: C.Value.Union(listOf(lazyOf(thenBranch.type), lazyOf(elseBranch.type)), meta.freshType(term.range))
        C.Term.If(condition, thenBranch, elseBranch, type)
      }
      term is R.Term.Is && synth(type)                                           -> {
        val scrutineer = restoring { synthPattern(term.scrutineer, phase) }
        val scrutinee = checkTerm(term.scrutinee, phase, scrutineer.type)
        C.Term.Is(scrutinee, scrutineer)
      }
      term is R.Term.Byte && synth(type)                                         -> C.Term.Byte
      term is R.Term.ByteOf && synth(type)                                       -> C.Term.ByteOf(term.value)
      term is R.Term.Short && synth(type)                                        -> C.Term.Short
      term is R.Term.ShortOf && synth(type)                                      -> C.Term.ShortOf(term.value)
      term is R.Term.Int && synth(type)                                          -> C.Term.Int
      term is R.Term.IntOf && synth(type)                                        -> C.Term.IntOf(term.value)
      term is R.Term.Long && synth(type)                                         -> C.Term.Long
      term is R.Term.LongOf && synth(type)                                       -> C.Term.LongOf(term.value)
      term is R.Term.Float && synth(type)                                        -> C.Term.Float
      term is R.Term.FloatOf && synth(type)                                      -> C.Term.FloatOf(term.value)
      term is R.Term.Double && synth(type)                                       -> C.Term.Double
      term is R.Term.DoubleOf && synth(type)                                     -> C.Term.DoubleOf(term.value)
      term is R.Term.String && synth(type)                                       -> C.Term.String
      term is R.Term.StringOf && synth(type)                                     -> C.Term.StringOf(term.value)
      term is R.Term.ByteArray && synth(type)                                    -> C.Term.ByteArray
      term is R.Term.ByteArrayOf && synth(type)                                  -> {
        val elements = term.elements.map { checkTerm(it, phase, C.Value.Byte) }
        C.Term.ByteArrayOf(elements)
      }
      term is R.Term.IntArray && synth(type)                                     -> C.Term.IntArray
      term is R.Term.IntArrayOf && synth(type)                                   -> {
        val elements = term.elements.map { checkTerm(it, phase, C.Value.Int) }
        C.Term.IntArrayOf(elements)
      }
      term is R.Term.LongArray && synth(type)                                    -> C.Term.LongArray
      term is R.Term.LongArrayOf && synth(type)                                  -> {
        val elements = term.elements.map { checkTerm(it, phase, C.Value.Long) }
        C.Term.LongArrayOf(elements)
      }
      term is R.Term.List && synth(type)                                         -> {
        val element = checkTerm(term.element, phase, meta.freshType(term.element.range))
        C.Term.List(element)
      }
      term is R.Term.ListOf && match<C.Value.List>(type)                         -> {
        val elementType = type?.element?.value
        val elements = term.elements.map { elaborateTerm(it, phase, elementType) }
        val type = type ?: C.Value.List(lazyOf(C.Value.Union(elements.map { lazyOf(it.type) }, meta.freshType(term.range))))
        C.Term.ListOf(elements, type)
      }
      term is R.Term.Compound && synth(type)                                     -> {
        val elements = term.elements.associate { (key, element) ->
          val element = checkTerm(element, phase, meta.freshType(element.range))
          key.value to element
        }
        C.Term.Compound(elements)
      }
      term is R.Term.CompoundOf && synth(type)                                   -> {
        TODO()
      }
      term is R.Term.CompoundOf && check<C.Value.Compound>(type)                 -> {
        TODO()
      }
      term is R.Term.Union && match<C.Value.Type>(type)                          -> {
        val type = type ?: meta.freshType(term.range)
        val elements = term.elements.map { checkTerm(it, phase, type) }
        C.Term.Union(elements, type)
      }
      term is R.Term.Func && synth(type)                                         -> {
        val env = freeze()
        restoring {
          val params = term.params.map { (pattern, term) ->
            val term = checkTerm(term, phase, meta.freshType(term.range))
            val pattern = checkPattern(pattern, phase, env.eval(term))
            pattern to term
          }
          val result = checkTerm(term.result, phase, meta.freshType(term.result.range))
          C.Term.Func(params, result)
        }
      }
      term is R.Term.FuncOf && synth(type)               -> {
        val next = next()
        restoring {
          val params = term.params.map { synthPattern(it, phase) }
          val result = synthTerm(term.result, phase)
          val type = C.Value.Func(
            params.map { lazyOf(it.type) },
            C.Closure(freeze(), params, (next + next.collect(params).size).quote(result.type)),
          )
          C.Term.FuncOf(params, result, type)
        }
      }
      term is R.Term.FuncOf && check<C.Value.Func>(type) -> {
        val next = next()
        restoring {
          if (type.params.size == term.params.size) {
            val params = (term.params zip type.params).map { (param, type) ->
              elaboratePattern(param, phase, type.value)
            }
            val result = checkTerm(term.result, phase, type.result(next.collect(type.result.binders)))
            C.Term.FuncOf(params, result, type)
          } else {
            invalidTerm(arityMismatch(type.params.size, term.params.size, term.range))
          }
        }
      }
      term is R.Term.Apply && synth(type)                -> {
        val func = synthTerm(term.func, phase)
        val funcType = when (val funcType = meta.forceValue(func.type)) {
          is C.Value.Func -> funcType
          else            -> {
            val params = term.args.map { lazyOf(meta.freshValue(term.func.range)) }
            val result = C.Closure(freeze(), params.map { C.Pattern.Drop(it.value) }, next().quote(meta.freshValue(term.func.range)))
            C.Value.Func(params, result).also {
              if (!next().sub(funcType, it)) {
                return invalidTerm(next().typeMismatch(funcType, it, term.func.range))
              }
            }
          }
        }
        if (funcType.params.size != term.args.size) {
          return invalidTerm(arityMismatch(funcType.params.size, term.args.size, term.range))
        }
        val args = (term.args zip funcType.params).map { (arg, param) ->
          checkTerm(arg, phase, param.value)
        }
        val values = freeze()
        val type = funcType.result(args.map { lazy { values.eval(it) } })
        C.Term.Apply(func, args, type)
      }
      term is R.Term.Code && phase == Phase.CONST && synth(type)                 -> {
        val element = checkTerm(term.element, Phase.WORLD, meta.freshType(term.element.range))
        C.Term.Code(element)
      }
      term is R.Term.CodeOf && phase == Phase.CONST && match<C.Value.Code>(type) -> {
        val element = elaborateTerm(term.element, Phase.WORLD, type?.element?.value)
        val type = type ?: C.Value.Code(lazyOf(element.type))
        C.Term.CodeOf(element, type)
      }
      term is R.Term.Splice && match<C.Value>(type)                              -> {
        val type = type ?: meta.freshValue(term.range)
        val element = checkTerm(term.element, Phase.CONST, C.Value.Code(lazyOf(type)))
        C.Term.Splice(element, type)
      }
      term is R.Term.Let && match<C.Value>(type)         -> {
        val init = synthTerm(term.init, phase)
        restoring {
          val binder = synthPattern(term.binder, phase)
          if (!next().sub(init.type, binder.type)) {
            diagnostics += next().typeMismatch(init.type, binder.type, term.init.range)
          }
          val body = elaborateTerm(term.body, phase, type)
          val type = type ?: body.type
          C.Term.Let(binder, init, body, type)
        }
      }
      term is R.Term.Var && synth(type)                  -> {
        val entry = this[next().toLvl(term.idx)]
        if (phase == entry.phase) {
          C.Term.Var(term.name, term.idx, entry.type)
        } else {
          invalidTerm(phaseMismatch(phase, entry.phase, term.range))
        }
      }
      term is R.Term.Def && synth(type)                  -> {
        when (val definition = definitions[term.name]) {
          is C.Definition.Def -> {
            when (val actualPhase = getPhase(definition.modifiers)) {
              phase -> {
                val type = freeze().eval(definition.type)
                C.Term.Def(term.name, definition.body, type)
              }
              else  -> invalidTerm(phaseMismatch(phase, actualPhase, term.range))
            }
          }
          else                -> invalidTerm(expectedDef(term.range))
        }
      }
      term is R.Term.As && synth(type)                   -> {
        val type = freeze().eval(checkTerm(term.type, phase, meta.freshType(term.type.range)))
        checkTerm(term.element, phase, type)
      }
      term is R.Term.Hole && match<C.Value>(type)        -> C.Term.Hole
      synth(type)                                        -> invalidTerm(cannotSynthesize(term.range))
      check<C.Value>(type)                               -> {
        val synth = synthTerm(term, phase)
        if (next().sub(synth.type, type)) {
          synth
        } else {
          invalidTerm(next().typeMismatch(type, synth.type, term.range))
        }
      }
      else                                               -> error("unreachable")
    }.also {
      hover(it.type, term.range)
    }
  }

  private inline fun Ctx.synthPattern(
    pattern: R.Pattern,
    phase: Phase,
  ): C.Pattern {
    return elaboratePattern(pattern, phase, null)
  }

  private inline fun Ctx.checkPattern(
    pattern: R.Pattern,
    phase: Phase,
    type: C.Value,
  ): C.Pattern {
    return elaboratePattern(pattern, phase, type)
  }

  private fun Ctx.elaboratePattern(
    pattern: R.Pattern,
    phase: Phase,
    type: C.Value?,
  ): C.Pattern {
    val type = type?.let { meta.forceValue(type) }
    return when {
      pattern is R.Pattern.IntOf && synth(type)                        -> C.Pattern.IntOf(pattern.value)
      pattern is R.Pattern.CompoundOf && synth(type)                   -> {
        TODO()
      }
      pattern is R.Pattern.CompoundOf && check<C.Value.Compound>(type) -> {
        TODO()
      }
      pattern is R.Pattern.Var && match<C.Value>(type)                 -> {
        val type = type ?: meta.freshValue(pattern.range)
        push(pattern.name, phase, type, null)
        C.Pattern.Var(pattern.name, type)
      }
      pattern is R.Pattern.Drop && match<C.Value>(type) -> {
        val type = type ?: meta.freshValue(pattern.range)
        C.Pattern.Drop(type)
      }
      pattern is R.Pattern.As && synth(type)            -> {
        val type = freeze().eval(checkTerm(pattern.type, phase, meta.freshType(pattern.type.range)))
        checkPattern(pattern.element, phase, type)
      }
      pattern is R.Pattern.Hole && match<C.Value>(type) -> C.Pattern.Hole
      synth(type)                                       -> invalidPattern(cannotSynthesize(pattern.range))
      check<C.Value>(type)                              -> {
        val synth = synthPattern(pattern, phase)
        if (next().sub(synth.type, type)) {
          synth
        } else {
          invalidPattern(next().typeMismatch(type, synth.type, pattern.range))
        }
      }
      else                                              -> error("unreachable")
    }.also {
      hover(it.type, pattern.range)
    }
  }

  private fun Lvl.sub(
    value1: C.Value,
    value2: C.Value,
  ): Boolean {
    val value1 = meta.forceValue(value1)
    val value2 = meta.forceValue(value2)
    return with(meta) { unifyValue(value1, value2) } // TODO: subtyping
  }

  private fun getPhase(modifiers: Collection<Modifier>): Phase {
    return if (Modifier.CONST in modifiers) Phase.CONST else Phase.WORLD
  }

  @OptIn(ExperimentalContracts::class)
  private inline fun synth(type: C.Value?): Boolean {
    contract {
      returns(false) implies (type != null)
      returns(true) implies (type == null)
    }
    return type == null
  }

  @OptIn(ExperimentalContracts::class)
  private inline fun <reified V : C.Value> check(type: C.Value?): Boolean {
    contract {
      returns(false) implies (type !is V)
      returns(true) implies (type is V)
    }
    return type is V
  }

  @OptIn(ExperimentalContracts::class)
  private inline fun <reified V : C.Value> match(type: C.Value?): Boolean {
    contract {
      returns(false) implies (type !is V?)
      returns(true) implies (type is V?)
    }
    return type is V?
  }

  private fun Ctx.hover(
    type: C.Value,
    range: Range,
  ) {
    if (hover == null && position != null && position in range) {
      val next = next()
      hover = { next.prettyValue(type) }
    }
  }

  private fun Lvl.typeMismatch(
    expected: C.Value,
    actual: C.Value,
    range: Range,
  ): Diagnostic {
    val expected = prettyValue(expected)
    val actual = prettyValue(actual)
    return diagnostic(
      range,
      """type mismatch:
        |  expected: $expected
        |  actual  : $actual
      """.trimMargin(),
      DiagnosticSeverity.Error,
    )
  }

  private fun Lvl.prettyValue(
    value: C.Value,
  ): String {
    return prettyTerm(with(meta) { zonkTerm(quote(value)) })
  }

  private fun invalidTerm(
    diagnostic: Diagnostic,
  ): C.Term {
    diagnostics += diagnostic
    return C.Term.Hole
  }

  private fun invalidPattern(
    diagnostic: Diagnostic,
  ): C.Pattern {
    diagnostics += diagnostic
    return C.Pattern.Hole
  }

  private class Ctx private constructor(
    private val _entries: MutableList<Entry>,
    private val _values: MutableList<Lazy<C.Value>>,
  ) {
    fun next(): Lvl {
      return Lvl(_entries.size)
    }

    operator fun get(level: Lvl): Entry {
      return _entries[level.value]
    }

    fun push(
      name: String,
      phase: Phase,
      type: C.Value,
      value: Lazy<C.Value>?,
    ) {
      _values += value ?: lazyOf(C.Value.Var(name, next(), type))
      _entries += Entry(name, phase, type)
    }

    private fun pop() {
      _values.removeLast()
      _entries.removeLast()
    }

    fun freeze(): Env {
      return _values.toPersistentList()
    }

    inline fun <R> restoring(block: Ctx.() -> R): R {
      val restore = next().value
      val result = block(this)
      repeat(next().value - restore) { pop() }
      return result
    }

    companion object {
      fun emptyCtx(): Ctx {
        return Ctx(mutableListOf(), mutableListOf())
      }
    }
  }

  private data class Entry(
    val name: String,
    val phase: Phase,
    val type: C.Value,
  )

  data class Result(
    val module: C.Module,
    val diagnostics: List<Diagnostic>,
    val completionItems: List<CompletionItem>,
    val hover: (() -> String)?,
  )

  companion object {
    private fun arityMismatch(
      expected: Int,
      actual: Int,
      range: Range,
    ): Diagnostic {
      return diagnostic(
        range,
        """arity mismatch:
          |  expected: $expected
          |  actual  : $actual
        """.trimMargin(),
        DiagnosticSeverity.Error,
      )
    }

    private fun phaseMismatch(
      expected: Phase,
      actual: Phase,
      range: Range,
    ): Diagnostic {
      return diagnostic(
        range,
        """phase mismatch:
          |  expected: $expected
          |  actual  : $actual
        """.trimMargin(),
        DiagnosticSeverity.Error,
      )
    }

    private fun expectedDef(
      range: Range,
    ): Diagnostic {
      return diagnostic(
        range,
        "expected definition: def",
        DiagnosticSeverity.Error,
      )
    }

    private fun cannotSynthesize(
      range: Range,
    ): Diagnostic {
      return diagnostic(
        range,
        "cannot synthesize",
        DiagnosticSeverity.Error,
      )
    }

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

    operator fun invoke(
      context: Context,
      dependencies: List<C.Module>,
      input: Resolve.Result,
      position: Position? = null,
    ): Result =
      Elaborate(dependencies, input, position).elaborate()
  }
}
