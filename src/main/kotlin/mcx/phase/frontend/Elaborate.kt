package mcx.phase.frontend

import mcx.ast.*
import mcx.lsp.highlight
import mcx.phase.*
import mcx.phase.Normalize.evalType
import mcx.phase.frontend.Elaborate.Env.Companion.emptyEnv
import mcx.util.contains
import mcx.util.diagnostic
import mcx.util.rangeTo
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either.forRight
import kotlin.also
import mcx.ast.Core as C
import mcx.ast.Resolved as R

@Suppress("NAME_SHADOWING")
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
      is R.Definition.Function -> {
        val types = definition.typeParams.mapIndexed { level, typeParam -> typeParam to C.Type.Var(typeParam, level) }
        val env = emptyEnv(definitions, types, Modifier.LEAF in modifiers, Modifier.STATIC in modifiers)
        val binder = env.elaboratePattern(definition.binder)
        val result = env.elaborateType(definition.result)
        val body = if (signature) {
          null
        } else {
          definition.body?.let { env.elaborateTerm(it, result) }
        }
        C.Definition
          .Function(modifiers, name, definition.typeParams, binder, result, body)
          .also {
            hover(definition.name.range) { createFunctionDocumentation(it) }
          }
      }
      is R.Definition.Type     -> {
        val env = emptyEnv(definitions, emptyList(), Modifier.LEAF in modifiers, Modifier.STATIC in modifiers)
        val kind = elaborateKind(definition.kind)
        val body = env.elaborateType(definition.body, kind)
        C.Definition
          .Type(modifiers, name, body)
          .also {
            hover(definition.name.range) { createTypeDocumentation(it) }
          }
      }
      is R.Definition.Class    -> {
        val signatures = definition.signatures.mapNotNull { signature ->
          when (signature) {
            is R.Definition.Class.Signature.Function -> {
              val types = signature.typeParams.mapIndexed { level, typeParam -> typeParam to C.Type.Var(typeParam, level) }
              val env = emptyEnv(definitions, types, leaf = false, static = false)
              val binder = env.elaboratePattern(signature.binder)
              val result = env.elaborateType(signature.result)
              C.Definition.Class.Signature.Function(signature.name.value, signature.typeParams, binder, result)
            }
            is R.Definition.Class.Signature.Hole     -> null
          }
        }
        C.Definition.Class(modifiers, name, signatures)
      }
      is R.Definition.Test     -> {
        if (signature) {
          null
        } else {
          val env = emptyEnv(definitions, emptyList(), Modifier.LEAF in modifiers, Modifier.STATIC in modifiers)
          val body = env.elaborateTerm(definition.body, C.Type.Bool.SET)
          C.Definition
            .Test(modifiers, name, body)
            .also {
              hover(definition.name.range) { createTestDocumentation(it) }
            }
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
      type is R.Type.Ref && expected == null       -> C.Type.Ref(elaborateType(type.element, C.Kind.Type.ONE))
      type is R.Type.Tuple && expected == null     -> {
        val elements = type.elements.map { elaborateType(it) }
        C.Type.Tuple(elements, C.Kind.Type(elements.size))
      }
      type is R.Type.Union && expected == null     ->
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
      type is R.Type.Func && expected == null      -> C.Type.Func(elaborateType(type.param), elaborateType(type.result))
      type is R.Type.Clos && expected == null      -> C.Type.Clos(elaborateType(type.param), elaborateType(type.result))
      type is R.Type.Code && expected == null      -> {
        if (isMeta()) {
          C.Type.Code(elaborateType(type.element))
        } else {
          diagnostics += levelMismatch(type.range)
          C.Type.Hole
        }
      }
      type is R.Type.Var && expected == null       -> C.Type.Var(type.name, type.level)
      type is R.Type.Run && expected == null       -> {
        when (val definition = definitions[type.name]) {
          is C.Definition.Type -> C.Type.Run(type.name, definition.body, definition.body.kind)
          else                 -> {
            if (!signature) {
              diagnostics += expectedTypeDefinition(type.range)
            }
            val kind = metaEnv.freshKind()
            C.Type.Run(type.name, metaEnv.freshType(type.range, kind), kind)
          }
        }
      }
      type is R.Type.Meta                          -> {
        if (signature) {
          diagnostics += unexpectedMeta(type.range)
          C.Type.Hole
        } else {
          metaEnv.freshType(type.range, expected)
        }
      }
      type is R.Type.Hole                          -> C.Type.Hole
      expected == null                             -> error("kind must be non-null")
      else                                         -> {
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

  private fun Env.elaborateTerm(
    term: R.Term,
    expected: C.Type? = null,
  ): C.Term {
    val expected = expected?.let { metaEnv.forceType(it) }
    return when {
      term is R.Term.BoolOf &&
      expected == null              -> C.Term.BoolOf(term.value, C.Type.Bool(term.value))

      term is R.Term.ByteOf &&
      expected == null              -> C.Term.ByteOf(term.value, C.Type.Byte(term.value))

      term is R.Term.ShortOf &&
      expected == null              -> C.Term.ShortOf(term.value, C.Type.Short(term.value))

      term is R.Term.IntOf &&
      expected == null              -> C.Term.IntOf(term.value, C.Type.Int(term.value))

      term is R.Term.LongOf &&
      expected == null              -> C.Term.LongOf(term.value, C.Type.Long(term.value))

      term is R.Term.FloatOf &&
      expected == null              -> C.Term.FloatOf(term.value, C.Type.Float(term.value))

      term is R.Term.DoubleOf &&
      expected == null              -> C.Term.DoubleOf(term.value, C.Type.Double(term.value))

      term is R.Term.StringOf &&
      expected == null              -> {
        val head = term.parts.first()
        if (head is R.Term.StringOf.Part.Raw && term.parts.size == 1) {
          C.Term.StringOf(head.value, C.Type.String(head.value))
        } else if (isMeta()) {
          term.parts.fold<R.Term.StringOf.Part, C.Term>(C.Term.StringOf("", C.Type.String.SET)) { acc, part ->
            val part = when (part) {
              is R.Term.StringOf.Part.Raw         -> C.Term.StringOf(part.value, C.Type.String.SET)
              is R.Term.StringOf.Part.Interpolate -> elaborateTerm(part.element, C.Type.String.SET)
            }
            val type = C.Type.Tuple(listOf(C.Type.String.SET, C.Type.String.SET), C.Kind.Type(2))
            C.Term.Run(
              prelude / "++",
              emptyList(),
              C.Term.TupleOf(listOf(acc, part), type),
              C.Type.String.SET,
            )
          }
        } else {
          diagnostics += levelMismatch(term.range)
          C.Term.Hole(C.Type.String.SET)
        }
      }

      term is R.Term.ByteArrayOf &&
      expected is C.Type.ByteArray? -> {
        val elements = term.elements.map { element ->
          elaborateTerm(element, C.Type.Byte.SET)
        }
        C.Term.ByteArrayOf(elements, C.Type.ByteArray)
      }

      term is R.Term.IntArrayOf &&
      expected is C.Type.IntArray?  -> {
        val elements = term.elements.map { element ->
          elaborateTerm(element, C.Type.Int.SET)
        }
        C.Term.IntArrayOf(elements, C.Type.IntArray)
      }

      term is R.Term.LongArrayOf &&
      expected is C.Type.LongArray? -> {
        val elements = term.elements.map { element ->
          elaborateTerm(element, C.Type.Long.SET)
        }
        C.Term.LongArrayOf(elements, C.Type.LongArray)
      }

      term is R.Term.ListOf &&
      term.elements.isEmpty() &&
      expected is C.Type.List?      -> C.Term.ListOf(emptyList(), C.Type.List(C.Type.Union.END))

      term is R.Term.ListOf &&
      expected is C.Type.List?      -> {
        val head = elaborateTerm(term.elements.first(), expected?.element)
        val element = expected?.element ?: head.type
        val tail =
          term.elements
            .drop(1)
            .map { elaborateTerm(it, element) }
        C.Term.ListOf(listOf(head) + tail, C.Type.List(element))
      }

      term is R.Term.CompoundOf &&
      expected == null              -> {
        val elements = term.elements.associate { (key, element) ->
          val element = elaborateTerm(element)
          hoverType(key.range, element.type)
          key.value to element
        }
        C.Term.CompoundOf(elements, C.Type.Compound(elements.mapValues { it.value.type }))
      }

      term is R.Term.CompoundOf &&
      expected is C.Type.Compound   -> {
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
          .minus(
            term.elements
              .map { it.first.value }
              .toSet()
          )
          .forEach { diagnostics += keyNotFound(it, term.range) }
        C.Term.CompoundOf(elements, C.Type.Compound(elements.mapValues { it.value.type }))
      }

      term is R.Term.RefOf &&
      expected is C.Type.Ref? -> {
        val element = elaborateTerm(term.element, expected?.element)
        C.Term.RefOf(element, C.Type.Ref(element.type))
      }

      term is R.Term.TupleOf &&
      expected == null         -> {
        val elements = term.elements.map { element ->
          elaborateTerm(element)
        }
        C.Term.TupleOf(elements, C.Type.Tuple(elements.map { it.type }, C.Kind.Type(elements.size)))
      }

      term is R.Term.TupleOf &&
      expected is C.Type.Tuple -> {
        if (expected.elements.size != term.elements.size) {
          diagnostics += arityMismatch(expected.elements.size, term.elements.size, term.range) // TODO: use KindMismatch
        }
        val elements = term.elements.mapIndexed { index, element ->
          elaborateTerm(element, expected.elements.getOrNull(index))
        }
        C.Term.TupleOf(elements, C.Type.Tuple(elements.map { it.type }, C.Kind.Type(elements.size)))
      }

      term is R.Term.FuncOf &&
      expected is C.Type.Func? -> {
        val (binder, body) = restoring {
          val binder = elaboratePattern(term.binder, expected?.param)
          val body = elaborateTerm(term.body, expected?.result)
          binder to body
        }
        C.Term.FuncOf(binder, body, expected ?: C.Type.Func(binder.type, body.type))
      }

      term is R.Term.ClosOf &&
      expected is C.Type.Clos? -> {
        val (binder, body) = restoring {
          val binder = elaboratePattern(term.binder, expected?.param)
          val body = elaborateTerm(term.body, expected?.result)
          binder to body
        }
        C.Term.ClosOf(binder, body, expected ?: C.Type.Clos(binder.type, body.type))
      }

      term is R.Term.Apply     -> {
        val operator = elaborateTerm(term.operator)
        when (val operatorType = metaEnv.forceType(operator.type)) {
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

      term is R.Term.If        -> {
        val condition = elaborateTerm(term.condition, C.Type.Bool.SET)
        val elseEnv = copy()
        val thenClause = elaborateTerm(term.thenClause, expected)
        val elseClause = elseEnv.elaborateTerm(term.elseClause, expected ?: thenClause.type)
        C.Term.If(condition, thenClause, elseClause, thenClause.type)
      }

      term is R.Term.Let            -> {
        val init = elaborateTerm(term.init)
        val (binder, body) = restoring {
          val binder = elaboratePattern(term.binder)
          if (!(init.type isSubtypeOf binder.type)) {
            diagnostics += typeMismatch(binder.type, init.type, term.init.range)
          }
          val body = elaborateTerm(term.body, expected)
          binder to body
        }
        C.Term.Let(binder, init, body, body.type)
      }

      term is R.Term.Var &&
      expected == null              -> {
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
        C.Term.Var(term.name, term.level, entry.type)
      }

      term is R.Term.Run &&
      expected == null              -> {
        if (isLeaf()) {
          diagnostics += leafFunctionCannotRunOtherFunctions(term.range)
          C.Term.Hole(C.Type.Hole)
        } else {
          when (val definition = definitions[term.name.value]) {
            is C.Definition.Function -> {
              if (!isMeta() && Modifier.STATIC in definition.modifiers) {
                diagnostics += levelMismatch(term.name.range)
                C.Term.Hole(C.Type.Hole)
              } else {
                hover(term.name.range) { createFunctionDocumentation(definition) }
                val typeArgs =
                  if (definition.typeParams.isNotEmpty() && term.typeArgs.value.isEmpty()) {
                    definition.typeParams.map { metaEnv.freshType(term.typeArgs.range) }
                  } else {
                    if (definition.typeParams.size != term.typeArgs.value.size) {
                      diagnostics += arityMismatch(definition.typeParams.size, term.typeArgs.value.size, term.typeArgs.range.end..term.typeArgs.range.end)
                    }
                    term.typeArgs.value.map { elaborateType(it) }
                  }
                val typeEnv = Normalize.Env(definitions, typeArgs, false)
                val param = typeEnv.evalType(definition.binder.type)
                val arg = elaborateTerm(term.arg, param)
                val result = typeEnv.evalType(definition.result)
                C.Term.Run(definition.name, typeArgs, arg, result)
              }
            }
            else                     -> {
              diagnostics += expectedFunctionDefinition(term.name.range)
              C.Term.Hole(C.Type.Hole)
            }
          }
        }
      }

      term is R.Term.Is &&
      expected is C.Type.Bool?      -> {
        val scrutinee = elaborateTerm(term.scrutinee)
        val scrutineer = restoring {
          elaboratePattern(term.scrutineer, scrutinee.type)
        }
        C.Term.Is(scrutinee, scrutineer, C.Type.Bool.SET)
      }

      term is R.Term.CodeOf &&
      expected is C.Type.Code?      -> {
        if (isMeta()) {
          val element = quoting {
            elaborateTerm(term.element, expected?.element)
          }
          C.Term.CodeOf(element, C.Type.Code(element.type))
        } else {
          diagnostics += levelMismatch(term.range)
          C.Term.Hole(expected ?: C.Type.Hole)
        }
      }

      term is R.Term.Splice         -> {
        val element = splicing {
          elaborateTerm(term.element, expected?.let { C.Type.Code(it) })
        }
        when (val elementType = element.type) {
          is C.Type.Code -> C.Term.Splice(element, elementType.element)
          else           -> {
            diagnostics += typeMismatch(C.Type.Code(C.Type.Hole), elementType, term.element.range)
            C.Term.Hole(expected ?: C.Type.Hole)
          }
        }
      }

      term is R.Term.Hole           ->
        C.Term.Hole(expected ?: C.Type.Hole)

      expected == null              -> error("type must be non-null")

      else                          -> {
        val actual = elaborateTerm(term)
        if (!(actual.type isSubtypeOf expected)) {
          diagnostics += typeMismatch(expected, actual.type, term.range)
        }
        actual
      }
    }.also {
      hoverType(term.range, it.type)
      completionVars(term.range)
    }
  }

  private fun Env.elaboratePattern(
    pattern: R.Pattern,
    expected: C.Type? = null,
  ): C.Pattern {
    val expected = expected?.let { metaEnv.forceType(it) }
    return when {
      pattern is R.Pattern.IntOf &&
      expected is C.Type.Int?   -> C.Pattern.IntOf(pattern.value, C.Type.Int.SET)

      pattern is R.Pattern.IntRangeOf &&
      expected is C.Type.Int?     -> {
        if (pattern.min > pattern.max) {
          diagnostics += emptyRange(pattern.range)
        }
        C.Pattern.IntRangeOf(pattern.min, pattern.max, C.Type.Int.SET)
      }

      pattern is R.Pattern.ListOf &&
      pattern.elements.isEmpty() &&
      expected is C.Type.List?  -> C.Pattern.ListOf(emptyList(), C.Type.List(C.Type.Union.END))

      pattern is R.Pattern.ListOf &&
      expected is C.Type.List?    -> {
        val head = elaboratePattern(pattern.elements.first(), expected?.element)
        val element = expected?.element ?: head.type
        val tail =
          pattern.elements
            .drop(1)
            .map { elaboratePattern(it, element) }
        C.Pattern.ListOf(listOf(head) + tail, C.Type.List(element))
      }

      pattern is R.Pattern.CompoundOf &&
      expected == null            -> {
        val elements = pattern.elements.associate { (key, element) ->
          val element = elaboratePattern(element)
          hoverType(key.range, element.type)
          key.value to element
        }
        C.Pattern.CompoundOf(elements, C.Type.Compound(elements.mapValues { it.value.type }))
      }

      pattern is R.Pattern.CompoundOf &&
      expected is C.Type.Compound -> {
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
          .minus(
            pattern.elements
              .map { it.first.value }
              .toSet()
          )
          .forEach { diagnostics += keyNotFound(it, pattern.range) }
        C.Pattern.CompoundOf(elements, C.Type.Compound(elements.mapValues { it.value.type }))
      }

      pattern is R.Pattern.TupleOf &&
      expected is C.Type.Tuple    -> {
        if (expected.elements.size != pattern.elements.size) {
          diagnostics += arityMismatch(expected.elements.size, pattern.elements.size, pattern.range.end..pattern.range.end) // TODO: use KindMismatch
        }
        val elements = pattern.elements.mapIndexed { index, element ->
          elaboratePattern(element, expected.elements.getOrNull(index))
        }
        C.Pattern.TupleOf(elements, expected)
      }

      pattern is R.Pattern.TupleOf &&
      expected == null            -> {
        val elements = pattern.elements.map { element ->
          elaboratePattern(element)
        }
        C.Pattern.TupleOf(elements, C.Type.Tuple(elements.map { it.type }, C.Kind.Type(elements.size)))
      }

      pattern is R.Pattern.Var &&
      expected != null            -> {
        bind(pattern.name, expected)
        when (val kind = metaEnv.forceKind(expected.kind)) {
          is C.Kind.Type ->
            if (kind.arity == 1) {
              C.Pattern.Var(pattern.name, pattern.level, expected)
            } else {
              diagnostics += kindMismatch(C.Kind.Type.ONE, kind, pattern.range)
              C.Pattern.Hole(expected)
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

      pattern is R.Pattern.Var &&
      expected == null            -> {
        val type = metaEnv.freshType(pattern.range, C.Kind.Type(1))
        bind(pattern.name, type)
        C.Pattern.Var(pattern.name, pattern.level, type)
      }

      pattern is R.Pattern.Drop -> C.Pattern.Drop(expected ?: metaEnv.freshType(pattern.range))

      pattern is R.Pattern.Anno &&
      expected == null            -> {
        val type = elaborateType(pattern.type)
        elaboratePattern(pattern.element, type)
      }

      pattern is R.Pattern.Hole -> C.Pattern.Hole(expected ?: C.Type.Hole)

      expected == null            -> error("type must be non-null")

      else                      -> {
        val actual = elaboratePattern(pattern)
        if (!(actual.type isSubtypeOf expected)) {
          diagnostics += typeMismatch(expected, actual.type, pattern.range)
        }
        actual
      }
    }.also {
      hoverType(pattern.range, it.type)
    }
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
      type2 is C.Type.Compound -> type1.elements.size == type2.elements.size &&
                                   type1.elements.all { (key1, element1) ->
                                     when (val element2 = type2.elements[key1]) {
                                       null -> false
                                       else -> element1 isSubtypeOf element2
                                     }
                                   }

      type1 is C.Type.Ref &&
      type2 is C.Type.Ref      -> type1.element isSubtypeOf type2.element

      type1 is C.Type.Tuple &&
      type2 is C.Type.Tuple    -> type1.elements.size == type2.elements.size &&
                                  (type1.elements zip type2.elements).all { (element1, element2) -> element1 isSubtypeOf element2 }

      type1 is C.Type.Tuple &&
      type1.elements.size == 1 -> type1.elements.first() isSubtypeOf type2

      type2 is C.Type.Tuple &&
      type2.elements.size == 1 -> type1 isSubtypeOf type2.elements.first()

      type1 is C.Type.Func &&
      type2 is C.Type.Func     -> type2.param isSubtypeOf type1.param &&
                                  type1.result isSubtypeOf type2.result

      type1 is C.Type.Clos &&
      type2 is C.Type.Clos     -> type2.param isSubtypeOf type1.param &&
                                  type1.result isSubtypeOf type2.result

      type1 is C.Type.Union    -> type1.elements.all { it isSubtypeOf type2 }
      type2 is C.Type.Union    -> type2.elements.any { type1 isSubtypeOf it }

      type1 is C.Type.Code &&
      type2 is C.Type.Code     -> type1.element isSubtypeOf type2.element

      type1 is C.Type.Var &&
      type2 is C.Type.Var      -> type1.level == type2.level

      type1 is C.Type.Run &&
      type2 is C.Type.Run &&
      type1.name == type2.name -> true

      type1 is C.Type.Run &&
      (
          type1.name.module == input.module.name ||
          Modifier.INLINE in definitions[type1.name]!!.modifiers
      )                        -> Normalize
        .Env(definitions, emptyList(), true)
        .evalType(type1) isSubtypeOf type2

      type2 is C.Type.Run &&
      (
          type2.name.module == input.module.name ||
          Modifier.INLINE in definitions[type2.name]!!.modifiers
      )                         -> type1 isSubtypeOf Normalize
        .Env(definitions, emptyList(), true)
        .evalType(type2)

      type1 is C.Type.Hole      -> true
      type2 is C.Type.Hole      -> true

      else                      -> false
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
            is C.Definition.Function -> {
              documentation = forRight(highlight(createFunctionDocumentation(definition)))
              labelDetails.detail = ": ${prettyType(definition.binder.type)} -> ${prettyType(definition.result)}"
              CompletionItemKind.Function
            }
            is C.Definition.Type     -> {
              // TODO: add documentation and detail
              CompletionItemKind.Class
            }
            is C.Definition.Class    -> {
              // TODO: add documentation and detail
              CompletionItemKind.Interface
            }
            is C.Definition.Test     -> {
              return@mapNotNull null
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

  private fun createFunctionDocumentation(
    definition: C.Definition.Function,
  ): String {
    val name = definition.name.name
    val typeParams = if (definition.typeParams.isEmpty()) "" else definition.typeParams.joinToString(", ", "⟨", "⟩")
    val param = prettyPattern(definition.binder)
    val result = prettyType(metaEnv.zonkType(definition.result))
    return "function $name$typeParams $param → $result"
  }

  private fun createTypeDocumentation(
    definition: C.Definition.Type,
  ): String {
    val name = definition.name.name
    val kind = prettyKind(definition.body.kind)
    return "type $name: $kind"
  }

  private fun createTestDocumentation(
    definition: C.Definition.Test,
  ): String {
    val name = definition.name.name
    return "test $name"
  }

  private class Env private constructor(
    val definitions: Map<DefinitionLocation, C.Definition>,
    val types: List<Pair<String, C.Type>>,
    private val _entries: MutableList<Entry>,
    val leaf: Boolean,
    val static: Boolean,
  ) {
    val entries: List<Entry> get() = _entries
    private var savedSize: Int = 0
    var stage: Int = 0
      private set

    fun isLeaf(): Boolean =
      leaf

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
        types,
        _entries
          .map { it.copy() }
          .toMutableList(),
        leaf,
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
        types: List<Pair<String, C.Type>>,
        leaf: Boolean,
        static: Boolean,
      ): Env =
        Env(definitions, types, mutableListOf(), leaf, static)
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

  private fun leafFunctionCannotRunOtherFunctions(
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "leaf function cannot run other functions",
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
