package mcx.phase

import mcx.ast.Core
import mcx.ast.Location
import mcx.ast.Surface
import mcx.lsp.highlight
import mcx.phase.Elaborate.Env.Companion.emptyEnv
import mcx.util.contains
import mcx.util.rangeTo
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either.forRight
import mcx.ast.Core as C
import mcx.ast.Surface as S

class Elaborate private constructor(
  private val dependencies: List<Dependency>,
  private val signature: Boolean,
  private val position: Position?,
) {
  private val diagnostics: MutableList<Diagnostic> = mutableListOf()
  private var varCompletionItems: MutableList<CompletionItem> = mutableListOf()
  private var resourceCompletionItems: MutableList<CompletionItem> = mutableListOf()
  private var hover: (() -> String)? = null

  private fun elaborateResult(
    input: Parse.Result,
  ): Result {
    return Result(
      elaborateModule(input.module),
      input.diagnostics + diagnostics,
      varCompletionItems + resourceCompletionItems,
      hover,
    )
  }

  private fun elaborateModule(
    module: Surface.Module,
  ): Core.Module {
    val resources = hashMapOf<Location, C.Resource>().also { resources ->
      dependencies.forEach { dependency ->
        when (dependency.module) {
          null -> diagnostics += Diagnostic.ModuleNotFound(
            dependency.location,
            dependency.range!!,
          )
          else -> dependency.module.resources.forEach { resource ->
            if (resource !is C.Resource.Hole) {
              resources[resource.name] = resource // TODO: handle name duplication
            }
          }
        }
      }
    }
    if (position != null) {
      resourceCompletionItems += resources.map { (location, resource) ->
        val name = location.parts.last()
        CompletionItem(name).apply {
          val labelDetails = CompletionItemLabelDetails()
          kind = when (resource) {
            is C.Resource.JsonResource -> {
              labelDetails.detail = ": ${resource.registry.string}"
              CompletionItemKind.Struct
            }
            is Core.Resource.Function  -> {
              documentation = forRight(highlight(createFunctionDocumentation(resource)))
              labelDetails.detail = ": ${prettyType(resource.param)} -> ${prettyType(resource.result)}"
              CompletionItemKind.Function
            }
            else                       -> error("unexpected: hole")
          }
          labelDetails.description =
            location.parts
              .dropLast(1)
              .joinToString("/")
          this.labelDetails = labelDetails
        }
      }
    }
    return C.Module(
      module.name,
      module.resources.map {
        elaborateResource(
          resources,
          module.name,
          it,
        )
      },
    )
  }

  private fun elaborateResource(
    resources: Map<Location, C.Resource>,
    module: Location,
    resource: S.Resource,
  ): C.Resource {
    return when (resource) {
      is S.Resource.JsonResource   -> {
        val annotations = resource.annotations.map { elaborateAnnotation(it) }
        C.Resource
          .JsonResource(annotations, resource.registry, module + resource.name.value)
          .also {
            if (!signature) {
              val env = emptyEnv(resources, true)
              val body = elaborateTerm(env, resource.body /* TODO */)
              it.body = body
            }
          }
      }
      is Surface.Resource.Function -> {
        val annotations = resource.annotations.map { elaborateAnnotation(it) }
        val meta = C.Annotation.Inline in annotations
        val env = emptyEnv(resources, meta)
        val binder = elaboratePattern(env, resource.binder)
        val result = elaborateType(resource.result, meta = meta)
        C.Resource
          .Function(annotations, module + resource.name.value, binder, binder.type, result)
          .also {
            hover(resource.name.range) { createFunctionDocumentation(it) }
            if (!signature) {
              val body = elaborateTerm(env, resource.body, result)
              it.body = body
            }
          }
      }
      is S.Resource.Hole           -> C.Resource.Hole
    }
  }

  private fun elaborateAnnotation(
    annotation: S.Annotation,
  ): C.Annotation {
    // TODO: validate
    return when (annotation) {
      is S.Annotation.Tick    -> C.Annotation.Tick
      is S.Annotation.Load    -> C.Annotation.Load
      is S.Annotation.NoDrop  -> C.Annotation.NoDrop
      is S.Annotation.Inline  -> C.Annotation.Inline
      is S.Annotation.Builtin -> C.Annotation.Builtin
      is S.Annotation.Hole    -> C.Annotation.Hole
    }
  }

  private fun elaborateType(
    type: S.Type,
    arity: Int? = null,
    meta: Boolean? = null,
  ): C.Type {
    return when {
      type is S.Type.End && arity == null                  -> C.Type.End
      type is S.Type.Bool && arity == null                 -> C.Type.Bool
      type is S.Type.Byte && arity == null                 -> C.Type.Byte
      type is S.Type.Short && arity == null                -> C.Type.Short
      type is S.Type.Int && arity == null                  -> C.Type.Int
      type is S.Type.Long && arity == null                 -> C.Type.Long
      type is S.Type.Float && arity == null                -> C.Type.Float
      type is S.Type.Double && arity == null               -> C.Type.Double
      type is S.Type.String && arity == null               -> C.Type.String
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
      type is S.Type.Code && arity == null && meta == null -> C.Type.Code(elaborateType(type.element))
      type is S.Type.Type && arity == null && meta == null -> C.Type.Type
      type is S.Type.Hole                                  -> C.Type.Hole
      else                                                 -> {
        val actual = elaborateType(type)
        if (arity?.let { it != actual.kind.arity } == true) {
          diagnostics += Diagnostic.ArityMismatch(arity, actual.kind.arity, type.range)
        }
        if (meta?.let { it != actual.kind.meta } == true) {
          diagnostics += Diagnostic.RequiredInline(type.range)
        }
        actual
      }
    }
  }

  private fun elaborateTerm(
    env: Env,
    term: S.Term,
    expected: C.Type? = null,
  ): C.Term {
    return when {
      term is S.Term.BoolOf &&
      expected is C.Type.Bool?    -> C.Term.BoolOf(term.value, C.Type.Bool)

      term is S.Term.ByteOf &&
      expected is C.Type.Byte?      -> C.Term.ByteOf(term.value, C.Type.Byte)

      term is S.Term.ShortOf &&
      expected is C.Type.Short?     -> C.Term.ShortOf(term.value, C.Type.Short)

      term is S.Term.IntOf &&
      expected is C.Type.Int?       -> C.Term.IntOf(term.value, C.Type.Int)

      term is S.Term.LongOf &&
      expected is C.Type.Long?      -> C.Term.LongOf(term.value, C.Type.Long)

      term is S.Term.FloatOf &&
      expected is C.Type.Float?     -> C.Term.FloatOf(term.value, C.Type.Float)

      term is S.Term.DoubleOf &&
      expected is C.Type.Double?    -> C.Term.DoubleOf(term.value, C.Type.Double)

      term is S.Term.StringOf &&
      expected is C.Type.String?    -> C.Term.StringOf(term.value, C.Type.String)

      term is S.Term.ByteArrayOf &&
      expected is C.Type.ByteArray? -> {
        val elements = term.elements.map { element ->
          elaborateTerm(env, element, C.Type.Byte)
        }
        C.Term.ByteArrayOf(elements, C.Type.ByteArray)
      }

      term is S.Term.IntArrayOf &&
      expected is C.Type.IntArray?  -> {
        val elements = term.elements.map { element ->
          elaborateTerm(env, element, C.Type.Int)
        }
        C.Term.IntArrayOf(elements, C.Type.IntArray)
      }

      term is S.Term.LongArrayOf &&
      expected is C.Type.LongArray? -> {
        val elements = term.elements.map { element ->
          elaborateTerm(env, element, C.Type.Long)
        }
        C.Term.LongArrayOf(elements, C.Type.LongArray)
      }

      term is S.Term.ListOf &&
      term.elements.isEmpty()       -> C.Term.ListOf(emptyList(), C.Type.List(C.Type.End))

      term is S.Term.ListOf &&
      expected is C.Type.List?      -> {
        val head = elaborateTerm(env, term.elements.first(), expected?.element)
        val element = expected?.element ?: head.type
        val tail =
          term.elements
            .drop(1)
            .map { elaborateTerm(env, it, element) }
        C.Term.ListOf(listOf(head) + tail, C.Type.List(element))
      }

      term is S.Term.CompoundOf &&
      expected == null            -> {
        val elements = term.elements.associate { (key, element) ->
          @Suppress("NAME_SHADOWING")
          val element = elaborateTerm(env, element)
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
              @Suppress("NAME_SHADOWING")
              val element = elaborateTerm(env, element)
              hover(key.range) { prettyType(element.type) }
              elements[key.value] = element
            }
            else -> {
              hover(key.range) { prettyType(type) }
              elements[key.value] = elaborateTerm(env, element, type)
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
        val element = elaborateTerm(env, term.element, expected?.element)
        C.Term.RefOf(element, C.Type.Ref(element.type))
      }

      term is S.Term.If           -> {
        val condition = elaborateTerm(env, term.condition, C.Type.Bool)
        val elseEnv = env.copy()
        val thenClause = elaborateTerm(env, term.thenClause, expected)
        val elseClause = elaborateTerm(elseEnv, term.elseClause, expected ?: thenClause.type)
        C.Term.If(condition, thenClause, elseClause, thenClause.type)
      }

      term is S.Term.Let          -> {
        val init = elaborateTerm(env, term.init)
        val (binder, body) = env.restoring {
          val binder = elaboratePattern(env, term.binder, init.type)
          val body = elaborateTerm(env, term.body, expected)
          binder to body
        }
        C.Term.Let(binder, init, body, body.type)
      }

      term is S.Term.Var &&
      expected == null            ->
        when (val level = env[term.name]) {
          -1   -> {
            diagnostics += Diagnostic.VarNotFound(term.name, term.range)
            C.Term.Hole(C.Type.Hole)
          }
          else -> {
            val entry = env.entries[level]
            if (entry.used) {
              diagnostics += Diagnostic.VarAlreadyUsed(term.name, term.range)
            }
            if (env.stage != entry.stage) {
              diagnostics += Diagnostic.StageMismatch(env.stage, entry.stage, term.range)
            }
            entry.used = true
            C.Term.Var(term.name, level, entry.type)
          }
        }

      term is S.Term.Run &&
      expected == null            -> {
        val resources = env.findResources(term.name.value)
        when (resources.size) {
          0    -> {
            diagnostics += Diagnostic.ResourceNotFound(term.name.value, term.name.range)
            C.Term.Hole(C.Type.Hole)
          }
          1    -> when (val resource = resources.first()) {
            !is Core.Resource.Function -> {
              diagnostics += Diagnostic.ExpectedFunction(term.name.range)
              C.Term.Hole(C.Type.Hole)
            }
            else                       -> {
              hover(term.name.range) { createFunctionDocumentation(resource) }
              val arg = elaborateTerm(env, term.arg, resource.param)
              C.Term.Run(resource.name, arg, resource.result)
            }
          }
          else -> {
            diagnostics += Diagnostic.AmbiguousResource(term.name.value, term.name.range)
            C.Term.Hole(C.Type.Hole)
          }
        }
      }

      term is S.Term.Is &&
      expected is C.Type.Bool?    -> {
        val scrutinee = elaborateTerm(env, term.scrutinee)
        val scrutineer = env.restoring {
          elaboratePattern(env, term.scrutineer, scrutinee.type)
        }
        C.Term.Is(scrutinee, scrutineer, C.Type.Bool)
      }

      term is S.Term.TupleOf &&
      expected == null            -> {
        val elements = term.elements.map { element ->
          elaborateTerm(env, element)
        }
        C.Term.TupleOf(elements, C.Type.Tuple(elements.map { it.type }, C.Kind(elements.size, env.stage > 0)))
      }

      term is S.Term.TupleOf &&
      expected is C.Type.Tuple    -> {
        if (expected.elements.size != term.elements.size) {
          diagnostics += Diagnostic.ArityMismatch(expected.elements.size, term.elements.size, term.range)
        }
        val elements = term.elements.mapIndexed { index, element ->
          elaborateTerm(env, element, expected.elements.getOrNull(index))
        }
        C.Term.TupleOf(elements, C.Type.Tuple(elements.map { it.type }, C.Kind(elements.size, env.stage > 0)))
      }

      term is S.Term.Command      -> C.Term.Command(term.value, expected ?: C.Type.End)

      term is S.Term.CodeOf &&
      expected is C.Type.Code?    -> {
        val element = env.quoting {
          elaborateTerm(env, term.element, expected?.element)
        }
        C.Term.CodeOf(element, C.Type.Code(element.type))
      }

      term is S.Term.Splice       -> {
        val element = env.splicing {
          elaborateTerm(env, term.element, expected?.let { C.Type.Code(it) })
        }
        C.Term.Splice(element, element.type)
      }

      term is S.Term.TypeOf &&
      expected is C.Type.Type?    -> {
        val value = elaborateType(term.value, meta = env.meta)
        C.Term.TypeOf(value, C.Type.Type)
      }

      term is S.Term.Hole         ->
        C.Term.Hole(expected ?: C.Type.Hole)

      expected == null            -> error("type must be non-null")

      else                        -> {
        val actual = elaborateTerm(env, term)
        if (!(actual.type isSubtypeOf expected)) {
          diagnostics += Diagnostic.TypeMismatch(expected, actual.type, term.range)
        }
        actual
      }
    }.also {
      if (position != null && position in term.range) {
        if (varCompletionItems.isEmpty()) {
          varCompletionItems += env.entries
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

  private fun elaboratePattern(
    env: Env,
    pattern: S.Pattern,
    expected: C.Type? = null,
  ): C.Pattern {
    val annotations = pattern.annotations.map { elaborateAnnotation(it) }
    return when {
      pattern is S.Pattern.IntOf &&
      expected is C.Type.Int?   -> C.Pattern.IntOf(pattern.value, annotations, C.Type.Int)

      pattern is S.Pattern.IntRangeOf &&
      expected is C.Type.Int?   -> {
        if (pattern.min > pattern.max) {
          diagnostics += Diagnostic.EmptyRange(pattern.range)
        }
        C.Pattern.IntRangeOf(pattern.min, pattern.max, annotations, C.Type.Int)
      }

      pattern is S.Pattern.TupleOf &&
      expected is C.Type.Tuple  -> {
        if (expected.elements.size != pattern.elements.size) {
          diagnostics += Diagnostic.ArityMismatch(expected.elements.size, pattern.elements.size, pattern.range.end..pattern.range.end)
        }
        val elements = pattern.elements.mapIndexed { index, element ->
          elaboratePattern(env, element, expected.elements.getOrNull(index))
        }
        C.Pattern.TupleOf(elements, annotations, expected)
      }

      pattern is S.Pattern.TupleOf &&
      expected == null          -> {
        val elements = pattern.elements.map { element ->
          elaboratePattern(env, element)
        }
        C.Pattern.TupleOf(elements, annotations, C.Type.Tuple(elements.map { it.type }, C.Kind(elements.size, env.stage > 0)))
      }

      pattern is S.Pattern.Var &&
      expected != null          -> {
        val meta = !expected.kind.meta || env.meta
        if (!meta) {
          diagnostics += Diagnostic.RequiredInline(pattern.range)
        }
        val arity = expected.kind.arity == 1
        if (!arity) {
          diagnostics += Diagnostic.ArityMismatch(1, expected.kind.arity, pattern.range)
        }
        if (meta && arity) {
          env.bind(pattern.name, expected)
          C.Pattern.Var(pattern.name, env.entries.lastIndex, annotations, expected)
        } else {
          C.Pattern.Hole(annotations, expected)
        }
      }

      pattern is S.Pattern.Var &&
      expected == null          -> {
        diagnostics += Diagnostic.CannotSynthesizeType(pattern.range)
        C.Pattern.Hole(annotations, C.Type.Hole)
      }

      pattern is S.Pattern.Drop -> C.Pattern.Drop(annotations, expected ?: C.Type.End)

      pattern is S.Pattern.Anno &&
      expected == null          -> {
        val type = elaborateType(pattern.type, meta = env.meta)
        elaboratePattern(env, pattern.element, type)
      }

      pattern is S.Pattern.Hole -> C.Pattern.Hole(annotations, expected ?: C.Type.Hole)

      expected == null          -> error("type must be non-null")

      else                      -> {
        val actual = elaboratePattern(env, pattern)
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
      type1 is C.Type.End       -> true

      type1 is C.Type.Bool &&
      type2 is C.Type.Bool      -> true

      type1 is C.Type.Byte &&
      type2 is C.Type.Byte      -> true

      type1 is C.Type.Short &&
      type2 is C.Type.Short     -> true

      type1 is C.Type.Int &&
      type2 is C.Type.Int       -> true

      type1 is C.Type.Long &&
      type2 is C.Type.Long      -> true

      type1 is C.Type.Float &&
      type2 is C.Type.Float     -> true

      type1 is C.Type.Double &&
      type2 is C.Type.Double    -> true

      type1 is C.Type.String &&
      type2 is C.Type.String    -> true

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

      type1 is C.Type.Code &&
      type2 is C.Type.Code     -> type1.element isSubtypeOf type2.element

      type1 is C.Type.Type &&
      type2 is C.Type.Type     -> true

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
    resource: C.Resource.Function,
  ): String {
    val name = resource.name.parts.last()
    val param = prettyType(resource.param)
    val result = prettyType(resource.result)
    return "function $name: $param -> $result"
  }

  private class Env private constructor(
    private val resources: Map<Location, C.Resource>,
    private val _entries: MutableList<Entry>,
    val meta: Boolean,
  ) {
    val entries: List<Entry> get() = _entries
    private var savedSize: Int = 0
    var stage: Int = 0
      private set

    operator fun get(name: String): Int =
      _entries.indexOfLast { it.name == name }

    fun findResources(
      expected: Location,
    ): List<C.Resource> =
      resources.entries
        .filter { (actual, _) ->
          expected.parts.size <= actual.parts.size &&
          (expected.parts.asReversed() zip actual.parts.asReversed()).all { it.first == it.second }
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
        resources,
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
        resources: Map<Location, Core.Resource>,
        meta: Boolean,
      ): Env =
        Env(resources, mutableListOf(), meta)
    }
  }

  data class Dependency(
    val location: Location,
    val module: Core.Module?,
    val range: Range?,
  )

  data class Result(
    val module: Core.Module,
    val diagnostics: List<Diagnostic>,
    val completionItems: List<CompletionItem>,
    val hover: (() -> String)?,
  )

  companion object {
    operator fun invoke(
      config: Config,
      dependencies: List<Dependency>,
      input: Parse.Result,
      signature: Boolean,
      position: Position? = null,
    ): Result =
      Elaborate(dependencies, signature, position).elaborateResult(input)
  }
}
