package mcx.phase.frontend

import mcx.ast.*
import mcx.ast.Annotation
import mcx.lsp.highlight
import mcx.phase.Context
import mcx.phase.Normalize.evalType
import mcx.phase.frontend.Elaborate.Env.Companion.emptyEnv
import mcx.phase.prettyPattern
import mcx.phase.prettyType
import mcx.util.Ranged
import mcx.util.contains
import mcx.util.rangeTo
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either.forRight
import kotlin.Boolean
import kotlin.Int
import kotlin.Pair
import kotlin.String
import kotlin.Suppress
import kotlin.also
import kotlin.apply
import kotlin.error
import kotlin.let
import kotlin.repeat
import kotlin.to
import mcx.ast.Core as C
import mcx.ast.Resolved as R

@Suppress("NAME_SHADOWING")
class Elaborate private constructor(
  private val dependencies: List<Dependency>,
  private val signature: Boolean,
  private val position: Position?,
) {
  private val metaEnv: MetaEnv = MetaEnv()
  private val diagnostics: MutableList<Diagnostic> = mutableListOf()
  private var varCompletionItems: MutableList<CompletionItem> = mutableListOf()
  private var definitionCompletionItems: MutableList<CompletionItem> = mutableListOf()
  private var hover: (() -> String)? = null
  private val definitions: Map<DefinitionLocation, C.Definition> =
    hashMapOf<DefinitionLocation, C.Definition>().also { definitions ->
      dependencies.forEach { dependency ->
        when (dependency.module) {
          null -> diagnostics += Diagnostic.ModuleNotFound(
            dependency.location,
            dependency.range!!,
          )
          else -> dependency.module.definitions.forEach { definition ->
            if (definition !is C.Definition.Hole) {
              definitions[definition.name] = definition // TODO: handle name duplication
            }
          }
        }
      }
    }

  private fun elaborateResult(
    input: Resolve.Result,
  ): Result {
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
    if (position != null) {
      definitionCompletionItems += definitions.map { (location, definition) ->
        CompletionItem(location.name).apply {
          val labelDetails = CompletionItemLabelDetails()
          kind = when (definition) {
            is C.Definition.Resource -> {
              labelDetails.detail = ": ${definition.registry.string}"
              CompletionItemKind.Struct
            }
            is C.Definition.Function -> {
              documentation = forRight(highlight(createFunctionDocumentation(definition)))
              labelDetails.detail = ": ${prettyType(definition.binder.type)} -> ${prettyType(definition.result)}"
              CompletionItemKind.Function
            }
            is C.Definition.Type     -> {
              // TODO: add documentation and detail
              CompletionItemKind.Class
            }
            is C.Definition.Hole     -> error("unexpected: hole")
          }
          labelDetails.description = location.module.toString()
          this.labelDetails = labelDetails
        }
      }
    }
    return C.Module(module.name, module.definitions.map { elaborateDefinition(it) })
  }

  private fun elaborateDefinition(
    definition: R.Definition,
  ): C.Definition {
    return when (definition) {
      is R.Definition.Resource -> {
        val annotations = definition.annotations.map { elaborateAnnotation(it) }
        C.Definition
          .Resource(annotations, definition.registry, definition.name.value)
          .also {
            if (!signature) {
              val env = emptyEnv(definitions, emptyList(), true)
              val body = env.elaborateTerm(definition.body /* TODO */)
              it.body = body
            }
          }
      }
      is R.Definition.Function -> {
        val annotations = definition.annotations.map { elaborateAnnotation(it) }
        val types = definition.typeParams.mapIndexed { level, typeParam -> typeParam to C.Type.Var(typeParam, level) }
        val meta = Annotation.Inline in annotations
        val env = emptyEnv(definitions, types, meta)
        val binder = env.elaboratePattern(definition.binder)
        val result = env.elaborateType(definition.result)
        C.Definition
          .Function(annotations, definition.name.value, definition.typeParams, binder, result)
          .also {
            hover(definition.name.range) { createFunctionDocumentation(it) }
            if (!signature) {
              val body = env.elaborateTerm(definition.body, result)
              it.body = body
            }
          }
      }
      is R.Definition.Type     -> {
        val annotations = definition.annotations.map { elaborateAnnotation(it) }
        val meta = Annotation.Inline in annotations
        val env = emptyEnv(definitions, emptyList(), meta)
        val body = env.elaborateType(definition.body)
        C.Definition.Type(annotations, definition.name.value, body)
      }
      is R.Definition.Hole     -> C.Definition.Hole
    }
  }

  private fun elaborateAnnotation(
    annotation: Ranged<Annotation>,
  ): Annotation {
    // TODO: validate
    return annotation.value
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
                  diagnostics += Diagnostic.TypeMismatch(head, element, it.range)
                }
                element
              }
          C.Type.Union(listOf(head) + tail, head.kind)
        }
      type is R.Type.Fun && expected == null       -> C.Type.Fun(elaborateType(type.param), elaborateType(type.result))
      type is R.Type.Code && expected == null      -> C.Type.Code(elaborateType(type.element))
      type is R.Type.Var && expected == null       -> C.Type.Var(type.name, type.level)
      type is R.Type.Run                           -> C.Type.Run(type.name, expected ?: metaEnv.freshKind())
      type is R.Type.Hole                          -> C.Type.Hole
      expected == null                             -> error("kind must be non-null")
      else                                         -> {
        val actual = elaborateType(type)
        if (!(actual.kind isSubkindOf expected)) {
          diagnostics += Diagnostic.KindMismatch(expected, actual.kind, type.range)
        }
        actual
      }
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
      expected == null              -> C.Term.StringOf(term.value, C.Type.String(term.value))

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
              diagnostics += Diagnostic.ExtraKey(key.value, key.range)
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
          .forEach { diagnostics += Diagnostic.KeyNotFound(it, term.range) }
        C.Term.CompoundOf(elements, C.Type.Compound(elements.mapValues { it.value.type }))
      }

      term is R.Term.RefOf &&
      expected is C.Type.Ref?       -> {
        val element = elaborateTerm(term.element, expected?.element)
        C.Term.RefOf(element, C.Type.Ref(element.type))
      }

      term is R.Term.TupleOf &&
      expected == null              -> {
        val elements = term.elements.map { element ->
          elaborateTerm(element)
        }
        C.Term.TupleOf(elements, C.Type.Tuple(elements.map { it.type }, C.Kind.Type(elements.size)))
      }

      term is R.Term.TupleOf &&
      expected is C.Type.Tuple      -> {
        if (expected.elements.size != term.elements.size) {
          diagnostics += Diagnostic.ArityMismatch(expected.elements.size, term.elements.size, term.range) // TODO: use KindMismatch
        }
        val elements = term.elements.mapIndexed { index, element ->
          elaborateTerm(element, expected.elements.getOrNull(index))
        }
        C.Term.TupleOf(elements, C.Type.Tuple(elements.map { it.type }, C.Kind.Type(elements.size)))
      }

      term is R.Term.FunOf &&
      expected is C.Type.Fun?       -> {
        val (binder, body) = restoring {
          val binder = elaboratePattern(term.binder, expected?.param)
          val body = elaborateTerm(term.body, expected?.result)
          binder to body
        }
        C.Term.FunOf(binder, body, expected ?: C.Type.Fun(binder.type, body.type))
      }

      term is R.Term.Apply          -> {
        val operator = elaborateTerm(term.operator)
        when (val operatorType = metaEnv.forceType(operator.type)) {
          is C.Type.Fun -> {
            val operand = elaborateTerm(term.operand, operatorType.param)
            C.Term.Apply(operator, operand, operatorType.result)
          }
          else          -> {
            diagnostics += Diagnostic.TypeMismatch(C.Type.Fun(C.Type.Hole, C.Type.Hole), operatorType, term.operator.range)
            C.Term.Hole(C.Type.Hole)
          }
        }
      }

      term is R.Term.If             -> {
        val condition = elaborateTerm(term.condition, C.Type.Bool.SET)
        val elseEnv = copy()
        val thenClause = elaborateTerm(term.thenClause, expected)
        val elseClause = elseEnv.elaborateTerm(term.elseClause, expected ?: thenClause.type)
        C.Term.If(condition, thenClause, elseClause, thenClause.type)
      }

      term is R.Term.Let            -> {
        val init = elaborateTerm(term.init)
        val (binder, body) = restoring {
          val binder = elaboratePattern(term.binder, init.type)
          val body = elaborateTerm(term.body, expected)
          binder to body
        }
        C.Term.Let(binder, init, body, body.type)
      }

      term is R.Term.Var &&
      expected == null              -> {
        val entry = entries[term.level]
        if (entry.used) {
          diagnostics += Diagnostic.VarAlreadyUsed(term.name, term.range)
        }
        if (stage != entry.stage) {
          diagnostics += Diagnostic.StageMismatch(stage, entry.stage, term.range)
        }
        entry.used = true
        C.Term.Var(term.name, term.level, entry.type)
      }

      term is R.Term.Run &&
      expected == null              -> {
        when (val definition = definitions[term.name.value]) {
          is C.Definition.Function -> {
            hover(term.name.range) { createFunctionDocumentation(definition) }
            val typeArgs =
              if (definition.typeParams.isNotEmpty() && term.typeArgs.value.isEmpty()) {
                definition.typeParams.map { metaEnv.freshType(term.typeArgs.range) }
              } else {
                if (definition.typeParams.size != term.typeArgs.value.size) {
                  diagnostics += Diagnostic.ArityMismatch(definition.typeParams.size, term.typeArgs.value.size, term.typeArgs.range.end..term.typeArgs.range.end)
                }
                term.typeArgs.value.map { elaborateType(it) }
              }
            val param = typeArgs.evalType(definition.binder.type)
            val arg = elaborateTerm(term.arg, param)
            val result = typeArgs.evalType(definition.result)
            C.Term.Run(definition.name, typeArgs, arg, result)
          }
          else                     -> {
            diagnostics += Diagnostic.ExpectedFunction(term.name.range)
            C.Term.Hole(C.Type.Hole)
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
        if (meta || stage > 0) {
          val element = quoting {
            elaborateTerm(term.element, expected?.element)
          }
          C.Term.CodeOf(element, C.Type.Code(element.type))
        } else {
          diagnostics += Diagnostic.RequiredInline(term.range)
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
            diagnostics += Diagnostic.TypeMismatch(C.Type.Code(C.Type.Hole), elementType, term.element.range)
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
          diagnostics += Diagnostic.TypeMismatch(expected, actual.type, term.range)
        }
        actual
      }
    }.also {
      if (position != null && position in term.range) {
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
      hoverType(term.range, it.type)
    }
  }

  private fun Env.elaboratePattern(
    pattern: R.Pattern,
    expected: C.Type? = null,
  ): C.Pattern {
    val expected = expected?.let { metaEnv.forceType(it) }
    val annotations = pattern.annotations.map { elaborateAnnotation(it) }
    return when {
      pattern is R.Pattern.IntOf &&
      expected is C.Type.Int?     -> C.Pattern.IntOf(pattern.value, annotations, C.Type.Int.SET)

      pattern is R.Pattern.IntRangeOf &&
      expected is C.Type.Int?     -> {
        if (pattern.min > pattern.max) {
          diagnostics += Diagnostic.EmptyRange(pattern.range)
        }
        C.Pattern.IntRangeOf(pattern.min, pattern.max, annotations, C.Type.Int.SET)
      }

      pattern is R.Pattern.CompoundOf &&
      expected == null            -> {
        val elements = pattern.elements.associate { (key, element) ->
          val element = elaboratePattern(element)
          hoverType(key.range, element.type)
          key.value to element
        }
        C.Pattern.CompoundOf(elements, annotations, C.Type.Compound(elements.mapValues { it.value.type }))
      }

      pattern is R.Pattern.CompoundOf &&
      expected is C.Type.Compound -> {
        val elements = mutableMapOf<String, C.Pattern>()
        pattern.elements.forEach { (key, element) ->
          when (val type = expected.elements[key.value]) {
            null -> {
              diagnostics += Diagnostic.ExtraKey(key.value, key.range)
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
          .forEach { diagnostics += Diagnostic.KeyNotFound(it, pattern.range) }
        C.Pattern.CompoundOf(elements, annotations, C.Type.Compound(elements.mapValues { it.value.type }))
      }

      pattern is R.Pattern.TupleOf &&
      expected is C.Type.Tuple    -> {
        if (expected.elements.size != pattern.elements.size) {
          diagnostics += Diagnostic.ArityMismatch(expected.elements.size, pattern.elements.size, pattern.range.end..pattern.range.end) // TODO: use KindMismatch
        }
        val elements = pattern.elements.mapIndexed { index, element ->
          elaboratePattern(element, expected.elements.getOrNull(index))
        }
        C.Pattern.TupleOf(elements, annotations, expected)
      }

      pattern is R.Pattern.TupleOf &&
      expected == null            -> {
        val elements = pattern.elements.map { element ->
          elaboratePattern(element)
        }
        C.Pattern.TupleOf(elements, annotations, C.Type.Tuple(elements.map { it.type }, C.Kind.Type(elements.size)))
      }

      pattern is R.Pattern.Var &&
      expected != null            -> {
        when (val kind = metaEnv.forceKind(expected.kind)) {
          is C.Kind.Type ->
            if (kind.arity == 1) {
              bind(pattern.name, expected)
              C.Pattern.Var(pattern.name, pattern.level, annotations, expected)
            } else {
              diagnostics += Diagnostic.KindMismatch(C.Kind.Type.ONE, kind, pattern.range)
              C.Pattern.Hole(annotations, expected)
            }
          is C.Kind.Meta -> {
            metaEnv.unifyKinds(kind, C.Kind.Type.ONE)
            bind(pattern.name, expected)
            C.Pattern.Var(pattern.name, pattern.level, annotations, expected)
          }
        }
      }

      pattern is R.Pattern.Var &&
      expected == null            -> {
        val type = metaEnv.freshType(pattern.range, C.Kind.Type(1))
        bind(pattern.name, type)
        C.Pattern.Var(pattern.name, pattern.level, annotations, type)
      }

      pattern is R.Pattern.Drop   -> C.Pattern.Drop(annotations, expected ?: metaEnv.freshType(pattern.range))

      pattern is R.Pattern.Anno &&
      expected == null            -> {
        val type = elaborateType(pattern.type)
        elaboratePattern(pattern.element, type)
      }

      pattern is R.Pattern.Hole   -> C.Pattern.Hole(annotations, expected ?: C.Type.Hole)

      expected == null            -> error("type must be non-null")

      else                        -> {
        val actual = elaboratePattern(pattern)
        if (!(actual.type isSubtypeOf expected)) {
          diagnostics += Diagnostic.TypeMismatch(expected, actual.type, pattern.range)
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
    return metaEnv.unifyKinds(this, kind2)
  }

  private infix fun C.Type.isSubtypeOf(
    type2: C.Type,
  ): Boolean {
    val type1 = metaEnv.forceType(this)
    val type2 = metaEnv.forceType(type2)
    return when {
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
      type2 is C.Type.Compound  -> type1.elements.size == type2.elements.size &&
                                   type1.elements.all { (key1, element1) ->
                                     when (val element2 = type2.elements[key1]) {
                                       null -> false
                                       else -> element1 isSubtypeOf element2
                                     }
                                   }

      type1 is C.Type.Ref &&
      type2 is C.Type.Ref       -> type1.element isSubtypeOf type2.element

      type1 is C.Type.Tuple &&
      type2 is C.Type.Tuple    -> type1.elements.size == type2.elements.size &&
                                   (type1.elements zip type2.elements).all { (element1, element2) -> element1 isSubtypeOf element2 }

      type1 is C.Type.Tuple &&
      type1.elements.size == 1 -> type1.elements.first() isSubtypeOf type2

      type2 is C.Type.Tuple &&
      type2.elements.size == 1 -> type1 isSubtypeOf type2.elements.first()

      type1 is C.Type.Fun &&
      type2 is C.Type.Fun      -> type2.param isSubtypeOf type1.param &&
                                  type1.result isSubtypeOf type2.result

      type1 is C.Type.Union    -> type1.elements.all { it isSubtypeOf type2 }
      type2 is C.Type.Union    -> type2.elements.any { type1 isSubtypeOf it }

      type1 is C.Type.Code &&
      type2 is C.Type.Code     -> type1.element isSubtypeOf type2.element

      type1 is C.Type.Var &&
      type2 is C.Type.Var      -> type1.level == type2.level

      type1 is C.Type.Run &&
      type2 is C.Type.Run      -> type1.name == type2.name

      type1 is C.Type.Meta     -> metaEnv.unifyTypes(type1, type2)
      type2 is C.Type.Meta     -> metaEnv.unifyTypes(type1, type2)

      type1 is C.Type.Hole     -> true
      type2 is C.Type.Hole     -> true

      else                     -> false
    }
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

  private fun createFunctionDocumentation(
    definition: C.Definition.Function,
  ): String {
    val name = definition.name.name
    val typeParams = if (definition.typeParams.isEmpty()) "" else definition.typeParams.joinToString(", ", "⟨", "⟩")
    val param = prettyPattern(definition.binder)
    val result = prettyType(definition.result)
    return "function $name$typeParams $param → $result"
  }

  private class Env private constructor(
    private val definitions: Map<DefinitionLocation, C.Definition>,
    private val types: List<Pair<String, C.Type>>,
    private val _entries: MutableList<Entry>,
    val meta: Boolean,
  ) {
    val entries: List<Entry> get() = _entries
    private var savedSize: Int = 0
    var stage: Int = 0
      private set

    fun bind(
      name: String,
      type: C.Type,
    ) {
      _entries += Entry(name, false, stage, type)
    }

    inline fun <R> restoring(
      action: () -> R,
    ): R {
      savedSize = _entries.size
      val result = action()
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
        meta,
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
        meta: Boolean,
      ): Env =
        Env(definitions, types, mutableListOf(), meta)
    }
  }

  data class Dependency(
    val location: ModuleLocation,
    val module: C.Module?,
    val range: Range?,
  )

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
      dependencies: List<Dependency>,
      input: Resolve.Result,
      signature: Boolean,
      position: Position? = null,
    ): Result =
      Elaborate(dependencies, signature, position).elaborateResult(input)
  }
}
