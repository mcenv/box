package mcx.phase.frontend

import kotlinx.collections.immutable.*
import mcx.ast.*
import mcx.data.NbtType
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
        val stage = if (Modifier.CONST in modifiers) 1 else 0
        val type = ctx.checkTerm(definition.type, stage, meta.freshType(definition.type.range))
        if (Modifier.REC in modifiers) {
          definitions[name] = C.Definition.Def(modifiers, name, type, null)
        }
        val body = definition.body?.let { ctx.checkTerm(it, stage, ctx.freeze().eval(type)) }
        with(meta) {
          resetUnsolvedMetas()
          val type = Lvl(0).zonk(type)
          val body = body?.let { Lvl(0).zonk(it) }
          meta.unsolvedMetas.forEach {
            diagnostics += unsolvedMeta(it.index, it.source)
          }
          C.Definition.Def(modifiers, name, type, body)
        }
      }
      is R.Definition.Hole -> error("unreachable")
    }.also { definitions[name] = it }
  }

  private inline fun Ctx.synthTerm(
    term: R.Term,
    stage: Int,
  ): Pair<C.Term, Value> {
    return elaborateTerm(term, stage, null)
  }

  private inline fun Ctx.checkTerm(
    term: R.Term,
    stage: Int,
    type: Value,
  ): C.Term {
    return elaborateTerm(term, stage, type).first
  }

  private fun Ctx.elaborateTerm(
    term: R.Term,
    stage: Int,
    type: Value?,
  ): Pair<C.Term, Value> {
    val type = type?.let { meta.force(type) }
    return when {
      term is R.Term.Tag && synth(type)                        -> {
        if (stage == 0) {
          invalidTerm(stageMismatch(1, 0, term.range), meta.fresh(term.range))
        } else {
          C.Term.Tag to TYPE_END
        }
      }
      term is R.Term.TagOf && synth(type)                      -> C.Term.TagOf(term.value) to Value.Tag
      term is R.Term.Type && synth(type)                       -> {
        val tag = checkTerm(term.tag, stage, Value.Tag)
        C.Term.Type(tag) to TYPE_BYTE
      }
      term is R.Term.Bool && synth(type)                       -> C.Term.Bool to TYPE_BYTE
      term is R.Term.BoolOf && synth(type)                     -> C.Term.BoolOf(term.value) to Value.Bool
      term is R.Term.If && match<Value>(type)                  -> {
        val condition = checkTerm(term.condition, stage, Value.Bool)
        val (thenBranch, thenBranchType) = elaborateTerm(term.thenBranch, stage, type)
        val (elseBranch, elseBranchType) = elaborateTerm(term.elseBranch, stage, type)
        val type = type ?: Value.Union(listOf(lazyOf(thenBranchType), lazyOf(elseBranchType)))
        C.Term.If(condition, thenBranch, elseBranch) to type
      }
      term is R.Term.Is && synth(type)                         -> {
        val (scrutineer, scrutineerType) = restoring { synthPattern(term.scrutineer, stage) }
        val scrutinee = checkTerm(term.scrutinee, stage, scrutineerType)
        C.Term.Is(scrutinee, scrutineer) to Value.Bool
      }
      term is R.Term.Byte && synth(type)                       -> C.Term.Byte to TYPE_BYTE
      term is R.Term.ByteOf && synth(type)                     -> C.Term.ByteOf(term.value) to Value.Byte
      term is R.Term.Short && synth(type)                      -> C.Term.Short to TYPE_SHORT
      term is R.Term.ShortOf && synth(type)                    -> C.Term.ShortOf(term.value) to Value.Short
      term is R.Term.Int && synth(type)                        -> C.Term.Int to TYPE_INT
      term is R.Term.IntOf && synth(type)                      -> C.Term.IntOf(term.value) to Value.Int
      term is R.Term.Long && synth(type)                       -> C.Term.Long to TYPE_LONG
      term is R.Term.LongOf && synth(type)                     -> C.Term.LongOf(term.value) to Value.Long
      term is R.Term.Float && synth(type)                      -> C.Term.Float to TYPE_FLOAT
      term is R.Term.FloatOf && synth(type)                    -> C.Term.FloatOf(term.value) to Value.Float
      term is R.Term.Double && synth(type)                     -> C.Term.Double to TYPE_DOUBLE
      term is R.Term.DoubleOf && synth(type)                   -> C.Term.DoubleOf(term.value) to Value.Double
      term is R.Term.String && synth(type)                     -> C.Term.String to TYPE_STRING
      term is R.Term.StringOf && synth(type)                   -> C.Term.StringOf(term.value) to Value.String
      term is R.Term.ByteArray && synth(type)                  -> C.Term.ByteArray to TYPE_BYTE_ARRAY
      term is R.Term.ByteArrayOf && synth(type)                -> {
        val elements = term.elements.map { checkTerm(it, stage, Value.Byte) }
        C.Term.ByteArrayOf(elements) to Value.ByteArray
      }
      term is R.Term.IntArray && synth(type)                   -> C.Term.IntArray to TYPE_INT_ARRAY
      term is R.Term.IntArrayOf && synth(type)                 -> {
        val elements = term.elements.map { checkTerm(it, stage, Value.Int) }
        C.Term.IntArrayOf(elements) to Value.IntArray
      }
      term is R.Term.LongArray && synth(type)                  -> C.Term.LongArray to TYPE_LONG_ARRAY
      term is R.Term.LongArrayOf && synth(type)                -> {
        val elements = term.elements.map { checkTerm(it, stage, Value.Long) }
        C.Term.LongArrayOf(elements) to Value.LongArray
      }
      term is R.Term.List && synth(type)                       -> {
        val element = checkTerm(term.element, stage, meta.freshType(term.element.range))
        C.Term.List(element) to TYPE_LIST
      }
      term is R.Term.ListOf && match<Value.List>(type)         -> {
        val (elements, elementsTypes) = term.elements.map { elaborateTerm(it, stage, type?.element?.value) }.unzip()
        val type = type ?: Value.List(lazyOf(Value.Union(elementsTypes.map { lazyOf(it) })))
        C.Term.ListOf(elements) to type
      }
      term is R.Term.Compound && synth(type)                   -> {
        val elements = term.elements.associate { (key, element) ->
          val element = checkTerm(element, stage, meta.freshType(element.range))
          key.value to element
        }
        C.Term.Compound(elements) to TYPE_COMPOUND
      }
      term is R.Term.CompoundOf && synth(type)                 -> {
        TODO()
      }
      term is R.Term.CompoundOf && check<Value.Compound>(type) -> {
        TODO()
      }
      term is R.Term.Union && match<Value.Type>(type)          -> {
        val type = type ?: meta.freshType(term.range)
        val elements = term.elements.map { checkTerm(it, stage, type) }
        C.Term.Union(elements) to type
      }
      term is R.Term.Func && synth(type)                       -> {
        restoring {
          val params = term.params.map { (pattern, term) ->
            val term = checkTerm(term, stage, meta.freshType(term.range))
            val pattern = checkPattern(pattern, stage, /* TODO: optimize */ freeze().eval(term))
            pattern to term
          }
          val result = checkTerm(term.result, stage, meta.freshType(term.result.range))
          C.Term.Func(params, result) to TYPE_COMPOUND
        }
      }
      term is R.Term.FuncOf && synth(type)                     -> {
        restoring {
          val (params, paramsTypes) = term.params.map { synthPattern(it, stage) }.unzip()
          val (result, resultType) = synthTerm(term.result, stage)
          val type = Value.Func(paramsTypes.map { lazyOf(it) }, Closure(freeze(), params, (next + next.collect(params).size).quote(resultType)))
          C.Term.FuncOf(params, result) to type
        }
      }
      term is R.Term.FuncOf && check<Value.Func>(type)         -> {
        val size = next
        restoring {
          if (type.params.size == term.params.size) {
            val (params, _) = term.params.mapIndexed { index, param ->
              elaboratePattern(param, stage, type.params.getOrNull(index)?.value)
            }.unzip()
            val result = checkTerm(term.result, stage, type.result(size.collect(type.result.binders)))
            C.Term.FuncOf(params, result) to type
          } else {
            invalidTerm(arityMismatch(type.params.size, term.params.size, term.range), type)
          }
        }
      }
      term is R.Term.Apply && synth(type)                      -> {
        val (func, maybeFuncType) = synthTerm(term.func, stage)
        val funcType = when (val funcType = meta.force(maybeFuncType)) {
          is Value.Func -> funcType
          else          -> {
            val params = term.args.map { lazyOf(meta.fresh(term.func.range)) }
            val result = Closure(
              freeze(),
              params.map { C.Pattern.Drop },
              next.quote(meta.fresh(term.func.range)),
            )
            Value.Func(params, result).also {
              if (!next.sub(funcType, it)) {
                return invalidTerm(next.typeMismatch(funcType, it, term.func.range), type)
              }
            }
          }
        }
        if (funcType.params.size != term.args.size) {
          return invalidTerm(arityMismatch(funcType.params.size, term.args.size, term.range), type)
        }
        val (args, _) = term.args.mapIndexed { index, arg ->
          elaborateTerm(arg, stage, funcType.params.getOrNull(index)?.value)
        }.unzip()
        val values = freeze()
        val type = funcType.result(args.map { lazy { values.eval(it) } })
        C.Term.Apply(func, args) to type
      }
      /*
      term is R.Term.Apply && check<Value>(type)                    -> {
        val args = term.args.map { elaborateTerm(it, stage, null) }
        val funcType = Value.Func(args.map { lazyOf(it.type) }, C.Closure(freeze(), args.map { C.Pattern.Drop(it.type) }, size.quote(type)))
        val func = elaborateTerm(term.func, stage, funcType)
        C.Term.Apply(func, args, type)
      }
      */
      term is R.Term.Code && synth(type)                       -> {
        if (stage == 0) {
          invalidTerm(stageMismatch(1, 0, term.range), meta.fresh(term.range))
        } else {
          val element = checkTerm(term.element, stage - 1, meta.freshType(term.element.range))
          C.Term.Code(element) to TYPE_END
        }
      }
      term is R.Term.CodeOf && match<Value.Code>(type)         -> {
        if (stage == 0) {
          invalidTerm(stageMismatch(1, 0, term.range), meta.fresh(term.range))
        } else {
          val (element, elementType) = elaborateTerm(term.element, stage - 1, type?.element?.value)
          val type = type ?: Value.Code(lazyOf(elementType))
          C.Term.CodeOf(element) to type
        }
      }
      term is R.Term.Splice && match<Value>(type)              -> {
        val type = type ?: meta.fresh(term.range)
        val element = checkTerm(term.element, stage + 1, Value.Code(lazyOf(type)))
        C.Term.Splice(element) to type
      }
      term is R.Term.Let && match<Value>(type)                 -> {
        val (init, initType) = synthTerm(term.init, stage)
        restoring {
          val (binder, binderType) = synthPattern(term.binder, stage)
          if (!next.sub(initType, binderType)) {
            diagnostics += next.typeMismatch(initType, binderType, term.init.range)
          }
          val (body, bodyType) = elaborateTerm(term.body, stage, type)
          val type = type ?: bodyType
          C.Term.Let(binder, init, body) to type
        }
      }
      term is R.Term.Var && synth(type)                        -> {
        val entry = this[next.toLvl(term.idx)]
        if (stage == entry.stage) {
          C.Term.Var(term.name, term.idx) to entry.type
        } else {
          invalidTerm(stageMismatch(stage, entry.stage, term.range), entry.type)
        }
      }
      term is R.Term.Def && synth(type)                        -> {
        when (val definition = definitions[term.name]) {
          is C.Definition.Def -> {
            if (stage == 0 && Modifier.CONST in definition.modifiers) {
              invalidTerm(stageMismatch(1, 0, term.range), type)
            } else {
              C.Term.Def(term.name, definition.body) to persistentListOf<Lazy<Value>>().eval(definition.type)
            }
          }
          else                -> invalidTerm(expectedDef(term.range), type)
        }
      }
      term is R.Term.As && synth(type)                         -> {
        val type = freeze().eval(checkTerm(term.type, stage, meta.freshType(term.type.range)))
        checkTerm(term.element, stage, type) to type
      }
      term is R.Term.Hole && match<Value>(type)                -> {
        val type = type ?: meta.fresh(term.range)
        C.Term.Hole to type
      }
      check<Value>(type)                                       -> {
        val synth = synthTerm(term, stage)
        if (next.sub(synth.second, type)) {
          synth
        } else {
          invalidTerm(next.typeMismatch(type, synth.second, term.range), type)
        }
      }
      else                                                     -> error("unreachable: $term $stage $type")
    }.also { (_, type) ->
      next.hover(type, term.range)
    }
  }

  private inline fun Ctx.synthPattern(
    pattern: R.Pattern,
    stage: Int,
  ): Pair<C.Pattern, Value> {
    return elaboratePattern(pattern, stage, null)
  }

  private inline fun Ctx.checkPattern(
    pattern: R.Pattern,
    stage: Int,
    type: Value,
  ): C.Pattern {
    return elaboratePattern(pattern, stage, type).first
  }

  private fun Ctx.elaboratePattern(
    pattern: R.Pattern,
    stage: Int,
    type: Value?,
  ): Pair<C.Pattern, Value> {
    val type = type?.let { meta.force(type) }
    return when {
      stage > 0 && check<Value.Tag>(type)                            -> elaboratePattern(pattern, stage - 1, type)
      pattern is R.Pattern.IntOf && synth(type)                      -> C.Pattern.IntOf(pattern.value) to Value.Int
      pattern is R.Pattern.CompoundOf && synth(type)                 -> {
        TODO()
      }
      pattern is R.Pattern.CompoundOf && check<Value.Compound>(type) -> {
        TODO()
      }
      pattern is R.Pattern.CodeOf && match<Value.Code>(type)         -> {
        if (stage == 0) {
          invalidPattern(stageMismatch(1, 0, pattern.range), type)
        } else {
          val (element, elementType) = elaboratePattern(pattern.element, stage - 1, type?.element?.value)
          val type = type ?: Value.Code(lazyOf(elementType))
          C.Pattern.CodeOf(element) to type
        }
      }
      pattern is R.Pattern.Var && match<Value>(type)                 -> {
        val type = type ?: meta.fresh(pattern.range)
        push(pattern.name, stage, type, null)
        C.Pattern.Var(pattern.name) to type
      }
      pattern is R.Pattern.Drop && match<Value>(type)                -> {
        val type = type ?: meta.fresh(pattern.range)
        C.Pattern.Drop to type
      }
      pattern is R.Pattern.As && synth(type)                         -> {
        val type = freeze().eval(checkTerm(pattern.type, stage, meta.freshType(pattern.type.range)))
        checkPattern(pattern.element, stage, type) to type
      }
      pattern is R.Pattern.Hole && match<Value>(type)                -> {
        val type = type ?: meta.fresh(pattern.range)
        C.Pattern.Hole to type
      }
      check<Value>(type)                                             -> {
        val synth = synthPattern(pattern, stage)
        if (next.sub(synth.second, type)) {
          synth
        } else {
          invalidPattern(next.typeMismatch(type, synth.second, pattern.range), type)
        }
      }
      else                                                           -> error("unreachable: $pattern $stage $type")
    }.also { (_, type) ->
      next.hover(type, pattern.range)
    }
  }

  private fun Lvl.sub(
    value1: Value,
    value2: Value,
  ): Boolean {
    val value1 = meta.force(value1)
    val value2 = meta.force(value2)
    return with(meta) { unify(value1, value2) } // TODO: subtyping
  }

  @OptIn(ExperimentalContracts::class)
  private inline fun synth(type: Value?): Boolean {
    contract {
      returns(false) implies (type != null)
      returns(true) implies (type == null)
    }
    return type == null
  }

  @OptIn(ExperimentalContracts::class)
  private inline fun <reified V : Value> check(type: Value?): Boolean {
    contract {
      returns(false) implies (type !is V)
      returns(true) implies (type is V)
    }
    return type is V
  }

  @OptIn(ExperimentalContracts::class)
  private inline fun <reified V : Value> match(type: Value?): Boolean {
    contract {
      returns(false) implies (type !is V?)
      returns(true) implies (type is V?)
    }
    return type is V?
  }

  private fun Lvl.hover(
    type: Value,
    range: Range,
  ) {
    if (hover == null && position != null && position in range) {
      hover = { prettyTerm(with(meta) { zonk(quote(type)) }) }
    }
  }

  private fun Lvl.typeMismatch(
    expected: Value,
    actual: Value,
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
    type: Value?,
  ): Pair<C.Term, Value> {
    diagnostics += diagnostic
    return C.Term.Hole to (type ?: meta.fresh(diagnostic.range))
  }

  private fun invalidPattern(
    diagnostic: Diagnostic,
    type: Value?,
  ): Pair<C.Pattern, Value> {
    diagnostics += diagnostic
    return C.Pattern.Hole to (type ?: meta.fresh(diagnostic.range))
  }

  private class Ctx private constructor(
    private val _entries: MutableList<Entry>,
    private val _values: MutableList<Lazy<Value>>,
  ) {
    val entries: List<Entry> get() = _entries
    val next: Lvl get() = Lvl(_entries.size)

    operator fun get(level: Lvl): Entry {
      return _entries[level.value]
    }

    fun push(
      name: String,
      stage: Int,
      type: Value,
      value: Lazy<Value>?,
    ) {
      _values += value ?: lazyOf(Value.Var(name, next))
      _entries += Entry(name, stage, type)
    }

    private fun pop() {
      _values.removeLast()
      _entries.removeLast()
    }

    fun freeze(): PersistentList<Lazy<Value>> {
      return _values.toPersistentList()
    }

    inline fun <R> restoring(block: Ctx.() -> R): R {
      val restore = next.value
      val result = block(this)
      repeat(next.value - restore) { pop() }
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
    val stage: Int,
    val type: Value,
  )

  data class Result(
    val module: C.Module,
    val diagnostics: List<Diagnostic>,
    val completionItems: List<CompletionItem>,
    val hover: (() -> String)?,
  )

  companion object {
    private val TYPE_END = Value.Type(lazyOf(Value.TagOf(NbtType.END)))
    private val TYPE_BYTE = Value.Type(lazyOf(Value.TagOf(NbtType.BYTE)))
    private val TYPE_SHORT = Value.Type(lazyOf(Value.TagOf(NbtType.SHORT)))
    private val TYPE_INT = Value.Type(lazyOf(Value.TagOf(NbtType.INT)))
    private val TYPE_LONG = Value.Type(lazyOf(Value.TagOf(NbtType.LONG)))
    private val TYPE_FLOAT = Value.Type(lazyOf(Value.TagOf(NbtType.FLOAT)))
    private val TYPE_DOUBLE = Value.Type(lazyOf(Value.TagOf(NbtType.DOUBLE)))
    private val TYPE_BYTE_ARRAY = Value.Type(lazyOf(Value.TagOf(NbtType.BYTE_ARRAY)))
    private val TYPE_INT_ARRAY = Value.Type(lazyOf(Value.TagOf(NbtType.INT_ARRAY)))
    private val TYPE_LONG_ARRAY = Value.Type(lazyOf(Value.TagOf(NbtType.LONG_ARRAY)))
    private val TYPE_STRING = Value.Type(lazyOf(Value.TagOf(NbtType.STRING)))
    private val TYPE_LIST = Value.Type(lazyOf(Value.TagOf(NbtType.LIST)))
    private val TYPE_COMPOUND = Value.Type(lazyOf(Value.TagOf(NbtType.COMPOUND)))

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

    private fun expectedDef(
      range: Range,
    ): Diagnostic {
      return diagnostic(
        range,
        "expected definition: def",
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
