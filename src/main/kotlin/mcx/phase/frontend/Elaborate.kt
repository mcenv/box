package mcx.phase.frontend

import mcx.ast.*
import mcx.lsp.highlight
import mcx.phase.*
import mcx.phase.frontend.Elaborate.Env.Companion.emptyEnv
import mcx.util.contains
import mcx.util.diagnostic
import mcx.util.rangeTo
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either.forRight
import kotlin.also
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import mcx.ast.Core as C
import mcx.ast.Resolved as R

@Suppress("NAME_SHADOWING", "NOTHING_TO_INLINE")
class Elaborate private constructor(
  dependencies: List<C.Module>,
  private val input: Resolve.Result,
  private val signature: Boolean,
  private val position: Position?,
) {
  private val metaEnv: MetaEnv = MetaEnv()
  private val diagnostics: MutableList<Diagnostic> = mutableListOf()
  private var varCompletionItems: MutableList<CompletionItem> = mutableListOf()
  private var definitionCompletionItems: MutableList<CompletionItem> = mutableListOf()
  private var hover: (() -> String)? = null
  private val definitions: Map<DefinitionLocation, C.Definition> =
    dependencies
      .flatMap { dependency -> dependency.definitions.map { it.name to it } }
      .toMap()

  private fun elaborate(): Result {
    return Result(
      elaborateModule(input.module),
      metaEnv,
      input.diagnostics + diagnostics,
      varCompletionItems + definitionCompletionItems,
      hover,
    )
  }

  private fun elaborateModule(
    module: R.Module,
  ): C.Module {
    completionDefinitions()
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
      is R.Definition.Def      -> {
        val env = emptyEnv(definitions, Modifier.STATIC in modifiers)
        val type = env.elaborateType(definition.type)
        val body = if (signature) {
          null
        } else {
          definition.body?.let { env.elaborateTerm(it, type) }
        }
        C.Definition.Def(modifiers, name, type, body)
          .also {
            hover(definition.name.range) { createDefDocumentation(it) }
          }
      }
      is R.Definition.Hole     -> null
    }
  }

  private fun elaborateKind(
    kind: R.Kind,
  ): C.Kind {
    return when (kind) {
      is R.Kind.Type -> C.Kind.Type(kind.arity)
      is R.Kind.Hole -> C.Kind.Hole
    }
  }

  private fun Env.elaborateType(
    type: R.Type,
    expected: C.Kind? = null,
  ): C.Type {
    val expected = expected?.let { metaEnv.forceKind(it) }
    return when {
      type is R.Type.Bool && expected == null      -> C.Type.Bool(type.value)
      type is R.Type.Byte && expected == null      -> C.Type.Byte(type.value)
      type is R.Type.Short && expected == null     -> C.Type.Short(type.value)
      type is R.Type.Int && expected == null       -> C.Type.Int(type.value)
      type is R.Type.Long && expected == null      -> C.Type.Long(type.value)
      type is R.Type.Float && expected == null     -> C.Type.Float(type.value)
      type is R.Type.Double && expected == null    -> C.Type.Double(type.value)
      type is R.Type.String && expected == null    -> C.Type.String(type.value)
      type is R.Type.ByteArray && expected == null -> C.Type.ByteArray
      type is R.Type.IntArray && expected == null  -> C.Type.IntArray
      type is R.Type.LongArray && expected == null -> C.Type.LongArray
      type is R.Type.List && expected == null      -> C.Type.List(elaborateType(type.element, C.Kind.Type.ONE))
      type is R.Type.Compound && expected == null  -> C.Type.Compound(type.elements.mapValues { elaborateType(it.value, C.Kind.Type.ONE) })
      type is R.Type.Tuple && expected == null     -> {
        val elements = type.elements.map { elaborateType(it) }
        C.Type.Tuple(elements, C.Kind.Type(elements.size))
      }
      type is R.Type.Union && expected == null ->
        if (type.elements.isEmpty()) {
          C.Type.Union.END
        } else {
          val head = elaborateType(type.elements.first())
          val tail =
            type.elements
              .drop(1)
              .map {
                val element = elaborateType(it, head.kind)
                if (element::class != head::class) {
                  diagnostics += typeMismatch(head, element, it.range)
                }
                element
              }
          C.Type.Union(listOf(head) + tail, head.kind)
        }
      type is R.Type.Func && expected == null  -> C.Type.Func(elaborateType(type.param), elaborateType(type.result))
      type is R.Type.Clos && expected == null  -> C.Type.Clos(elaborateType(type.param), elaborateType(type.result))
      type is R.Type.Code && expected == null  -> {
        if (isMeta()) {
          C.Type.Code(elaborateType(type.element))
        } else {
          diagnostics += levelMismatch(type.range)
          C.Type.Hole
        }
      }
      type is R.Type.Var && expected == null   -> C.Type.Var(type.name, type.level)
      type is R.Type.Def && expected == null   -> TODO()
      type is R.Type.Meta                      -> {
        if (signature) {
          diagnostics += unexpectedMeta(type.range)
          C.Type.Hole
        } else {
          metaEnv.freshType(type.range, expected)
        }
      }
      type is R.Type.Hole                      -> C.Type.Hole
      expected == null                         -> error("kind must be non-null")
      else                                     -> {
        val actual = elaborateType(type)
        if (!(actual.kind isSubkindOf expected)) {
          diagnostics += kindMismatch(expected, actual.kind, type.range)
        }
        actual
      }
    }.also {
      hoverKind(type.range, it.kind)
    }
  }

  @OptIn(ExperimentalContracts::class)
  private inline fun synthType(type: C.Type?): Boolean {
    contract {
      returns(false) implies (type != null)
      returns(true) implies (type == null)
    }
    return type == null
  }

  @OptIn(ExperimentalContracts::class)
  private inline fun <reified V : C.Type> checkType(type: C.Type?): Boolean {
    contract {
      returns(false) implies (type !is V)
      returns(true) implies (type is V)
    }
    return type is V
  }

  @OptIn(ExperimentalContracts::class)
  private inline fun <reified V : C.Type> matchType(type: C.Type?): Boolean {
    contract {
      returns(false) implies (type !is V?)
      returns(true) implies (type is V?)
    }
    return type is V?
  }

  // TODO: bidirectionalize stage
  private fun Env.elaborateTerm(
    term: R.Term,
    expected: C.Type? = null,
  ): C.Term {
    val expected = expected?.let { metaEnv.forceType(it) }
    return when {
      term is R.Term.BoolOf && synthType(expected)                        -> synthTermBoolOf(term)
      term is R.Term.ByteOf && synthType(expected)                        -> synthTermByteOf(term)
      term is R.Term.ShortOf && synthType(expected)                       -> synthTermShortOf(term)
      term is R.Term.IntOf && synthType(expected)                         -> synthTermIntOf(term)
      term is R.Term.LongOf && synthType(expected)                        -> synthTermLongOf(term)
      term is R.Term.FloatOf && synthType(expected)                       -> synthTermFloatOf(term)
      term is R.Term.DoubleOf && synthType(expected)                      -> synthTermDoubleOf(term)
      term is R.Term.StringOf && synthType(expected)                      -> synthTermStringOf(term)
      term is R.Term.ByteArrayOf && matchType<C.Type.ByteArray>(expected) -> matchTermByteArrayOf(term)
      term is R.Term.IntArrayOf && matchType<C.Type.IntArray>(expected)   -> matchTermIntArrayOf(term)
      term is R.Term.LongArrayOf && matchType<C.Type.LongArray>(expected) -> matchTermLongArrayOf(term)
      term is R.Term.ListOf && matchType<C.Type.List>(expected)           -> matchTermListOf(term, expected)
      term is R.Term.CompoundOf && synthType(expected)                    -> synthTermCompoundOf(term)
      term is R.Term.CompoundOf && checkType<C.Type.Compound>(expected)   -> checkTermCompoundOf(term, expected)
      term is R.Term.TupleOf && matchType<C.Type.Tuple>(expected)         -> matchTermTupleOf(term, expected)
      term is R.Term.FuncOf && matchType<C.Type.Func>(expected)           -> matchTermFuncOf(term, expected)
      term is R.Term.ClosOf && matchType<C.Type.Clos>(expected)           -> matchTermClosOf(term, expected)
      term is R.Term.Apply && matchType<C.Type>(expected)                 -> matchTermApply(term)
      term is R.Term.If && matchType<C.Type>(expected)                    -> matchTermIf(term, expected)
      term is R.Term.Let && matchType<C.Type>(expected)                   -> matchTermLet(term, expected)
      term is R.Term.Var && synthType(expected)                           -> synthTermVar(term)
      term is R.Term.Is && matchType<C.Type.Bool>(expected)               -> matchTermIs(term)
      term is R.Term.Command && matchType<C.Type.Code>(expected)          -> matchTermCommand(term, expected)
      term is R.Term.CodeOf && matchType<C.Type.Code>(expected)           -> matchTermCodeOf(term, expected)
      term is R.Term.Splice && matchType<C.Type>(expected)                -> matchTermSplice(term, expected)
      term is R.Term.Hole && matchType<C.Type>(expected)                  -> matchTermHole(expected)
      checkType<C.Type>(expected)                                         -> checkTermSub(term, expected)
      else                                                                -> error("unreachable")
    }.also {
      hoverType(term.range, it.type)
      completionVars(term.range)
    }
  }

  private inline fun synthTermBoolOf(term: R.Term.BoolOf): C.Term {
    return C.Term.BoolOf(term.value, C.Type.Bool(term.value))
  }

  private inline fun synthTermByteOf(term: R.Term.ByteOf): C.Term {
    return C.Term.ByteOf(term.value, C.Type.Byte(term.value))
  }

  private inline fun synthTermShortOf(term: R.Term.ShortOf): C.Term {
    return C.Term.ShortOf(term.value, C.Type.Short(term.value))
  }

  private inline fun synthTermIntOf(term: R.Term.IntOf): C.Term {
    return C.Term.IntOf(term.value, C.Type.Int(term.value))
  }

  private inline fun synthTermLongOf(term: R.Term.LongOf): C.Term {
    return C.Term.LongOf(term.value, C.Type.Long(term.value))
  }

  private inline fun synthTermFloatOf(term: R.Term.FloatOf): C.Term {
    return C.Term.FloatOf(term.value, C.Type.Float(term.value))
  }

  private inline fun synthTermDoubleOf(term: R.Term.DoubleOf): C.Term {
    return C.Term.DoubleOf(term.value, C.Type.Double(term.value))
  }

  private inline fun Env.synthTermStringOf(term: R.Term.StringOf): C.Term {
    val head = term.parts.first()
    return if (head is R.Term.StringOf.Part.Raw && term.parts.size == 1) {
      C.Term.StringOf(head.value, C.Type.String(head.value))
    } else if (isMeta()) {
      term.parts.fold<R.Term.StringOf.Part, C.Term>(C.Term.StringOf("", C.Type.String.SET)) { acc, part ->
        val part = when (part) {
          is R.Term.StringOf.Part.Raw         -> C.Term.StringOf(part.value, C.Type.String.SET)
          is R.Term.StringOf.Part.Interpolate -> elaborateTerm(part.element, C.Type.String.SET)
        }
        val type = C.Type.Tuple(listOf(C.Type.String.SET, C.Type.String.SET), C.Kind.Type(2))
        TODO()
        /* C.Term.Run(
          prelude / "++",
          emptyList(),
          C.Term.TupleOf(listOf(acc, part), type),
          C.Type.String.SET,
        ) */
      }
    } else {
      diagnostics += levelMismatch(term.range)
      C.Term.Hole(C.Type.String.SET)
    }
  }

  private inline fun Env.matchTermByteArrayOf(term: R.Term.ByteArrayOf): C.Term {
    val elements = term.elements.map { element ->
      elaborateTerm(element, C.Type.Byte.SET)
    }
    return C.Term.ByteArrayOf(elements, C.Type.ByteArray)
  }

  private inline fun Env.matchTermIntArrayOf(term: R.Term.IntArrayOf): C.Term {
    val elements = term.elements.map { element ->
      elaborateTerm(element, C.Type.Int.SET)
    }
    return C.Term.IntArrayOf(elements, C.Type.IntArray)
  }

  private inline fun Env.matchTermLongArrayOf(term: R.Term.LongArrayOf): C.Term {
    val elements = term.elements.map { element ->
      elaborateTerm(element, C.Type.Long.SET)
    }
    return C.Term.LongArrayOf(elements, C.Type.LongArray)
  }

  private inline fun Env.matchTermListOf(
    term: R.Term.ListOf,
    expected: C.Type.List?,
  ): C.Term {
    return if (term.elements.isEmpty()) {
      C.Term.ListOf(emptyList(), C.Type.List(C.Type.Union.END))
    } else {
      val head = elaborateTerm(term.elements.first(), expected?.element)
      val element = expected?.element ?: head.type
      val tail = term.elements.drop(1).map { elaborateTerm(it, element) }
      C.Term.ListOf(listOf(head) + tail, C.Type.List(element))
    }
  }

  private inline fun Env.synthTermCompoundOf(term: R.Term.CompoundOf): C.Term {
    val elements = term.elements.associate { (key, element) ->
      val element = elaborateTerm(element)
      hoverType(key.range, element.type)
      key.value to element
    }
    return C.Term.CompoundOf(elements, C.Type.Compound(elements.mapValues { it.value.type }))
  }

  private inline fun Env.checkTermCompoundOf(
    term: R.Term.CompoundOf,
    expected: C.Type.Compound,
  ): C.Term {
    val elements = mutableMapOf<String, C.Term>()
    term.elements.forEach { (key, element) ->
      when (val type = expected.elements[key.value]) {
        null -> {
          diagnostics += extraKey(key.value, key.range)
          val element = elaborateTerm(element)
          hoverType(key.range, element.type)
          elements[key.value] = element
        }
        else -> {
          hoverType(key.range, type)
          elements[key.value] = elaborateTerm(element, type)
        }
      }
    }
    expected.elements.keys
      .minus(term.elements.map { it.first.value }.toSet())
      .forEach { diagnostics += keyNotFound(it, term.range) }
    return C.Term.CompoundOf(elements, C.Type.Compound(elements.mapValues { it.value.type }))
  }

  private inline fun Env.matchTermTupleOf(
    term: R.Term.TupleOf,
    expected: C.Type.Tuple?,
  ): C.Term {
    if (expected != null && expected.elements.size != term.elements.size) {
      diagnostics += arityMismatch(expected.elements.size, term.elements.size, term.range) // TODO: use KindMismatch
    }
    val elements = term.elements.mapIndexed { index, element ->
      elaborateTerm(element, expected?.elements?.getOrNull(index))
    }
    return C.Term.TupleOf(elements, C.Type.Tuple(elements.map { it.type }, C.Kind.Type(elements.size)))
  }

  private inline fun Env.matchTermFuncOf(
    term: R.Term.FuncOf,
    expected: C.Type.Func?,
  ): C.Term {
    val (binder, body) = restoring {
      val binder = elaboratePattern(term.binder, expected?.param)
      val body = elaborateTerm(term.body, expected?.result)
      binder to body
    }
    return C.Term.FuncOf(binder, body, expected ?: C.Type.Func(binder.type, body.type))
  }

  private inline fun Env.matchTermClosOf(
    term: R.Term.ClosOf,
    expected: C.Type.Clos?,
  ): C.Term {
    val (binder, body) = restoring {
      val binder = elaboratePattern(term.binder, expected?.param)
      val body = elaborateTerm(term.body, expected?.result)
      binder to body
    }
    return C.Term.ClosOf(binder, body, expected ?: C.Type.Clos(binder.type, body.type))
  }

  // TODO: split
  private inline fun Env.matchTermApply(term: R.Term.Apply): C.Term {
    val operator = elaborateTerm(term.operator)
    return when (val operatorType = metaEnv.forceType(operator.type)) {
      is C.Type.Func -> {
        val operand = elaborateTerm(term.operand, operatorType.param)
        C.Term.Apply(operator, operand, operatorType.result)
      }
      is C.Type.Clos -> {
        val operand = elaborateTerm(term.operand, operatorType.param)
        C.Term.Apply(operator, operand, operatorType.result)
      }
      else           -> {
        diagnostics += typeMismatch(C.Type.Clos(C.Type.Hole, C.Type.Hole), operatorType, term.operator.range)
        C.Term.Hole(C.Type.Hole)
      }
    }
  }

  private inline fun Env.matchTermIf(
    term: R.Term.If,
    expected: C.Type?,
  ): C.Term {
    val condition = elaborateTerm(term.condition, C.Type.Bool.SET)
    val elseEnv = copy()
    val thenClause = elaborateTerm(term.thenClause, expected)
    val elseClause = elseEnv.elaborateTerm(term.elseClause, expected ?: thenClause.type)
    return C.Term.If(condition, thenClause, elseClause, thenClause.type)
  }

  private inline fun Env.matchTermLet(
    term: R.Term.Let,
    expected: C.Type?,
  ): C.Term {
    val init = elaborateTerm(term.init)
    val (binder, body) = restoring {
      val binder = elaboratePattern(term.binder)
      if (!(init.type isSubtypeOf binder.type)) {
        diagnostics += typeMismatch(binder.type, init.type, term.init.range)
      }
      val body = elaborateTerm(term.body, expected)
      binder to body
    }
    return C.Term.Let(binder, init, body, body.type)
  }

  private inline fun Env.synthTermVar(term: R.Term.Var): C.Term {
    val entry = entries[term.level]
    if (entry.used) {
      diagnostics += varAlreadyUsed(term.name, term.range)
    }
    if (stage != entry.stage) {
      diagnostics += stageMismatch(stage, entry.stage, term.range)
    }
    if (!entry.type.isValueType()) {
      entry.used = true
    }
    return C.Term.Var(term.name, term.level, entry.type)
  }

  private inline fun Env.matchTermIs(term: R.Term.Is): C.Term {
    val scrutinee = elaborateTerm(term.scrutinee)
    val scrutineer = restoring {
      elaboratePattern(term.scrutineer, scrutinee.type)
    }
    return C.Term.Is(scrutinee, scrutineer, C.Type.Bool.SET)
  }

  private inline fun Env.matchTermCommand(
    term: R.Term.Command,
    expected: C.Type.Code?,
  ): C.Term {
    return if (isMeta()) {
      val element = elaborateTerm(term.element, C.Type.String.SET)
      C.Term.Command(element, expected ?: C.Type.Code(metaEnv.freshType(term.range, C.Kind.Type.ONE)))
    } else {
      diagnostics += levelMismatch(term.range)
      C.Term.Hole(expected ?: C.Type.Hole)
    }
  }

  private inline fun Env.matchTermCodeOf(
    term: R.Term.CodeOf,
    expected: C.Type.Code?,
  ): C.Term {
    return if (isMeta()) {
      val element = quoting {
        elaborateTerm(term.element, expected?.element)
      }
      C.Term.CodeOf(element, C.Type.Code(element.type))
    } else {
      diagnostics += levelMismatch(term.range)
      C.Term.Hole(expected ?: C.Type.Hole)
    }
  }

  private inline fun Env.matchTermSplice(
    term: R.Term.Splice,
    expected: C.Type?,
  ): C.Term {
    val element = splicing {
      elaborateTerm(term.element, expected?.let { C.Type.Code(it) })
    }
    return when (val elementType = element.type) {
      is C.Type.Code -> C.Term.Splice(element, elementType.element)
      else           -> {
        diagnostics += typeMismatch(C.Type.Code(C.Type.Hole), elementType, term.element.range)
        C.Term.Hole(expected ?: C.Type.Hole)
      }
    }
  }

  private inline fun matchTermHole(expected: C.Type?): C.Term {
    return C.Term.Hole(expected ?: C.Type.Hole)
  }

  private inline fun Env.checkTermSub(
    term: R.Term,
    expected: C.Type,
  ): C.Term {
    val actual = elaborateTerm(term)
    if (!(actual.type isSubtypeOf expected)) {
      diagnostics += typeMismatch(expected, actual.type, term.range)
    }
    return actual
  }

  private fun Env.elaboratePattern(
    pattern: R.Pattern,
    expected: C.Type? = null,
  ): C.Pattern {
    val expected = expected?.let { metaEnv.forceType(it) }
    return when {
      pattern is R.Pattern.IntOf && matchType<C.Type.Int>(expected)           -> matchPatternIntOf(pattern)
      pattern is R.Pattern.IntRangeOf && matchType<C.Type.Int>(expected)      -> matchPatternIntRangeOf(pattern)
      pattern is R.Pattern.ListOf && matchType<C.Type.List>(expected)         -> matchPatternListOf(pattern, expected)
      pattern is R.Pattern.CompoundOf && synthType(expected)                  -> synthPatternCompoundOf(pattern)
      pattern is R.Pattern.CompoundOf && checkType<C.Type.Compound>(expected) -> checkPatternCompoundOf(pattern, expected)
      pattern is R.Pattern.TupleOf && matchType<C.Type.Tuple>(expected)       -> matchPatternTupleOf(pattern, expected)
      pattern is R.Pattern.Var && synthType(expected)                         -> synthPatternVar(pattern)
      pattern is R.Pattern.Var && checkType<C.Type>(expected)                 -> checkPatternVar(pattern, expected)
      pattern is R.Pattern.Drop && matchType<C.Type>(expected)                -> matchPatternDrop(pattern, expected)
      pattern is R.Pattern.Anno && synthType(expected)                        -> synthPatternAnno(pattern)
      pattern is R.Pattern.Hole && matchType<C.Type>(expected)                -> matchPatternHole(expected)
      checkType<C.Type>(expected)                                             -> checkPatternSub(pattern, expected)
      else                                                                    -> error("unreachable")
    }.also {
      hoverType(pattern.range, it.type)
    }
  }

  private inline fun matchPatternIntOf(pattern: R.Pattern.IntOf): C.Pattern {
    return C.Pattern.IntOf(pattern.value, C.Type.Int.SET)
  }

  private inline fun matchPatternIntRangeOf(pattern: R.Pattern.IntRangeOf): C.Pattern {
    if (pattern.min > pattern.max) {
      diagnostics += emptyRange(pattern.range)
    }
    return C.Pattern.IntRangeOf(pattern.min, pattern.max, C.Type.Int.SET)
  }

  private inline fun Env.matchPatternListOf(
    pattern: R.Pattern.ListOf,
    expected: C.Type.List?,
  ): C.Pattern {
    return if (pattern.elements.isEmpty()) {
      C.Pattern.ListOf(emptyList(), C.Type.List(C.Type.Union.END))
    } else {
      val head = elaboratePattern(pattern.elements.first(), expected?.element)
      val element = expected?.element ?: head.type
      val tail = pattern.elements.drop(1).map { elaboratePattern(it, element) }
      C.Pattern.ListOf(listOf(head) + tail, C.Type.List(element))
    }
  }

  private inline fun Env.synthPatternCompoundOf(pattern: R.Pattern.CompoundOf): C.Pattern {
    val elements = pattern.elements.associate { (key, element) ->
      val element = elaboratePattern(element)
      hoverType(key.range, element.type)
      key.value to element
    }
    return C.Pattern.CompoundOf(elements, C.Type.Compound(elements.mapValues { it.value.type }))
  }

  private inline fun Env.checkPatternCompoundOf(
    pattern: R.Pattern.CompoundOf,
    expected: C.Type.Compound,
  ): C.Pattern {
    val elements = mutableMapOf<String, C.Pattern>()
    pattern.elements.forEach { (key, element) ->
      when (val type = expected.elements[key.value]) {
        null -> {
          diagnostics += extraKey(key.value, key.range)
          val element = elaboratePattern(element)
          hoverType(key.range, element.type)
          elements[key.value] = element
        }
        else -> {
          hoverType(key.range, type)
          elements[key.value] = elaboratePattern(element, type)
        }
      }
    }
    expected.elements.keys
      .minus(pattern.elements.map { it.first.value }.toSet())
      .forEach { diagnostics += keyNotFound(it, pattern.range) }
    return C.Pattern.CompoundOf(elements, C.Type.Compound(elements.mapValues { it.value.type }))
  }

  private inline fun Env.matchPatternTupleOf(
    pattern: R.Pattern.TupleOf,
    expected: C.Type.Tuple?,
  ): C.Pattern {
    if (expected != null && expected.elements.size != pattern.elements.size) {
      diagnostics += arityMismatch(expected.elements.size, pattern.elements.size, pattern.range.end..pattern.range.end) // TODO: use KindMismatch
    }
    val elements = pattern.elements.mapIndexed { index, element ->
      elaboratePattern(element, expected?.elements?.getOrNull(index))
    }
    return C.Pattern.TupleOf(elements, expected ?: C.Type.Tuple(elements.map { it.type }, C.Kind.Type(elements.size)))
  }

  private inline fun Env.synthPatternVar(pattern: R.Pattern.Var): C.Pattern {
    val type = metaEnv.freshType(pattern.range, metaEnv.freshKind())
    bind(pattern.name, type)
    return C.Pattern.Var(pattern.name, pattern.level, type)
  }

  private inline fun Env.checkPatternVar(
    pattern: R.Pattern.Var,
    expected: C.Type,
  ): C.Pattern {
    bind(pattern.name, expected)
    return when (val kind = metaEnv.forceKind(expected.kind)) {
      is C.Kind.Type -> {
        C.Pattern.Var(pattern.name, pattern.level, expected)
      }
      is C.Kind.Meta -> {
        metaEnv.unifyKinds(kind, C.Kind.Type.ONE)
        C.Pattern.Var(pattern.name, pattern.level, expected)
      }
      else           -> {
        diagnostics += kindMismatch(C.Kind.Type.ONE, kind, pattern.range)
        C.Pattern.Hole(expected)
      }
    }
  }

  private inline fun matchPatternDrop(
    pattern: R.Pattern.Drop,
    expected: C.Type?,
  ): C.Pattern {
    return C.Pattern.Drop(expected ?: metaEnv.freshType(pattern.range))
  }

  private inline fun Env.synthPatternAnno(pattern: R.Pattern.Anno): C.Pattern {
    val type = elaborateType(pattern.type)
    return elaboratePattern(pattern.element, type)
  }

  private inline fun matchPatternHole(expected: C.Type?): C.Pattern {
    return C.Pattern.Hole(expected ?: C.Type.Hole)
  }

  private inline fun Env.checkPatternSub(
    pattern: R.Pattern,
    expected: C.Type,
  ): C.Pattern {
    val actual = elaboratePattern(pattern)
    if (!(actual.type isSubtypeOf expected)) {
      diagnostics += typeMismatch(expected, actual.type, pattern.range)
    }
    return actual
  }

  private infix fun C.Kind.isSubkindOf(
    kind2: C.Kind,
  ): Boolean {
    val kind1 = this
    return when {
      kind1 is C.Kind.Meta -> metaEnv.unifyKinds(kind1, kind2)
      kind2 is C.Kind.Meta -> metaEnv.unifyKinds(kind1, kind2)

      kind1 is C.Kind.Type &&
      kind2 is C.Kind.Type -> kind1.arity == kind2.arity

      kind1 is C.Kind.Hole -> true
      kind2 is C.Kind.Hole -> true

      else                 -> false
    }
  }

  private infix fun C.Type.isSubtypeOf(
    type2: C.Type,
  ): Boolean {
    val type1 = metaEnv.forceType(this)
    val type2 = metaEnv.forceType(type2)
    return when {
      type1 is C.Type.Meta      -> metaEnv.unifyTypes(type1, type2)
      type2 is C.Type.Meta      -> metaEnv.unifyTypes(type1, type2)

      type1 is C.Type.Bool &&
      type2 is C.Type.Bool      -> type2.value == null || type1.value == type2.value

      type1 is C.Type.Byte &&
      type2 is C.Type.Byte      -> type2.value == null || type1.value == type2.value

      type1 is C.Type.Short &&
      type2 is C.Type.Short     -> type2.value == null || type1.value == type2.value

      type1 is C.Type.Int &&
      type2 is C.Type.Int       -> type2.value == null || type1.value == type2.value

      type1 is C.Type.Long &&
      type2 is C.Type.Long      -> type2.value == null || type1.value == type2.value

      type1 is C.Type.Float &&
      type2 is C.Type.Float     -> type2.value == null || type1.value == type2.value

      type1 is C.Type.Double &&
      type2 is C.Type.Double    -> type2.value == null || type1.value == type2.value

      type1 is C.Type.String &&
      type2 is C.Type.String    -> type2.value == null || type1.value == type2.value

      type1 is C.Type.ByteArray &&
      type2 is C.Type.ByteArray -> true

      type1 is C.Type.IntArray &&
      type2 is C.Type.IntArray  -> true

      type1 is C.Type.LongArray &&
      type2 is C.Type.LongArray -> true

      type1 is C.Type.List &&
      type2 is C.Type.List      -> type1.element isSubtypeOf type2.element

      type1 is C.Type.Compound &&
      type2 is C.Type.Compound        -> type1.elements.size == type2.elements.size &&
                                         type1.elements.all { (key1, element1) ->
                                           when (val element2 = type2.elements[key1]) {
                                             null -> false
                                             else -> element1 isSubtypeOf element2
                                           }
                                         }

      type1 is C.Type.Tuple &&
      type2 is C.Type.Tuple           -> type1.elements.size == type2.elements.size &&
                                         (type1.elements zip type2.elements).all { (element1, element2) -> element1 isSubtypeOf element2 }

      type1 is C.Type.Tuple &&
      type1.elements.size == 1        -> type1.elements.first() isSubtypeOf type2

      type2 is C.Type.Tuple &&
      type2.elements.size == 1        -> type1 isSubtypeOf type2.elements.first()

      type1 is C.Type.Func &&
      type2 is C.Type.Func            -> type2.param isSubtypeOf type1.param &&
                                         type1.result isSubtypeOf type2.result

      type1 is C.Type.Clos &&
      type2 is C.Type.Clos            -> type2.param isSubtypeOf type1.param &&
                                         type1.result isSubtypeOf type2.result

      type1 is C.Type.Union           -> type1.elements.all { it isSubtypeOf type2 }
      type2 is C.Type.Union           -> type2.elements.any { type1 isSubtypeOf it }

      type1 is C.Type.Code &&
      type2 is C.Type.Code            -> type1.element isSubtypeOf type2.element

      type1 is C.Type.Var &&
      type2 is C.Type.Var             -> type1.level == type2.level

      type1 is C.Type.Def &&
      type2 is C.Type.Def &&
      type1.name == type2.name        -> true

      type1 is C.Type.Hole            -> true
      type2 is C.Type.Hole            -> true

      else                            -> false
    }
  }

  private fun C.Type.isValueType(): Boolean {
    return when (metaEnv.forceType(this)) {
      is C.Type.Bool   -> true
      is C.Type.Byte   -> true
      is C.Type.Short  -> true
      is C.Type.Int    -> true
      is C.Type.Long   -> true
      is C.Type.Float  -> true
      is C.Type.Double -> true
      is C.Type.String -> true
      is C.Type.Func   -> true
      is C.Type.Code   -> true
      else             -> false
    }
  }

  private fun hoverKind(
    range: Range,
    kind: C.Kind,
  ) {
    hover(range) { prettyKind(kind) }
  }

  private fun hoverType(
    range: Range,
    type: C.Type,
  ) {
    hover(range) { prettyType(metaEnv.zonkType(type)) }
  }

  private fun hover(
    range: Range,
    string: () -> String,
  ) {
    if (hover == null && position != null && position in range) {
      hover = string
    }
  }

  private fun completionDefinitions() {
    if (position != null) {
      definitionCompletionItems += definitions.mapNotNull { (location, definition) ->
        CompletionItem(location.name).apply {
          val labelDetails = CompletionItemLabelDetails()
          kind = when (definition) {
            is C.Definition.Def -> {
              documentation = forRight(highlight(createDefDocumentation(definition)))
              labelDetails.detail = ": ${prettyType(definition.type)}"
              CompletionItemKind.Function
            }
          }
          labelDetails.description = location.module.toString()
          this.labelDetails = labelDetails
        }
      }
    }
  }

  private fun Env.completionVars(
    range: Range,
  ) {
    if (position != null && position in range) {
      if (varCompletionItems.isEmpty()) {
        varCompletionItems +=
          entries
            .filter { entry -> !entry.used }
            .map { entry ->
              CompletionItem(entry.name).apply {
                val type = prettyType(entry.type)
                documentation = forRight(highlight(type))
                labelDetails = CompletionItemLabelDetails().apply {
                  detail = ": $type"
                }
                kind = CompletionItemKind.Variable
              }
            }
      }
    }
  }

  private fun createDefDocumentation(
    definition: C.Definition.Def,
  ): String {
    val name = definition.name.name
    val type = prettyType(metaEnv.zonkType(definition.type))
    return "def $name: $type"
  }

  private class Env private constructor(
    val definitions: Map<DefinitionLocation, C.Definition>,
    private val _entries: MutableList<Entry>,
    val static: Boolean,
  ) {
    val entries: List<Entry> get() = _entries
    private var savedSize: Int = 0
    var stage: Int = 0
      private set

    fun isMeta(): Boolean =
      static || stage > 0

    fun bind(
      name: String,
      type: C.Type,
    ) {
      _entries += Entry(name, false, stage, type)
    }

    inline fun <R> restoring(
      action: Env.() -> R,
    ): R {
      savedSize = _entries.size
      val result = this.action()
      repeat(_entries.size - savedSize) {
        _entries.removeLast()
      }
      return result
    }

    inline fun <R> quoting(
      action: () -> R,
    ): R {
      --stage
      val result = action()
      ++stage
      return result
    }

    inline fun <R> splicing(
      action: () -> R,
    ): R {
      ++stage
      val result = action()
      --stage
      return result
    }

    fun copy(): Env =
      Env(
        definitions,
        _entries.map { it.copy() }.toMutableList(),
        static,
      )

    data class Entry(
      val name: String,
      var used: Boolean,
      val stage: Int,
      val type: C.Type,
    )

    companion object {
      fun emptyEnv(
        definitions: Map<DefinitionLocation, C.Definition>,
        static: Boolean,
      ): Env =
        Env(definitions, mutableListOf(), static)
    }
  }

  private fun typeMismatch(
    expected: C.Type,
    actual: C.Type,
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      """type mismatch:
        |  expected: ${prettyType(expected)}
        |  actual  : ${prettyType(actual)}
      """.trimMargin(),
      DiagnosticSeverity.Error,
    )
  }

  private fun levelMismatch(
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "level mismatch",
      DiagnosticSeverity.Error,
    )
  }

  private fun expectedTypeDefinition(
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "expected: type definition",
      DiagnosticSeverity.Error,
    )
  }

  private fun unexpectedMeta(
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "unexpected meta",
      DiagnosticSeverity.Error,
    )
  }

  private fun kindMismatch(
    expected: C.Kind,
    actual: C.Kind,
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      """kind mismatch:
      |  expected: ${prettyKind(expected)}
      |  actual  : ${prettyKind(actual)}
    """.trimMargin(),
      DiagnosticSeverity.Error,
    )
  }

  private fun extraKey(
    key: String,
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "extra key: '$key'",
      DiagnosticSeverity.Error,
    )
  }

  private fun keyNotFound(
    key: String,
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "key not found: '$key'",
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

  private fun varAlreadyUsed(
    name: String,
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "variable already used: '$name'",
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
      """.trimMargin(),
      DiagnosticSeverity.Error,
    )
  }

  private fun expectedFunctionDefinition(
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "expected: function definition",
      DiagnosticSeverity.Error,
    )
  }

  private fun emptyRange(
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "empty range",
      DiagnosticSeverity.Error,
    )
  }

  data class Result(
    val module: C.Module,
    val metaEnv: MetaEnv,
    val diagnostics: List<Diagnostic>,
    val completionItems: List<CompletionItem>,
    val hover: (() -> String)?,
  )

  companion object {
    operator fun invoke(
      context: Context,
      dependencies: List<C.Module>,
      input: Resolve.Result,
      signature: Boolean,
      position: Position? = null,
    ): Result =
      Elaborate(dependencies, input, signature, position).elaborate()
  }
}
