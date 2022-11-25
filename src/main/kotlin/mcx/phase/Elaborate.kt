package mcx.phase

import mcx.ast.*
import mcx.lsp.highlight
import mcx.phase.Elaborate.Env.Companion.emptyEnv
import mcx.phase.Normalize.evalType
import mcx.util.contains
import mcx.util.rangeTo
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either.forRight
import mcx.ast.Core as C
import mcx.ast.Surface as S

@Suppress("NAME_SHADOWING")
class Elaborate private constructor(
  private val dependencies: List<Dependency>,
  private val signature: Boolean,
  private val position: Position?,
) {
  private val diagnostics: MutableList<Diagnostic> = mutableListOf()
  private var varCompletionItems: MutableList<CompletionItem> = mutableListOf()
  private var definitionCompletionItems: MutableList<CompletionItem> = mutableListOf()
  private var hover: (() -> String)? = null

  private fun elaborateResult(
    input: Parse.Result,
  ): Result {
    return Result(
      elaborateModule(input.module),
      input.diagnostics + diagnostics,
      varCompletionItems + definitionCompletionItems,
      hover,
    )
  }

  private fun elaborateModule(
    module: S.Module,
  ): C.Module {
    val definitions = hashMapOf<DefinitionLocation, C.Definition>().also { definitions ->
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
            else                     -> error("unexpected: hole")
          }
          labelDetails.description = location.module.toString()
          this.labelDetails = labelDetails
        }
      }
    }
    return C.Module(
      module.name,
      module.definitions.map {
        elaborateDefinition(
          definitions,
          module.name,
          it,
        )
      },
    )
  }

  private fun elaborateDefinition(
    definitions: Map<DefinitionLocation, C.Definition>,
    module: ModuleLocation,
    definition: S.Definition,
  ): C.Definition {
    return when (definition) {
      is S.Definition.Resource -> {
        val annotations = definition.annotations.map { elaborateAnnotation(it) }
        C.Definition
          .Resource(annotations, definition.registry, module / definition.name.value)
          .also {
            if (!signature) {
              val env = emptyEnv(definitions, emptyList(), true)
              val body = env.elaborateTerm(definition.body /* TODO */)
              it.body = body
            }
          }
      }
      is S.Definition.Function -> {
        val annotations = definition.annotations.map { elaborateAnnotation(it) }
        val types = definition.typeParams.mapIndexed { level, typeParam -> typeParam to C.Type.Var(typeParam, level) }
        val meta = C.Annotation.Inline in annotations
        val env = emptyEnv(definitions, types, meta)
        val binder = env.elaboratePattern(definition.binder)
        val result = env.elaborateType(definition.result, meta = meta)
        C.Definition
          .Function(annotations, module / definition.name.value, definition.typeParams, binder, result)
          .also {
            hover(definition.name.range) { createFunctionDocumentation(it) }
            if (!signature) {
              val body = env.elaborateTerm(definition.body, result)
              it.body = body
            }
          }
      }
      is S.Definition.Hole     -> C.Definition.Hole
    }
  }

  private fun elaborateAnnotation(
    annotation: S.Annotation,
  ): C.Annotation {
    // TODO: validate
    return when (annotation) {
      is S.Annotation.Export  -> C.Annotation.Export
      is S.Annotation.Tick    -> C.Annotation.Tick
      is S.Annotation.Load    -> C.Annotation.Load
      is S.Annotation.NoDrop  -> C.Annotation.NoDrop
      is S.Annotation.Inline  -> C.Annotation.Inline
      is S.Annotation.Builtin -> C.Annotation.Builtin
      is S.Annotation.Hole    -> C.Annotation.Hole
    }
  }

  private fun Env.elaborateType(
    type: S.Type,
    arity: Int? = null,
    meta: Boolean? = null,
  ): C.Type {
    return when {
      type is S.Type.Bool && arity == null                 -> C.Type.Bool(type.value)
      type is S.Type.Byte && arity == null                 -> C.Type.Byte(type.value)
      type is S.Type.Short && arity == null                -> C.Type.Short(type.value)
      type is S.Type.Int && arity == null                  -> C.Type.Int(type.value)
      type is S.Type.Long && arity == null                 -> C.Type.Long(type.value)
      type is S.Type.Float && arity == null                -> C.Type.Float(type.value)
      type is S.Type.Double && arity == null               -> C.Type.Double(type.value)
      type is S.Type.String && arity == null               -> C.Type.String(type.value)
      type is S.Type.ByteArray && arity == null            -> C.Type.ByteArray
      type is S.Type.IntArray && arity == null             -> C.Type.IntArray
      type is S.Type.LongArray && arity == null            -> C.Type.LongArray
      type is S.Type.List && arity == null                 -> C.Type.List(elaborateType(type.element, 1, meta))
      type is S.Type.Compound && arity == null             -> C.Type.Compound(type.elements.mapValues { elaborateType(it.value, 1, meta) })
      type is S.Type.Ref && arity == null                  -> C.Type.Ref(elaborateType(type.element, 1, meta))
      type is S.Type.Tuple && arity == null                -> {
        val elements = type.elements.map { elaborateType(it, meta = meta) }
        C.Type.Tuple(elements, C.Kind(elements.size, elements.any { it.kind.meta }))
      }
      type is S.Type.Union                                 ->
        if (type.elements.isEmpty()) {
          C.Type.Union.END
        } else {
          val head = elaborateType(type.elements.first(), meta = meta)
          val tail =
            type.elements
              .drop(1)
              .map {
                val element = elaborateType(it, head.kind.arity, meta)
                if (element::class != head::class) {
                  diagnostics += Diagnostic.TypeMismatch(head, element, it.range)
                }
                element
              }
          C.Type.Union(listOf(head) + tail, head.kind)
        }
      type is S.Type.Fun && arity == null                  -> C.Type.Fun(elaborateType(type.param), elaborateType(type.result))
      type is S.Type.Code && arity == null && meta == null -> C.Type.Code(elaborateType(type.element))
      type is S.Type.Var && arity == null                  -> {
        when (val level = getType(type.name)) {
          -1   -> {
            diagnostics += Diagnostic.TypeVarNotFound(type.name, type.range)
            C.Type.Hole
          }
          else -> C.Type.Var(type.name, level)
        }
      }
      type is S.Type.Hole                                  -> C.Type.Hole
      else                                                 -> {
        val actual = elaborateType(type)
        if (arity?.let { it != actual.kind.arity } == true) {
          diagnostics += Diagnostic.ArityMismatch(arity, actual.kind.arity, type.range)
        }
        if (meta?.let { !it && actual.kind.meta } == true) {
          diagnostics += Diagnostic.RequiredInline(type.range)
        }
        actual
      }
    }
  }

  private fun Env.elaborateTerm(
    term: S.Term,
    expected: C.Type? = null,
  ): C.Term {
    return when {
      term is S.Term.BoolOf &&
      expected == null            -> C.Term.BoolOf(term.value, C.Type.Bool(term.value))

      term is S.Term.ByteOf &&
      expected == null            -> C.Term.ByteOf(term.value, C.Type.Byte(term.value))

      term is S.Term.ShortOf &&
      expected == null            -> C.Term.ShortOf(term.value, C.Type.Short(term.value))

      term is S.Term.IntOf &&
      expected == null            -> C.Term.IntOf(term.value, C.Type.Int(term.value))

      term is S.Term.LongOf &&
      expected == null            -> C.Term.LongOf(term.value, C.Type.Long(term.value))

      term is S.Term.FloatOf &&
      expected == null            -> C.Term.FloatOf(term.value, C.Type.Float(term.value))

      term is S.Term.DoubleOf &&
      expected == null            -> C.Term.DoubleOf(term.value, C.Type.Double(term.value))

      term is S.Term.StringOf &&
      expected == null            -> C.Term.StringOf(term.value, C.Type.String(term.value))

      term is S.Term.ByteArrayOf &&
      expected is C.Type.ByteArray? -> {
        val elements = term.elements.map { element ->
          elaborateTerm(element, C.Type.Byte.SET)
        }
        C.Term.ByteArrayOf(elements, C.Type.ByteArray)
      }

      term is S.Term.IntArrayOf &&
      expected is C.Type.IntArray?  -> {
        val elements = term.elements.map { element ->
          elaborateTerm(element, C.Type.Int.SET)
        }
        C.Term.IntArrayOf(elements, C.Type.IntArray)
      }

      term is S.Term.LongArrayOf &&
      expected is C.Type.LongArray? -> {
        val elements = term.elements.map { element ->
          elaborateTerm(element, C.Type.Long.SET)
        }
        C.Term.LongArrayOf(elements, C.Type.LongArray)
      }

      term is S.Term.ListOf &&
      term.elements.isEmpty() &&
      expected is C.Type.List?    -> C.Term.ListOf(emptyList(), C.Type.List(C.Type.Union.END))

      term is S.Term.ListOf &&
      expected is C.Type.List?      -> {
        val head = elaborateTerm(term.elements.first(), expected?.element)
        val element = expected?.element ?: head.type
        val tail =
          term.elements
            .drop(1)
            .map { elaborateTerm(it, element) }
        C.Term.ListOf(listOf(head) + tail, C.Type.List(element))
      }

      term is S.Term.CompoundOf &&
      expected == null              -> {
        val elements = term.elements.associate { (key, element) ->
          val element = elaborateTerm(element)
          hover(key.range) { prettyType(element.type) }
          key.value to element
        }
        C.Term.CompoundOf(elements, C.Type.Compound(elements.mapValues { it.value.type }))
      }

      term is S.Term.CompoundOf &&
      expected is C.Type.Compound -> {
        val elements = mutableMapOf<String, C.Term>()
        term.elements.forEach { (key, element) ->
          when (val type = expected.elements[key.value]) {
            null -> {
              diagnostics += Diagnostic.ExtraKey(key.value, key.range)
              val element = elaborateTerm(element)
              hover(key.range) { prettyType(element.type) }
              elements[key.value] = element
            }
            else -> {
              hover(key.range) { prettyType(type) }
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

      term is S.Term.RefOf &&
      expected is C.Type.Ref?     -> {
        val element = elaborateTerm(term.element, expected?.element)
        C.Term.RefOf(element, C.Type.Ref(element.type))
      }

      term is S.Term.TupleOf &&
      expected == null            -> {
        val elements = term.elements.map { element ->
          elaborateTerm(element)
        }
        C.Term.TupleOf(elements, C.Type.Tuple(elements.map { it.type }, C.Kind(elements.size, stage > 0)))
      }

      term is S.Term.TupleOf &&
      expected is C.Type.Tuple    -> {
        if (expected.elements.size != term.elements.size) {
          diagnostics += Diagnostic.ArityMismatch(expected.elements.size, term.elements.size, term.range)
        }
        val elements = term.elements.mapIndexed { index, element ->
          elaborateTerm(element, expected.elements.getOrNull(index))
        }
        C.Term.TupleOf(elements, C.Type.Tuple(elements.map { it.type }, C.Kind(elements.size, stage > 0)))
      }

      term is S.Term.FunOf &&
      expected is C.Type.Fun?     -> {
        val (binder, body) = restoring {
          val binder = elaboratePattern(term.binder, expected?.param)
          val body = elaborateTerm(term.body, expected?.result)
          binder to body
        }
        C.Term.FunOf(binder, body, expected ?: C.Type.Fun(binder.type, body.type))
      }

      term is S.Term.If           -> {
        val condition = elaborateTerm(term.condition, C.Type.Bool.SET)
        val elseEnv = copy()
        val thenClause = elaborateTerm(term.thenClause, expected)
        val elseClause = elseEnv.elaborateTerm(term.elseClause, expected ?: thenClause.type)
        C.Term.If(condition, thenClause, elseClause, thenClause.type)
      }

      term is S.Term.Let          -> {
        val init = elaborateTerm(term.init)
        val (binder, body) = restoring {
          val binder = elaboratePattern(term.binder, init.type)
          val body = elaborateTerm(term.body, expected)
          binder to body
        }
        C.Term.Let(binder, init, body, body.type)
      }

      term is S.Term.Var &&
      expected == null            ->
        when (val level = getVar(term.name)) {
          -1   -> {
            diagnostics += Diagnostic.VarNotFound(term.name, term.range)
            C.Term.Hole(C.Type.Hole)
          }
          else -> {
            val entry = entries[level]
            if (entry.used) {
              diagnostics += Diagnostic.VarAlreadyUsed(term.name, term.range)
            }
            if (stage != entry.stage) {
              diagnostics += Diagnostic.StageMismatch(stage, entry.stage, term.range)
            }
            entry.used = true
            C.Term.Var(term.name, level, entry.type)
          }
        }

      term is S.Term.Run &&
      expected == null            -> {
        if (term.operator is S.Term.Var && getVar(term.operator.name) == -1) {
          val location =
            term.operator.name
              .split('.')
              .let { DefinitionLocation(ModuleLocation(it.dropLast(1)), it.last()) }
          val definitions = findDefinition(location)
          when (definitions.size) {
            0    -> {
              diagnostics += Diagnostic.DefinitionNotFound(location, term.operator.range)
              C.Term.Hole(C.Type.Hole)
            }
            1    -> when (val definition = definitions.first()) {
              !is C.Definition.Function -> {
                diagnostics += Diagnostic.ExpectedFunction(term.operator.range)
                C.Term.Hole(C.Type.Hole)
              }
              else                      -> {
                hover(term.operator.range) { createFunctionDocumentation(definition) }
                if (definition.typeParams.size != term.typeArgs.value.size) {
                  diagnostics += Diagnostic.ArityMismatch(definition.typeParams.size, term.typeArgs.value.size, term.typeArgs.range.end..term.typeArgs.range.end)
                }
                val typeArgs = term.typeArgs.value.map { elaborateType(it) }
                val param = typeArgs.evalType(definition.binder.type)
                val arg = elaborateTerm(term.arg, param)
                val result = typeArgs.evalType(definition.result)
                C.Term.Run(definition.name, typeArgs, arg, result)
              }
            }
            else -> {
              diagnostics += Diagnostic.AmbiguousDefinition(location, term.operator.range)
              C.Term.Hole(C.Type.Hole)
            }
          }
        } else {
          val operator = elaborateTerm(term.operator)
          when (val operatorType = operator.type) {
            is C.Type.Fun -> {
              val arg = elaborateTerm(term.arg, operatorType.param)
              C.Term.Apply(operator, arg, operatorType.result)
            }
            else          -> {
              diagnostics += Diagnostic.TypeMismatch(C.Type.Fun(C.Type.Hole, C.Type.Hole), operatorType, term.operator.range)
              C.Term.Hole(C.Type.Hole)
            }
          }
        }
      }

      term is S.Term.Is &&
      expected is C.Type.Bool?      -> {
        val scrutinee = elaborateTerm(term.scrutinee)
        val scrutineer = restoring {
          elaboratePattern(term.scrutineer, scrutinee.type)
        }
        C.Term.Is(scrutinee, scrutineer, C.Type.Bool.SET)
      }

      term is S.Term.CodeOf &&
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

      term is S.Term.Splice         -> {
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

      term is S.Term.Hole           ->
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
      hover(term.range) { prettyType(it.type) }
    }
  }

  private fun Env.elaboratePattern(
    pattern: S.Pattern,
    expected: C.Type? = null,
  ): C.Pattern {
    val annotations = pattern.annotations.map { elaborateAnnotation(it) }
    return when {
      pattern is S.Pattern.IntOf &&
      expected is C.Type.Int?     -> C.Pattern.IntOf(pattern.value, annotations, C.Type.Int.SET)

      pattern is S.Pattern.IntRangeOf &&
      expected is C.Type.Int?     -> {
        if (pattern.min > pattern.max) {
          diagnostics += Diagnostic.EmptyRange(pattern.range)
        }
        C.Pattern.IntRangeOf(pattern.min, pattern.max, annotations, C.Type.Int.SET)
      }

      pattern is S.Pattern.CompoundOf &&
      expected == null            -> {
        val elements = pattern.elements.associate { (key, element) ->
          val element = elaboratePattern(element)
          hover(key.range) { prettyType(element.type) }
          key.value to element
        }
        C.Pattern.CompoundOf(elements, annotations, C.Type.Compound(elements.mapValues { it.value.type }))
      }

      pattern is S.Pattern.CompoundOf &&
      expected is C.Type.Compound -> {
        val elements = mutableMapOf<String, C.Pattern>()
        pattern.elements.forEach { (key, element) ->
          when (val type = expected.elements[key.value]) {
            null -> {
              diagnostics += Diagnostic.ExtraKey(key.value, key.range)
              val element = elaboratePattern(element)
              hover(key.range) { prettyType(element.type) }
              elements[key.value] = element
            }
            else -> {
              hover(key.range) { prettyType(type) }
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

      pattern is S.Pattern.TupleOf &&
      expected is C.Type.Tuple    -> {
        if (expected.elements.size != pattern.elements.size) {
          diagnostics += Diagnostic.ArityMismatch(expected.elements.size, pattern.elements.size, pattern.range.end..pattern.range.end)
        }
        val elements = pattern.elements.mapIndexed { index, element ->
          elaboratePattern(element, expected.elements.getOrNull(index))
        }
        C.Pattern.TupleOf(elements, annotations, expected)
      }

      pattern is S.Pattern.TupleOf &&
      expected == null            -> {
        val elements = pattern.elements.map { element ->
          elaboratePattern(element)
        }
        C.Pattern.TupleOf(elements, annotations, C.Type.Tuple(elements.map { it.type }, C.Kind(elements.size, stage > 0)))
      }

      pattern is S.Pattern.Var &&
      expected != null          -> {
        if (expected.kind.arity == 1) {
          bind(pattern.name, expected)
          C.Pattern.Var(pattern.name, entries.lastIndex, annotations, expected)
        } else {
          diagnostics += Diagnostic.ArityMismatch(1, expected.kind.arity, pattern.range)
          C.Pattern.Hole(annotations, expected)
        }
      }

      pattern is S.Pattern.Var &&
      expected == null          -> {
        diagnostics += Diagnostic.CannotSynthesizeType(pattern.range)
        C.Pattern.Hole(annotations, C.Type.Hole)
      }

      pattern is S.Pattern.Drop -> C.Pattern.Drop(annotations, expected ?: C.Type.Hole)

      pattern is S.Pattern.Anno &&
      expected == null          -> {
        val type = elaborateType(pattern.type, meta = meta || stage > 0)
        elaboratePattern(pattern.element, type)
      }

      pattern is S.Pattern.Hole -> C.Pattern.Hole(annotations, expected ?: C.Type.Hole)

      expected == null          -> error("type must be non-null")

      else                      -> {
        val actual = elaboratePattern(pattern)
        if (!(actual.type isSubtypeOf expected)) {
          diagnostics += Diagnostic.TypeMismatch(expected, actual.type, pattern.range)
        }
        actual
      }
    }.also {
      hover(pattern.range) { prettyType(it.type) }
    }
  }

  private infix fun C.Type.isSubtypeOf(
    type2: C.Type,
  ): Boolean {
    val type1 = this
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

      type1 is C.Type.Fun &&
      type2 is C.Type.Fun      -> type2.param isSubtypeOf type1.param &&
                                  type1.result isSubtypeOf type2.result

      type1 is C.Type.Union    -> type1.elements.all { it isSubtypeOf type2 }
      type2 is C.Type.Union    -> type2.elements.any { type1 isSubtypeOf it }

      type1 is C.Type.Code &&
      type2 is C.Type.Code     -> type1.element isSubtypeOf type2.element

      type1 is C.Type.Var &&
      type2 is C.Type.Var      -> type1.level == type2.level

      type1 is C.Type.Hole     -> true
      type2 is C.Type.Hole     -> true

      else                     -> false
    }
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

    fun getType(
      name: String,
    ): Int =
      types.indexOfLast { it.first == name }

    fun getVar(
      name: String,
    ): Int =
      _entries.indexOfLast { it.name == name }

    fun findDefinition(
      expected: DefinitionLocation,
    ): List<C.Definition> =
      definitions.entries
        .filter { (actual, _) ->
          expected.module.parts.size <= actual.module.parts.size &&
          (expected.name == actual.name) &&
          (expected.module.parts.asReversed() zip actual.module.parts.asReversed()).all { it.first == it.second }
        }
        .map { it.value }

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
    val diagnostics: List<Diagnostic>,
    val completionItems: List<CompletionItem>,
    val hover: (() -> String)?,
  )

  companion object {
    operator fun invoke(
      context: Context,
      dependencies: List<Dependency>,
      input: Parse.Result,
      signature: Boolean,
      position: Position? = null,
    ): Result =
      Elaborate(dependencies, signature, position).elaborateResult(input)
  }
}
