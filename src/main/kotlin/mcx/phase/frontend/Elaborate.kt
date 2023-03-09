package mcx.phase.frontend

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import mcx.ast.*
import mcx.data.NbtType
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
  private val definitions: Map<DefinitionLocation, C.Definition> = dependencies.flatMap { dependency -> dependency.definitions.map { it.name to it } }.toMap()

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
        val tag = lazy { meta.fresh(definition.type.range) }
        val type = ctx.freeze().eval(ctx.elaborateTerm(definition.type, stage, C.Value.Type(tag)))
        val body = definition.body?.let { ctx.elaborateTerm(it, stage, type) }
        C.Definition.Def(modifiers, name, type, body)
      }
      is R.Definition.Hole -> null
    }
  }

  private fun Ctx.elaborateTerm(
    term: R.Term,
    stage: Int,
    type: C.Value?,
  ): C.Term {
    val type = type?.let { meta.force(type) }
    return when {
      term is R.Term.Tag && stage > 0 && synth(type)                  -> C.Term.Tag(C.Value.Type(lazyOf(C.Value.TagOf(NbtType.BYTE))))
      term is R.Term.TagOf && synth(type)                             -> C.Term.TagOf(term.value, C.Value.Tag)
      term is R.Term.Type && synth(type)                              -> {
        val tag = elaborateTerm(term.tag, stage, C.Value.Tag)
        val type = C.Value.Type(lazyOf(C.Value.TagOf(NbtType.BYTE)))
        C.Term.Type(tag, type)
      }
      term is R.Term.Bool && synth(type)                              -> {
        val type = C.Value.Type(lazyOf(C.Value.TagOf(NbtType.BYTE)))
        C.Term.Bool(type)
      }
      term is R.Term.Byte && synth(type)                              -> {
        val type = C.Value.Type(lazyOf(C.Value.TagOf(NbtType.BYTE)))
        C.Term.Byte(type)
      }
      term is R.Term.BoolOf && synth(type)                            -> C.Term.BoolOf(term.value, C.Value.Byte)
      term is R.Term.If && synth(type)                                -> {
        val condition = elaborateTerm(term.condition, stage, C.Value.Byte)
        val thenBranch = elaborateTerm(term.thenBranch, stage, null)
        val elseBranch = elaborateTerm(term.elseBranch, stage, null)
        val type = C.Value.Union(listOf(lazyOf(thenBranch.type), lazyOf(elseBranch.type)))
        C.Term.If(condition, thenBranch, elseBranch, type)
      }
      term is R.Term.If && check<C.Value>(type)                       -> {
        val condition = elaborateTerm(term.condition, stage, C.Value.Byte)
        val thenBranch = elaborateTerm(term.thenBranch, stage, type)
        val elseBranch = elaborateTerm(term.elseBranch, stage, type)
        C.Term.If(condition, thenBranch, elseBranch, type)
      }
      term is R.Term.Is && synth(type)                                -> {
        val scrutineer = restoring { elaboratePattern(term.scrutineer, stage, null) }
        val scrutinee = elaborateTerm(term.scrutinee, stage, scrutineer.type)
        C.Term.Is(scrutinee, scrutineer, C.Value.Bool)
      }
      term is R.Term.Byte && synth(type)                              -> {
        val type = C.Value.Type(lazyOf(C.Value.TagOf(NbtType.BYTE)))
        C.Term.Byte(type)
      }
      term is R.Term.ByteOf && synth(type)                            -> C.Term.ByteOf(term.value, C.Value.Byte)
      term is R.Term.Short && synth(type)                             -> {
        val type = C.Value.Type(lazyOf(C.Value.TagOf(NbtType.SHORT)))
        C.Term.Short(type)
      }
      term is R.Term.ShortOf && synth(type)                           -> C.Term.ShortOf(term.value, C.Value.Short)
      term is R.Term.Int && synth(type)                               -> {
        val type = C.Value.Type(lazyOf(C.Value.TagOf(NbtType.INT)))
        C.Term.Int(type)
      }
      term is R.Term.IntOf && synth(type)                             -> C.Term.IntOf(term.value, C.Value.Int)
      term is R.Term.Long && synth(type)                              -> {
        val type = C.Value.Type(lazyOf(C.Value.TagOf(NbtType.LONG)))
        C.Term.Long(type)
      }
      term is R.Term.LongOf && synth(type)                            -> C.Term.LongOf(term.value, C.Value.Long)
      term is R.Term.Float && synth(type)                             -> {
        val type = C.Value.Type(lazyOf(C.Value.TagOf(NbtType.FLOAT)))
        C.Term.Float(type)
      }
      term is R.Term.FloatOf && synth(type)                           -> C.Term.FloatOf(term.value, C.Value.Float)
      term is R.Term.Double && synth(type)                            -> {
        val type = C.Value.Type(lazyOf(C.Value.TagOf(NbtType.DOUBLE)))
        C.Term.Double(type)
      }
      term is R.Term.DoubleOf && synth(type)                          -> C.Term.DoubleOf(term.value, C.Value.Double)
      term is R.Term.String && synth(type)                            -> {
        val type = C.Value.Type(lazyOf(C.Value.TagOf(NbtType.STRING)))
        C.Term.String(type)
      }
      term is R.Term.StringOf && synth(type)                          -> C.Term.StringOf(term.value, C.Value.String)
      term is R.Term.ByteArray && synth(type)                         -> {
        val type = C.Value.Type(lazyOf(C.Value.TagOf(NbtType.BYTE_ARRAY)))
        C.Term.ByteArray(type)
      }
      term is R.Term.ByteArrayOf && synth(type)                       -> {
        val elements = term.elements.map { elaborateTerm(it, stage, C.Value.Byte) }
        C.Term.ByteArrayOf(elements, C.Value.ByteArray)
      }
      term is R.Term.IntArray && synth(type)                          -> {
        val type = C.Value.Type(lazyOf(C.Value.TagOf(NbtType.INT_ARRAY)))
        C.Term.IntArray(type)
      }
      term is R.Term.IntArrayOf && synth(type)                        -> {
        val elements = term.elements.map { elaborateTerm(it, stage, C.Value.Int) }
        C.Term.IntArrayOf(elements, C.Value.IntArray)
      }
      term is R.Term.LongArray && synth(type)                         -> {
        val type = C.Value.Type(lazyOf(C.Value.TagOf(NbtType.LONG_ARRAY)))
        C.Term.LongArray(type)
      }
      term is R.Term.LongArrayOf && synth(type)                       -> {
        val elements = term.elements.map { elaborateTerm(it, stage, C.Value.Long) }
        C.Term.LongArrayOf(elements, C.Value.LongArray)
      }
      term is R.Term.List && synth(type)                              -> {
        val tag = lazy { meta.fresh(term.element.range) }
        val element = elaborateTerm(term.element, stage, C.Value.Type(tag))
        val type = C.Value.Type(lazyOf(C.Value.TagOf(NbtType.LIST)))
        C.Term.List(element, type)
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
          val tag = lazy { meta.fresh(element.range) }
          val element = elaborateTerm(element, stage, C.Value.Type(tag))
          key.value to element
        }
        val type = C.Value.Type(lazyOf(C.Value.TagOf(NbtType.COMPOUND)))
        C.Term.Compound(elements, type)
      }
      term is R.Term.CompoundOf && synth(type)                        -> {
        TODO()
      }
      term is R.Term.CompoundOf && check<C.Value.Compound>(type)      -> {
        TODO()
      }
      term is R.Term.Union && synth(type)                             -> {
        val tag = lazy { meta.fresh(term.range) }
        val elements = term.elements.map { elaborateTerm(it, stage, C.Value.Type(tag)) }
        val type = C.Value.Type(lazyOf(C.Value.TagOf(NbtType.BYTE)))
        C.Term.Union(elements, type)
      }
      term is R.Term.Union && check<C.Value.Type>(type)               -> {
        val elements = term.elements.map { elaborateTerm(it, stage, type) }
        C.Term.Union(elements, type)
      }
      term is R.Term.Func && synth(type)                              -> {
        restoring {
          val params = term.params.map { (pattern, term) ->
            val tag = lazy { meta.fresh(term.range) }
            val term = elaborateTerm(term, stage, C.Value.Type(tag))
            val pattern = elaboratePattern(pattern, stage, /* TODO: optimize */ freeze().eval(term))
            pattern to term
          }
          val tag = lazy { meta.fresh(term.result.range) }
          val result = elaborateTerm(term.result, stage, C.Value.Type(tag))
          val type = C.Value.Type(lazyOf(C.Value.TagOf(NbtType.COMPOUND)))
          C.Term.Func(params, result, type)
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
        restoring {
          if (type.params.size == term.params.size) {
            val params = term.params.mapIndexed { index, param ->
              elaboratePattern(param, stage, type.params.getOrNull(index)?.value)
            }
            val result = elaborateTerm(term.result, stage, type.result())
            C.Term.FuncOf(params, result, type)
          } else {
            diagnostics += arityMismatch(type.params.size, term.params.size, term.range)
            C.Term.Hole(type)
          }
        }
      }
      term is R.Term.Apply && synth(type)                             -> {
        val func = elaborateTerm(term.func, stage, null)
        when (val funcType = meta.force(func.type)) {
          is C.Value.Func -> {
            if (funcType.params.size == term.args.size) {
              val args = term.args.mapIndexed { index, arg -> elaborateTerm(arg, stage, funcType.params.getOrNull(index)?.value) }
              val values = freeze()
              val type = funcType.result(args.map { lazy { values.eval(it) } })
              C.Term.Apply(func, args, type)
            } else {
              diagnostics += arityMismatch(funcType.params.size, term.args.size, term.range)
              val type = meta.fresh(term.range)
              C.Term.Hole(type)
            }
          }
          else            -> {
            TODO("solve meta")
          }
        }
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
        val tag = lazy { meta.fresh(term.range) }
        val element = elaborateTerm(term.element, stage - 1, C.Value.Type(tag))
        val type = C.Value.Type(lazyOf(C.Value.TagOf(NbtType.BYTE)))
        C.Term.Code(element, type)
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
        val type = meta.fresh(term.range)
        val element = elaborateTerm(term.element, stage + 1, C.Value.Code(lazyOf(type)))
        C.Term.Splice(element, type)
      }
      term is R.Term.Splice && check<C.Value.Code>(type)              -> {
        val element = elaborateTerm(term.element, stage + 1, type.element.value)
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
          diagnostics += stageMismatch(stage, entry.stage, term.range)
          C.Term.Hole(entry.type)
        }
      }
      term is R.Term.Def && synth(type)                               -> {
        when (val definition = definitions[term.name]) {
          is C.Definition.Def -> {
            val body = definition.body ?: TODO("builtin")
            C.Term.Def(term.name, body, definition.type)
          }
          else                -> TODO()
        }
      }
      check<C.Value>(type)                                            -> {
        val synth = elaborateTerm(term, stage, null)
        if (size.sub(synth.type, type)) {
          synth
        } else {
          diagnostics += size.typeMismatch(type, synth.type, term.range)
          C.Term.Hole(type)
        }
      }
      else                                                            -> error("unreachable")
    }
  }

  private fun Ctx.elaboratePattern(
    pattern: R.Pattern,
    stage: Int,
    type: C.Value?,
  ): C.Pattern {
    val type = type?.let { meta.force(type) }
    return when {
      pattern is R.Pattern.IntOf && synth(type)                        -> C.Pattern.IntOf(pattern.value, C.Value.Int)
      pattern is R.Pattern.CompoundOf && synth(type)                   -> {
        TODO()
      }
      pattern is R.Pattern.CompoundOf && check<C.Value.Compound>(type) -> {
        TODO()
      }
      pattern is R.Pattern.Splice && synth(type)                       -> {
        val type = meta.fresh(pattern.range)
        val element = elaboratePattern(pattern.element, stage + 1, C.Value.Code(lazyOf(type)))
        C.Pattern.Splice(element, type)
      }
      pattern is R.Pattern.Splice && check<C.Value.Code>(type)         -> {
        val element = elaboratePattern(pattern.element, stage + 1, type.element.value)
        C.Pattern.Splice(element, type)
      }
      pattern is R.Pattern.Var && synth(type)                          -> {
        val type = meta.fresh(pattern.range)
        push(pattern.name, stage, type, null)
        C.Pattern.Var(pattern.name, pattern.level, type)
      }
      pattern is R.Pattern.Var && check<C.Value>(type)                 -> {
        push(pattern.name, stage, type, null)
        C.Pattern.Var(pattern.name, pattern.level, type)
      }
      pattern is R.Pattern.Drop && synth(type)                         -> {
        val type = meta.fresh(pattern.range)
        C.Pattern.Drop(type)
      }
      pattern is R.Pattern.Drop && check<C.Value>(type)                -> C.Pattern.Drop(type)
      check<C.Value>(type)                                             -> {
        val synth = elaboratePattern(pattern, stage, null)
        if (size.sub(synth.type, type)) {
          synth
        } else {
          diagnostics += size.typeMismatch(type, synth.type, pattern.range)
          C.Pattern.Hole(type)
        }
      }
      else                                                             -> error("unreachable")
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
      _values += value ?: lazyOf(C.Value.Var(name, size))
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
    private fun Int.typeMismatch(
      expected: C.Value,
      actual: C.Value,
      range: Range,
    ): Diagnostic {
      val expected = prettyTerm(quote(expected))
      val actual = prettyTerm(quote(actual))
      return diagnostic(
        range,
        """type mismatch:
          |  expected: $expected
          |  actual  : $actual
        """.trimIndent(),
        DiagnosticSeverity.Error,
      )
    }

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
        """.trimIndent(),
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
        """.trimIndent(),
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
