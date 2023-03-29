package mcx.pass.frontend

import kotlinx.collections.immutable.*
import mcx.ast.*
import mcx.lsp.Instruction
import mcx.lsp.contains
import mcx.lsp.diagnostic
import mcx.pass.*
import mcx.pass.frontend.Elaborate.Ctx.Companion.emptyCtx
import mcx.util.toSubscript
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import mcx.ast.Core as C
import mcx.ast.Resolved as R

@Suppress("NAME_SHADOWING", "NOTHING_TO_INLINE")
class Elaborate private constructor(
  dependencies: List<C.Module>,
  private val input: Resolve.Result,
  private val instruction: Instruction?,
) {
  private val meta: Meta = Meta()
  private val definitions: MutableMap<DefinitionLocation, C.Definition> = dependencies.flatMap { dependency -> dependency.definitions.map { it.name to it } }.toMap().toMutableMap()
  private val diagnostics: MutableList<Diagnostic> = mutableListOf()
  private var hover: (() -> String)? = null
  private var inlayHints: MutableList<InlayHint> = mutableListOf()

  private fun elaborate(): Result {
    val module = elaborateModule(input.module)
    return Result(
      module,
      input.diagnostics + diagnostics,
      hover,
      inlayHints,
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
        val body = definition.body?.let { ctx.checkTerm(it, phase, ctx.freeze().evalTerm(type)) }
        with(meta) {
          resetUnsolvedMetas()
          val type = emptyEnv().zonkTerm(type)
          val body = body?.let { emptyEnv().zonkTerm(it) }
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
  ): Pair<C.Term, Value> {
    return elaborateTerm(term, phase, null)
  }

  private inline fun Ctx.checkTerm(
    term: R.Term,
    phase: Phase,
    type: Value,
  ): C.Term {
    return elaborateTerm(term, phase, type).first
  }

  private fun Ctx.elaborateTerm(
    term: R.Term,
    phase: Phase,
    type: Value?,
  ): Pair<C.Term, Value> {
    val type = type?.let { meta.forceValue(type) }
    return when {
      check<Value.Point>(type)                                    -> {
        val (synth, _) = synthTerm(term, phase)
        val env = freeze()
        val value = env.evalTerm(synth)
        if (with(meta) { next().unifyValue(value, type.element.value) }) {
          synth to type
        } else {
          invalidTerm(env.typeMismatch(type.element.value, value, term.range))
        }
      }
      term is R.Term.Tag && phase == Phase.CONST && synth(type)   -> C.Term.Tag to Value.Type.BYTE
      term is R.Term.TagOf && phase == Phase.CONST && synth(type) -> C.Term.TagOf(term.value) to Value.Tag
      term is R.Term.Type && synth(type)                          -> {
        val tag = checkTerm(term.element, Phase.CONST, Value.Tag)
        C.Term.Type(tag) to Value.Type.BYTE
      }
      term is R.Term.Bool && synth(type)                          -> C.Term.Bool to Value.Type.BYTE
      term is R.Term.BoolOf && synth(type)                        -> C.Term.BoolOf(term.value) to Value.Bool
      term is R.Term.If && match<Value>(type)                     -> {
        val condition = checkTerm(term.condition, phase, Value.Bool)
        val (thenBranch, thenBranchType) = elaborateTerm(term.thenBranch, phase, type)
        val (elseBranch, elseBranchType) = elaborateTerm(term.elseBranch, phase, type)
        val type = type ?: Value.Union(listOf(lazyOf(thenBranchType), lazyOf(elseBranchType)))
        C.Term.If(condition, thenBranch, elseBranch) to type
      }
      term is R.Term.Is && synth(type)                            -> {
        val (scrutineer, scrutineerType) = restoring { synthPattern(term.scrutineer, phase) }
        val scrutinee = checkTerm(term.scrutinee, phase, scrutineerType)
        C.Term.Is(scrutinee, scrutineer) to Value.Bool
      }
      term is R.Term.Byte && synth(type)                          -> C.Term.Byte to Value.Type.BYTE
      term is R.Term.ByteOf && synth(type)                        -> C.Term.ByteOf(term.value) to Value.Byte
      term is R.Term.Short && synth(type)                         -> C.Term.Short to Value.Type.SHORT
      term is R.Term.ShortOf && synth(type)                       -> C.Term.ShortOf(term.value) to Value.Short
      term is R.Term.Int && synth(type)                           -> C.Term.Int to Value.Type.INT
      term is R.Term.IntOf && synth(type)                         -> C.Term.IntOf(term.value) to Value.Int
      term is R.Term.Long && synth(type)                          -> C.Term.Long to Value.Type.LONG
      term is R.Term.LongOf && synth(type)                        -> C.Term.LongOf(term.value) to Value.Long
      term is R.Term.Float && synth(type)                         -> C.Term.Float to Value.Type.FLOAT
      term is R.Term.FloatOf && synth(type)                       -> C.Term.FloatOf(term.value) to Value.Float
      term is R.Term.Double && synth(type)                        -> C.Term.Double to Value.Type.DOUBLE
      term is R.Term.DoubleOf && synth(type)                                   -> C.Term.DoubleOf(term.value) to Value.Double
      term is R.Term.String && synth(type)                                     -> C.Term.String to Value.Type.STRING
      term is R.Term.StringOf && synth(type)                                   -> C.Term.StringOf(term.value) to Value.String
      term is R.Term.ByteArray && synth(type)                                  -> C.Term.ByteArray to Value.Type.BYTE_ARRAY
      term is R.Term.ByteArrayOf && synth(type)                   -> {
        val elements = term.elements.map { checkTerm(it, phase, Value.Byte) }
        C.Term.ByteArrayOf(elements) to Value.ByteArray
      }
      term is R.Term.IntArray && synth(type)                      -> C.Term.IntArray to Value.Type.INT_ARRAY
      term is R.Term.IntArrayOf && synth(type)                    -> {
        val elements = term.elements.map { checkTerm(it, phase, Value.Int) }
        C.Term.IntArrayOf(elements) to Value.IntArray
      }
      term is R.Term.LongArray && synth(type)                     -> C.Term.LongArray to Value.Type.LONG_ARRAY
      term is R.Term.LongArrayOf && synth(type)                   -> {
        val elements = term.elements.map { checkTerm(it, phase, Value.Long) }
        C.Term.LongArrayOf(elements) to Value.LongArray
      }
      term is R.Term.List && synth(type)                          -> {
        val element = checkTerm(term.element, phase, meta.freshType(term.element.range))
        C.Term.List(element) to Value.Type.LIST
      }
      term is R.Term.ListOf && match<Value.List>(type)            -> {
        val elementType = type?.element?.value
        val (elements, elementsTypes) = term.elements.map { elaborateTerm(it, phase, elementType) }.unzip()
        val type = type ?: Value.List(lazyOf(Value.Union(elementsTypes.map { lazyOf(it) })))
        C.Term.ListOf(elements) to type
      }
      term is R.Term.Compound && synth(type)                      -> {
        val elements = term.elements.associateTo(linkedMapOf()) { (key, element) ->
          val element = checkTerm(element, phase, meta.freshType(element.range))
          key.value to element
        }
        C.Term.Compound(elements) to Value.Type.COMPOUND
      }
      term is R.Term.CompoundOf && synth(type)                    -> {
        val elements = linkedMapOf<String, C.Term>()
        val elementsTypes = linkedMapOf<String, Lazy<Value>>()
        term.elements.forEach { (key, element) ->
          val (element, elementType) = synthTerm(element, phase)
          elements[key.value] = element
          elementsTypes[key.value] = lazyOf(elementType)
        }
        val type = Value.Compound(elementsTypes)
        C.Term.CompoundOf(elements) to type
      }
      term is R.Term.CompoundOf && check<Value.Compound>(type)    -> {
        TODO("implement")
      }
      term is R.Term.Point && match<Value.Type>(type)             -> { // TODO: unify tags
        val (element, elementType) = synthTerm(term.element, phase)
        val type = type ?: meta.freshType(term.range)
        C.Term.Point(element, next().quoteValue(elementType)) to type
      }
      term is R.Term.Union && match<Value.Type>(type)             -> {
        val type = type ?: meta.freshType(term.range)
        val elements = term.elements.map { checkTerm(it, phase, type) }
        C.Term.Union(elements) to type
      }
      term is R.Term.Func && synth(type)                          -> {
        val env = freeze()
        restoring {
          val params = term.params.map { (pattern, term) ->
            val term = checkTerm(term, phase, meta.freshType(term.range))
            val pattern = checkPattern(pattern, phase, env.evalTerm(term))
            pattern to term
          }
          val result = checkTerm(term.result, phase, meta.freshType(term.result.range))
          C.Term.Func(params, result) to Value.Type.COMPOUND
        }
      }
      term is R.Term.FuncOf && synth(type)                        -> {
        val next = next()
        restoring {
          val (params, paramsTypes) = term.params.map { synthPattern(it, phase) }.unzip()
          val (result, resultType) = synthTerm(term.result, phase)
          val env = freeze()
          val vParams = params.map { env.evalPattern(it) }
          val type = Value.Func(
            paramsTypes.map { lazyOf(it) },
            Closure(env, vParams, (next + next.collect(vParams).size).quoteValue(resultType)),
          )
          C.Term.FuncOf(params, result) to type
        }
      }
      term is R.Term.FuncOf && check<Value.Func>(type)            -> {
        val next = next()
        restoring {
          if (type.params.size == term.params.size) {
            val params = term.params.mapIndexed { index, param ->
              val actual = checkPattern(param, phase, type.params[index].value)
              val expected = type.result.binders[index]
              if (conv(expected, actual)) {
                actual
              } else {
                return invalidTerm(patternMismatch(expected, actual, param.range))
              }
            }
            val result = checkTerm(term.result, phase, next.evalClosure(type.result))
            C.Term.FuncOf(params, result) to type
          } else {
            invalidTerm(arityMismatch(type.params.size, term.params.size, term.range))
          }
        }
      }
      term is R.Term.Apply && synth(type)                                      -> {
        val (func, maybeFuncType) = synthTerm(term.func, phase)
        val funcType = when (val funcType = meta.forceValue(maybeFuncType)) {
          is Value.Func -> funcType
          else          -> return invalidTerm(cannotSynthesize(term.func.range)) // TODO: unify?
        }
        if (funcType.params.size != term.args.size) {
          return invalidTerm(arityMismatch(funcType.params.size, term.args.size, term.range))
        }
        val args = (term.args zip funcType.params).map { (arg, param) ->
          checkTerm(arg, phase, param.value)
        }
        val values = freeze()
        val type = funcType.result(args.map { lazy { values.evalTerm(it) } })
        C.Term.Apply(func, args, next().quoteValue(type)) to type
      }
      term is R.Term.Code && phase == Phase.CONST && synth(type)               -> {
        val element = checkTerm(term.element, Phase.WORLD, meta.freshType(term.element.range))
        C.Term.Code(element) to Value.Type.END
      }
      term is R.Term.CodeOf && phase == Phase.CONST && match<Value.Code>(type) -> {
        val (element, elementType) = elaborateTerm(term.element, Phase.WORLD, type?.element?.value)
        val type = type ?: Value.Code(lazyOf(elementType))
        C.Term.CodeOf(element) to type
      }
      term is R.Term.Splice && match<Value>(type)                              -> {
        val type = type ?: meta.freshValue(term.range)
        val element = checkTerm(term.element, Phase.CONST, Value.Code(lazyOf(type)))
        C.Term.Splice(element) to type
      }
      term is R.Term.Command && match<Value>(type)                             -> {
        val type = type ?: meta.freshValue(term.range)
        val element = checkTerm(term.element, Phase.CONST, Value.String)
        C.Term.Command(element, next().quoteValue(type)) to type
      }
      term is R.Term.Let && match<Value>(type)                                 -> {
        val (init, initType) = synthTerm(term.init, phase)
        restoring {
          val (binder, binderType) = synthPattern(term.binder, phase)
          if (!next().sub(initType, binderType)) {
            diagnostics += freeze().typeMismatch(initType, binderType, term.init.range)
          }
          val (body, bodyType) = elaborateTerm(term.body, phase, type)
          val type = type ?: bodyType
          C.Term.Let(binder, init, body) to type
        }
      }
      term is R.Term.Var && synth(type)                           -> {
        val entry = this[next().toLvl(term.idx)]
        val type = meta.forceValue(entry.type)
        when {
          entry.phase == phase                      -> {
            C.Term.Var(term.name, term.idx, next().quoteValue(type)) to type
          }
          entry.phase < phase                       -> {
            inlayHint(term.range.start, "`")
            C.Term.CodeOf(C.Term.Var(term.name, term.idx, next().quoteValue(type))) to Value.Code(lazyOf(type))
          }
          entry.phase > phase && type is Value.Code -> {
            inlayHint(term.range.start, "$")
            C.Term.Splice(C.Term.Var(term.name, term.idx, next().quoteValue(type))) to type.element.value
          }
          else                                      -> invalidTerm(phaseMismatch(phase, entry.phase, term.range))
        }
      }
      term is R.Term.Def && synth(type)                           -> {
        when (val definition = definitions[term.name]) {
          is C.Definition.Def -> {
            val actualPhase = getPhase(definition.modifiers)
            val type = freeze().evalTerm(definition.type)
            when {
              actualPhase == phase                      -> {
                C.Term.Def(term.name, definition.body, definition.type) to type
              }
              actualPhase < phase                       -> {
                inlayHint(term.range.start, "`")
                C.Term.CodeOf(C.Term.Def(term.name, definition.body, definition.type)) to Value.Code(lazyOf(type))
              }
              actualPhase > phase && type is Value.Code -> {
                inlayHint(term.range.start, "$")
                C.Term.Splice(C.Term.Def(term.name, definition.body, definition.type)) to type.element.value
              }
              else                                      -> invalidTerm(phaseMismatch(phase, actualPhase, term.range))
            }
          }
          else                -> invalidTerm(expectedDef(term.range))
        }
      }
      term is R.Term.As && synth(type)                            -> {
        val type = freeze().evalTerm(checkTerm(term.type, phase, meta.freshType(term.type.range)))
        checkTerm(term.element, phase, type) to type
      }
      term is R.Term.Hole && match<Value>(type)                   -> C.Term.Hole to Value.Hole
      synth(type)                                                 -> invalidTerm(cannotSynthesize(term.range))
      check<Value>(type)                                          -> {
        val (synth, synthType) = synthTerm(term, phase)
        if (next().sub(synthType, type)) {
          synth to type
        } else {
          invalidTerm(freeze().typeMismatch(type, synthType, term.range))
        }
      }
      else                                                        -> error("unreachable")
    }.also { (_, type) ->
      hover(type, term.range)
    }
  }

  private inline fun Ctx.synthPattern(
    pattern: R.Pattern,
    phase: Phase,
  ): Pair<C.Pattern<C.Term>, Value> {
    return elaboratePattern(pattern, phase, null)
  }

  private inline fun Ctx.checkPattern(
    pattern: R.Pattern,
    phase: Phase,
    type: Value,
  ): C.Pattern<C.Term> {
    return elaboratePattern(pattern, phase, type).first
  }

  private fun Ctx.elaboratePattern(
    pattern: R.Pattern,
    phase: Phase,
    type: Value?,
  ): Pair<C.Pattern<C.Term>, Value> {
    val type = type?.let { meta.forceValue(type) }
    return when {
      pattern is R.Pattern.IntOf && synth(type)                      -> C.Pattern.IntOf(pattern.value) to Value.Int
      pattern is R.Pattern.CompoundOf && synth(type)                 -> {
        val elements = linkedMapOf<String, C.Pattern<C.Term>>()
        val elementsTypes = linkedMapOf<String, Lazy<Value>>()
        pattern.elements.forEach { (key, element) ->
          val (element, elementType) = synthPattern(element, phase)
          elements[key.value] = element
          elementsTypes[key.value] = lazyOf(elementType)
        }
        val type = Value.Compound(elementsTypes)
        C.Pattern.CompoundOf(elements) to type
      }
      pattern is R.Pattern.CompoundOf && check<Value.Compound>(type) -> {
        TODO("implement")
      }
      pattern is R.Pattern.Var && match<Value>(type)                 -> {
        val next = next()
        val type = type ?: meta.freshValue(pattern.range)
        push(pattern.name, phase, type, null)
        C.Pattern.Var(pattern.name, next.quoteValue(type)) to type
      }
      pattern is R.Pattern.Drop && match<Value>(type)                -> {
        val type = type ?: meta.freshValue(pattern.range)
        C.Pattern.Drop(next().quoteValue(type)) to type
      }
      pattern is R.Pattern.As && synth(type)                         -> {
        val type = freeze().evalTerm(checkTerm(pattern.type, phase, meta.freshType(pattern.type.range)))
        checkPattern(pattern.element, phase, type) to type
      }
      pattern is R.Pattern.Hole && match<Value>(type)                -> C.Pattern.Hole to Value.Hole
      synth(type)                                                    -> invalidPattern(cannotSynthesize(pattern.range))
      check<Value>(type)                                             -> {
        val synth = synthPattern(pattern, phase)
        if (next().sub(synth.second, type)) {
          synth
        } else {
          invalidPattern(freeze().typeMismatch(type, synth.second, pattern.range))
        }
      }
      else                                                           -> error("unreachable")
    }.also { (_, type) ->
      hover(type, pattern.range)
    }
  }

  private fun Lvl.sub(
    value1: Value,
    value2: Value,
  ): Boolean {
    val value1 = meta.forceValue(value1)
    val value2 = meta.forceValue(value2)
    return when {
      value1 is Value.List && value2 is Value.List         -> sub(value1.element.value, value2.element.value)
      value1 is Value.Compound && value2 is Value.Compound -> {
        value1.elements.size == value2.elements.size &&
        value1.elements.all { (key1, element1) ->
          value2.elements[key1]?.let { element2 -> sub(element1.value, element2.value) } ?: false
        }
      }
      value1 is Value.Point && value2 !is Value.Point      -> sub(value1.elementType, value2)
      value1 is Value.Union                                -> value1.elements.all { sub(it.value, value2) }
      value2 is Value.Union                                -> {
        // TODO: implement subtyping with unification that has lower bound and upper bound
        if (value1 is Value.Meta && value2.elements.isEmpty()) {
          with(meta) { unifyValue(value1, value2) }
        } else {
          value2.elements.any { sub(value1, it.value) }
        }
      }
      value1 is Value.Func && value2 is Value.Func         -> {
        value1.params.size == value2.params.size &&
        (value1.params zip value2.params).all { (param1, param2) -> sub(param1.value, param2.value) } &&
        sub(evalClosure(value1.result), evalClosure(value2.result))
      }
      value1 is Value.Code && value2 is Value.Code         -> sub(value1.element.value, value2.element.value)
      else                                                 -> with(meta) { unifyValue(value1, value2) }
    }
  }

  // TODO: remove this and use term-context and type-context instead
  private fun conv(
    pattern1: C.Pattern<*>,
    pattern2: C.Pattern<*>,
  ): Boolean {
    return when {
      pattern1 is C.Pattern.IntOf && pattern2 is C.Pattern.IntOf           -> pattern1.value == pattern2.value
      pattern1 is C.Pattern.CompoundOf && pattern2 is C.Pattern.CompoundOf -> {
        pattern1.elements.size == pattern2.elements.size &&
        pattern1.elements.all { (key1, element1) ->
          pattern2.elements[key1]?.let { element2 -> conv(element1, element2) } ?: false
        }
      }
      pattern1 is C.Pattern.Var && pattern2 is C.Pattern.Var               -> pattern1.name == pattern2.name
      pattern1 is C.Pattern.Drop && pattern2 is C.Pattern.Drop             -> true
      else                                                                 -> false
    }
  }

  private fun getPhase(modifiers: Collection<Modifier>): Phase {
    return if (Modifier.CONST in modifiers) Phase.CONST else Phase.WORLD
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

  private fun Ctx.hover(
    type: Value,
    range: Range,
  ) {
    if (hover == null && instruction is Instruction.Hover && instruction.position in range) {
      val env = freeze()
      hover = { env.prettyValue(type) }
    }
  }

  private fun inlayHint(
    position: Position,
    label: String,
  ) {
    if (instruction is Instruction.InlayHint && position in instruction.range) {
      inlayHints += InlayHint(position, forLeft(label))
    }
  }

  private fun Env.typeMismatch(
    expected: Value,
    actual: Value,
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

  private fun Env.prettyValue(
    value: Value,
  ): String {
    return prettyTerm(with(meta) { zonkTerm(Lvl(size).quoteValue(value)) })
  }

  private fun invalidTerm(
    diagnostic: Diagnostic,
  ): Pair<C.Term, Value> {
    diagnostics += diagnostic
    return C.Term.Hole to Value.Hole
  }

  private fun invalidPattern(
    diagnostic: Diagnostic,
  ): Pair<C.Pattern<Nothing>, Value> {
    diagnostics += diagnostic
    return C.Pattern.Hole to Value.Hole
  }

  private class Ctx private constructor(
    private val _entries: MutableList<Entry>,
    private val _values: MutableList<Lazy<Value>>,
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
      type: Value,
      value: Lazy<Value>?,
    ) {
      _values += value ?: lazyOf(Value.Var(name, next(), type))
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
    val type: Value,
  )

  data class Result(
    val module: C.Module,
    val diagnostics: List<Diagnostic>,
    val hover: (() -> String)?,
    val inlayHints: List<InlayHint>,
  )

  companion object {
    private fun patternMismatch(
      expected: C.Pattern<*>,
      actual: C.Pattern<*>,
      range: Range,
    ): Diagnostic {
      val expected = prettyPattern(expected)
      val actual = prettyPattern(actual)
      return diagnostic(
        range,
        """pattern mismatch:
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
      instruction: Instruction?,
    ): Result =
      Elaborate(dependencies, input, instruction).elaborate()
  }
}
