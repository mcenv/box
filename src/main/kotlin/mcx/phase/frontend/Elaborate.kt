package mcx.phase.frontend

import kotlinx.collections.immutable.*
import mcx.ast.*
import mcx.lsp.contains
import mcx.lsp.diagnostic
import mcx.phase.*
import mcx.phase.frontend.Elaborate.Ctx.Companion.emptyCtx
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
    return Result(
      elaborateModule(input.module),
      meta,
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
        val stage = if (Modifier.CONST in modifiers) 1 else 0
        val type = ctx.freeze().eval(ctx.elaborateTerm(definition.type, stage, meta.freshType(definition.type.range)))
        if (Modifier.REC in modifiers) {
          definitions[name] = C.Definition.Def(modifiers, name, type, null)
        }
        val body = definition.body?.let { ctx.elaborateTerm(it, stage, type) }
        C.Definition.Def(modifiers, name, type, body)
      }
      is R.Definition.Hole -> error("unreachable")
    }.also { definitions[name] = it }
  }

  private fun Ctx.elaborateTerm(
    term: R.Term,
    stage: Int,
    type: C.Value?,
  ): C.Term {
    val type = type?.let { meta.force(type) }
    return when {
      term is R.Term.Tag && stage > 0 && synth(type)                  -> C.Term.Tag
      term is R.Term.TagOf && synth(type)                             -> C.Term.TagOf(term.value)
      term is R.Term.Type && synth(type)                              -> {
        val tag = elaborateTerm(term.tag, stage, C.Value.Tag)
        C.Term.Type(tag)
      }
      term is R.Term.Bool && synth(type)                              -> C.Term.Bool
      term is R.Term.Byte && synth(type)                              -> C.Term.Byte
      term is R.Term.BoolOf && synth(type)                            -> C.Term.BoolOf(term.value)
      term is R.Term.If && synth(type)                                -> {
        val condition = elaborateTerm(term.condition, stage, C.Value.Bool)
        val thenBranch = elaborateTerm(term.thenBranch, stage, null)
        val elseBranch = elaborateTerm(term.elseBranch, stage, null)
        val type = C.Value.Union(listOf(lazyOf(thenBranch.type), lazyOf(elseBranch.type)))
        C.Term.If(condition, thenBranch, elseBranch, type)
      }
      term is R.Term.If && check<C.Value>(type)                       -> {
        val condition = elaborateTerm(term.condition, stage, C.Value.Bool)
        val thenBranch = elaborateTerm(term.thenBranch, stage, type)
        val elseBranch = elaborateTerm(term.elseBranch, stage, type)
        C.Term.If(condition, thenBranch, elseBranch, type)
      }
      term is R.Term.Is && synth(type)                                -> {
        val scrutineer = restoring { elaboratePattern(term.scrutineer, stage, null) }
        val scrutinee = elaborateTerm(term.scrutinee, stage, scrutineer.type)
        C.Term.Is(scrutinee, scrutineer)
      }
      term is R.Term.Byte && synth(type)                              -> C.Term.Byte
      term is R.Term.ByteOf && synth(type)                            -> C.Term.ByteOf(term.value)
      term is R.Term.Short && synth(type)                             -> C.Term.Short
      term is R.Term.ShortOf && synth(type)                           -> C.Term.ShortOf(term.value)
      term is R.Term.Int && synth(type)                               -> C.Term.Int
      term is R.Term.IntOf && synth(type)                             -> C.Term.IntOf(term.value)
      term is R.Term.Long && synth(type)                              -> C.Term.Long
      term is R.Term.LongOf && synth(type)                            -> C.Term.LongOf(term.value)
      term is R.Term.Float && synth(type)                             -> C.Term.Float
      term is R.Term.FloatOf && synth(type)                           -> C.Term.FloatOf(term.value)
      term is R.Term.Double && synth(type)                            -> C.Term.Double
      term is R.Term.DoubleOf && synth(type)                          -> C.Term.DoubleOf(term.value)
      term is R.Term.String && synth(type)                            -> C.Term.String
      term is R.Term.StringOf && synth(type)                          -> C.Term.StringOf(term.value)
      term is R.Term.ByteArray && synth(type)                         -> C.Term.ByteArray
      term is R.Term.ByteArrayOf && synth(type)                       -> {
        val elements = term.elements.map { elaborateTerm(it, stage, C.Value.Byte) }
        C.Term.ByteArrayOf(elements)
      }
      term is R.Term.IntArray && synth(type)                          -> C.Term.IntArray
      term is R.Term.IntArrayOf && synth(type)                        -> {
        val elements = term.elements.map { elaborateTerm(it, stage, C.Value.Int) }
        C.Term.IntArrayOf(elements)
      }
      term is R.Term.LongArray && synth(type)                         -> C.Term.LongArray
      term is R.Term.LongArrayOf && synth(type)                       -> {
        val elements = term.elements.map { elaborateTerm(it, stage, C.Value.Long) }
        C.Term.LongArrayOf(elements)
      }
      term is R.Term.List && synth(type)                              -> {
        val element = elaborateTerm(term.element, stage, meta.freshType(term.element.range))
        C.Term.List(element)
      }
      term is R.Term.ListOf && synth(type)                            -> {
        val elements = term.elements.map { elaborateTerm(it, stage, null) }
        val type = C.Value.List(lazyOf(C.Value.Union(elements.map { lazyOf(it.type) })))
        C.Term.ListOf(elements, type)
      }
      term is R.Term.ListOf && check<C.Value.List>(type)              -> {
        val elements = term.elements.map { elaborateTerm(it, stage, type.element.value) }
        C.Term.ListOf(elements, type)
      }
      term is R.Term.Compound && synth(type)                          -> {
        val elements = term.elements.associate { (key, element) ->
          val element = elaborateTerm(element, stage, meta.freshType(element.range))
          key.value to element
        }
        C.Term.Compound(elements)
      }
      term is R.Term.CompoundOf && synth(type)                        -> {
        TODO()
      }
      term is R.Term.CompoundOf && check<C.Value.Compound>(type)      -> {
        TODO()
      }
      term is R.Term.Union && synth(type)                             -> {
        val type = meta.freshType(term.range)
        val elements = term.elements.map { elaborateTerm(it, stage, type) }
        C.Term.Union(elements, type)
      }
      term is R.Term.Union && check<C.Value.Type>(type)               -> {
        val elements = term.elements.map { elaborateTerm(it, stage, type) }
        C.Term.Union(elements, type)
      }
      term is R.Term.Func && synth(type)                              -> {
        restoring {
          val params = term.params.map { (pattern, term) ->
            val term = elaborateTerm(term, stage, meta.freshType(term.range))
            val pattern = elaboratePattern(pattern, stage, /* TODO: optimize */ freeze().eval(term))
            pattern to term
          }
          val result = elaborateTerm(term.result, stage, meta.freshType(term.result.range))
          C.Term.Func(params, result)
        }
      }
      term is R.Term.FuncOf && synth(type)                            -> {
        restoring {
          val params = term.params.map { elaboratePattern(it, stage, null) }
          val result = elaborateTerm(term.result, stage, null)
          val type = C.Value.Func(params.map { lazyOf(it.type) }, C.Closure(freeze(), params, result))
          C.Term.FuncOf(params, result, type)
        }
      }
      term is R.Term.FuncOf && check<C.Value.Func>(type)              -> {
        val size = size
        restoring {
          if (type.params.size == term.params.size) {
            val params = term.params.mapIndexed { index, param ->
              elaboratePattern(param, stage, type.params.getOrNull(index)?.value)
            }
            val result = elaborateTerm(term.result, stage, type.result(size.collect(type.result.binders)))
            C.Term.FuncOf(params, result, type)
          } else {
            invalidTerm(arityMismatch(type.params.size, term.params.size, term.range), type)
          }
        }
      }
      term is R.Term.Apply && synth(type)                             -> {
        val func = elaborateTerm(term.func, stage, null)
        val funcType = when (val funcType = meta.force(func.type)) {
          is C.Value.Func -> funcType
          else            -> {
            val params = term.args.map { lazyOf(meta.fresh(term.func.range, meta.freshType(term.func.range))) }
            val result = C.Closure(
              freeze(),
              params.map { C.Pattern.Drop(it.value) },
              size.quote(meta.fresh(term.func.range, meta.freshType(term.func.range))),
            )
            C.Value.Func(params, result).also {
              if (!size.sub(funcType, it)) {
                return invalidTerm(size.typeMismatch(funcType, it, term.func.range), type)
              }
            }
          }
        }
        if (funcType.params.size != term.args.size) {
          return invalidTerm(arityMismatch(funcType.params.size, term.args.size, term.range), type)
        }
        val args = term.args.mapIndexed { index, arg ->
          elaborateTerm(arg, stage, funcType.params.getOrNull(index)?.value)
        }
        val values = freeze()
        val type = funcType.result(args.map { lazy { values.eval(it) } })
        C.Term.Apply(func, args, type)
      }
      /*
      term is R.Term.Apply && check<C.Value>(type)                    -> {
        val args = term.args.map { elaborateTerm(it, stage, null) }
        val funcType = C.Value.Func(args.map { lazyOf(it.type) }, C.Closure(freeze(), args.map { C.Pattern.Drop(it.type) }, size.quote(type)))
        val func = elaborateTerm(term.func, stage, funcType)
        C.Term.Apply(func, args, type)
      }
      */
      term is R.Term.Code && stage > 0 && synth(type)                 -> {
        val element = elaborateTerm(term.element, stage - 1, meta.freshType(term.element.range))
        C.Term.Code(element)
      }
      term is R.Term.CodeOf && stage > 0 && synth(type)               -> {
        val element = elaborateTerm(term.element, stage - 1, null)
        val type = C.Value.Code(lazyOf(element.type))
        C.Term.CodeOf(element, type)
      }
      term is R.Term.CodeOf && stage > 0 && check<C.Value.Code>(type) -> {
        val element = elaborateTerm(term.element, stage - 1, type.element.value)
        C.Term.CodeOf(element, type)
      }
      term is R.Term.Splice && synth(type)                            -> {
        val type = meta.fresh(term.range, meta.freshType(term.range))
        val element = elaborateTerm(term.element, stage + 1, C.Value.Code(lazyOf(type)))
        C.Term.Splice(element, type)
      }
      term is R.Term.Splice && check<C.Value>(type)                   -> {
        val element = elaborateTerm(term.element, stage + 1, C.Value.Code(lazyOf(type)))
        C.Term.Splice(element, type)
      }
      term is R.Term.Let && synth(type)                               -> {
        val init = elaborateTerm(term.init, stage, null)
        restoring {
          val binder = elaboratePattern(term.binder, stage, init.type)
          val body = elaborateTerm(term.body, stage, null)
          C.Term.Let(binder, init, body, body.type)
        }
      }
      term is R.Term.Let && check<C.Value>(type)                      -> {
        val init = elaborateTerm(term.init, stage, null)
        restoring {
          val binder = elaboratePattern(term.binder, stage, init.type)
          val body = elaborateTerm(term.body, stage, type)
          C.Term.Let(binder, init, body, type)
        }
      }
      term is R.Term.Var && synth(type)                               -> {
        val level = this[term.name]
        val entry = entries[level]
        if (stage == entry.stage) {
          C.Term.Var(term.name, level, entry.type)
        } else {
          invalidTerm(stageMismatch(stage, entry.stage, term.range), entry.type)
        }
      }
      term is R.Term.Def && synth(type)                               -> {
        when (val definition = definitions[term.name]) {
          is C.Definition.Def -> {
            if (stage == 0 && Modifier.CONST in definition.modifiers) {
              invalidTerm(stageMismatch(1, 0, term.range), type)
            } else {
              C.Term.Def(term.name, definition.body, definition.type)
            }
          }
          else                -> invalidTerm(unknownDef(term.name, term.range), type)
        }
      }
      term is R.Term.Hole && synth(type)                              -> {
        val type = meta.fresh(term.range, meta.freshType(term.range))
        C.Term.Hole(type)
      }
      term is R.Term.Hole && check<C.Value>(type)                     -> C.Term.Hole(type)
      check<C.Value>(type)                                            -> {
        val synth = elaborateTerm(term, stage, null)
        if (size.sub(synth.type, type)) {
          synth
        } else {
          invalidTerm(size.typeMismatch(type, synth.type, term.range), type)
        }
      }
      else                                                            -> error("unreachable: $term $stage $type")
    }.also {
      size.hover(it.type, term.range)
    }
  }

  private fun Ctx.elaboratePattern(
    pattern: R.Pattern,
    stage: Int,
    type: C.Value?,
  ): C.Pattern {
    val type = type?.let { meta.force(type) }
    return when {
      stage > 0 && check<C.Value.Tag>(type)                                 -> elaboratePattern(pattern, stage - 1, type)
      pattern is R.Pattern.IntOf && synth(type)                             -> C.Pattern.IntOf(pattern.value)
      pattern is R.Pattern.CompoundOf && synth(type)                        -> {
        TODO()
      }
      pattern is R.Pattern.CompoundOf && check<C.Value.Compound>(type)      -> {
        TODO()
      }
      pattern is R.Pattern.CodeOf && stage > 0 && synth(type)               -> {
        val element = elaboratePattern(pattern.element, stage - 1, null)
        val type = C.Value.Code(lazyOf(element.type))
        C.Pattern.CodeOf(element, type)
      }
      pattern is R.Pattern.CodeOf && stage > 0 && check<C.Value.Code>(type) -> {
        val element = elaboratePattern(pattern.element, stage - 1, type.element.value)
        C.Pattern.CodeOf(element, type)
      }
      pattern is R.Pattern.Var && synth(type)                               -> {
        val type = meta.fresh(pattern.range, meta.freshType(pattern.range))
        push(pattern.name, stage, type, null)
        C.Pattern.Var(pattern.name, pattern.level, type)
      }
      pattern is R.Pattern.Var && check<C.Value>(type)                      -> {
        push(pattern.name, stage, type, null)
        C.Pattern.Var(pattern.name, pattern.level, type)
      }
      pattern is R.Pattern.Drop && synth(type)                              -> {
        val type = meta.fresh(pattern.range, meta.freshType(pattern.range))
        C.Pattern.Drop(type)
      }
      pattern is R.Pattern.Drop && check<C.Value>(type)                     -> C.Pattern.Drop(type)
      pattern is R.Pattern.Hole && synth(type)                              -> {
        val type = meta.fresh(pattern.range, meta.freshType(pattern.range))
        C.Pattern.Hole(type)
      }
      pattern is R.Pattern.Hole && check<C.Value>(type)                     -> C.Pattern.Hole(type)
      check<C.Value>(type)                                                  -> {
        val synth = elaboratePattern(pattern, stage, null)
        if (size.sub(synth.type, type)) {
          synth
        } else {
          invalidPattern(size.typeMismatch(type, synth.type, pattern.range), type)
        }
      }
      else                                                                  -> error("unreachable: $pattern $stage $type")
    }.also {
      size.hover(it.type, pattern.range)
    }
  }

  private fun Int.sub(
    value1: C.Value,
    value2: C.Value,
  ): Boolean {
    val value1 = meta.force(value1)
    val value2 = meta.force(value2)
    return with(meta) { unify(value1, value2) } // TODO: subtyping
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

  private fun Int.hover(
    type: C.Value,
    range: Range,
  ) {
    if (hover == null && position != null && position in range) {
      hover = { prettyTerm(with(meta) { zonk(quote(type)) }) }
    }
  }

  private fun Int.typeMismatch(
    expected: C.Value,
    actual: C.Value,
    range: Range,
  ): Diagnostic {
    val expected = prettyTerm(with(meta) { zonk(quote(expected)) })
    val actual = prettyTerm(with(meta) { zonk(quote(actual)) })
    return diagnostic(
      range,
      """type mismatch:
        |  expected: $expected
        |  actual  : $actual
      """.trimMargin(),
      DiagnosticSeverity.Error,
    )
  }

  private fun invalidTerm(
    diagnostic: Diagnostic,
    type: C.Value?,
  ): C.Term {
    diagnostics += diagnostic
    return C.Term.Hole(type ?: meta.fresh(diagnostic.range, meta.freshType(diagnostic.range)))
  }

  private fun invalidPattern(
    diagnostic: Diagnostic,
    type: C.Value?,
  ): C.Pattern {
    diagnostics += diagnostic
    return C.Pattern.Hole(type ?: meta.fresh(diagnostic.range, meta.freshType(diagnostic.range)))
  }

  private class Ctx private constructor(
    private val _entries: MutableList<Entry>,
    private val _values: MutableList<Lazy<C.Value>>,
  ) {
    val entries: List<Entry> get() = _entries
    val size: Int get() = _entries.size

    operator fun get(name: String): Int {
      return _entries.indexOfLast { it.name == name }.also { require(it != -1) }
    }

    fun push(
      name: String,
      stage: Int,
      type: C.Value,
      value: Lazy<C.Value>?,
    ) {
      _values += value ?: lazyOf(C.Value.Var(name, size, type))
      _entries += Entry(name, stage, type)
    }

    private fun pop() {
      _values.removeLast()
      _entries.removeLast()
    }

    fun freeze(): PersistentList<Lazy<C.Value>> {
      return _values.toPersistentList()
    }

    inline fun <R> restoring(block: Ctx.() -> R): R {
      val restore = size
      val result = block(this)
      repeat(size - restore) { pop() }
      return result
    }

    companion object {
      fun emptyCtx(): Ctx {
        return Ctx(mutableListOf(), mutableListOf())
      }
    }
  }

  private class Entry(
    val name: String,
    val stage: Int,
    val type: C.Value,
  )

  data class Result(
    val module: C.Module,
    val meta: Meta,
    val diagnostics: List<Diagnostic>,
    val completionItems: List<CompletionItem>,
    val hover: (() -> String)?,
  )

  companion object {
    private fun stageMismatch(
      expected: Int,
      actual: Int,
      range: Range,
    ): Diagnostic {
      return diagnostic(
        range,
        """stage mismatch:
          |  expected: $expected
          |  actual  : $actual
        """.trimMargin(),
        DiagnosticSeverity.Error,
      )
    }

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

    private fun unknownDef(
      name: DefinitionLocation,
      range: Range,
    ): Diagnostic {
      return diagnostic(
        range,
        "unknown def: '$name'",
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
