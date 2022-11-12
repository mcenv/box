package mcx.phase

import mcx.ast.Core
import mcx.ast.Location
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
  private var hover: C.Type? = null

  private fun elaborateResult(
    input: Parse.Result,
  ): Result {
    return Result(
      elaborateRoot(input.root),
      input.diagnostics + diagnostics,
      varCompletionItems + resourceCompletionItems,
      hover,
    )
  }

  private fun elaborateRoot(
    root: S.Root,
  ): C.Root {
    val resources = hashMapOf<Location, C.Resource>().also { resources ->
      dependencies.forEach { dependency ->
        when (dependency.root) {
          null -> diagnostics += Diagnostic.ModuleNotFound(
            dependency.location,
            dependency.range!!,
          )
          else -> dependency.root.resources.forEach { resource ->
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
            is C.Resource.Functions    -> {
              val detail = "${
                resource.params.joinToString(", ", "(", ")") { (param, type) ->
                  "$param: ${prettyType(type)}"
                }
              }: ${prettyType(resource.result)}"
              documentation = forRight(highlight("functions $name$detail"))
              labelDetails.detail = detail
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
    return C.Root(
      root.module,
      root.resources.map {
        elaborateResource(
          resources,
          root.module,
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
      is S.Resource.JsonResource -> {
        val annotations = resource.annotations.map { elaborateAnnotation(it) }
        C.Resource
          .JsonResource(
            annotations,
            resource.registry,
            module + resource.name,
          )
          .also {
            if (!signature) {
              val env = emptyEnv(resources)
              val body = elaborateTerm(env, resource.body /* TODO */)
              it.body = body
            }
          }
      }
      is S.Resource.Functions    -> {
        val annotations = resource.annotations.map { elaborateAnnotation(it) }
        val env = emptyEnv(resources)
        val params = resource.params.map {
          val type = elaborateType(it.second)
          env.bind(it.first, type)
          it.first to type
        }
        val result = elaborateType(resource.result)
        C.Resource
          .Functions(
            annotations,
            module + resource.name,
            params,
            result,
          )
          .also {
            if (!signature) {
              val body = elaborateTerm(env, resource.body, result)
              it.body = body
            }
          }
      }
      is S.Resource.Hole         -> C.Resource.Hole
    }
  }

  private fun elaborateAnnotation(
    annotation: S.Annotation,
  ): C.Annotation {
    // TODO: validate
    return when (annotation) {
      is S.Annotation.Tick -> C.Annotation.Tick
      is S.Annotation.Load -> C.Annotation.Load
      is S.Annotation.Hole -> C.Annotation.Hole
    }
  }

  private fun elaborateType(
    type: S.Type,
  ): C.Type {
    return when (type) {
      is S.Type.End      -> C.Type.End
      is S.Type.Bool     -> C.Type.Bool
      is S.Type.Byte     -> C.Type.Byte
      is S.Type.Short    -> C.Type.Short
      is S.Type.Int      -> C.Type.Int
      is S.Type.Long     -> C.Type.Long
      is S.Type.Float    -> C.Type.Float
      is S.Type.Double   -> C.Type.Double
      is S.Type.String   -> C.Type.String
      is S.Type.List     -> C.Type.List(elaborateType(type.element))
      is S.Type.Compound -> C.Type.Compound(type.elements.mapValues { elaborateType(it.value) })
      is S.Type.Box      -> C.Type.Box(elaborateType(type.element))
      is S.Type.Hole     -> C.Type.Hole
    }
  }

  private fun elaborateTerm(
    env: Env,
    term: S.Term,
    expected: C.Type? = null,
  ): C.Term {
    return when {
      term is S.Term.BoolOf &&
      expected is C.Type.Bool?          -> C.Term.BoolOf(term.value, C.Type.Bool)

      term is S.Term.ByteOf &&
      expected is C.Type.Byte?          -> C.Term.ByteOf(term.value, C.Type.Byte)

      term is S.Term.ShortOf &&
      expected is C.Type.Short?         -> C.Term.ShortOf(term.value, C.Type.Short)

      term is S.Term.IntOf &&
      expected is C.Type.Int?           -> C.Term.IntOf(term.value, C.Type.Int)

      term is S.Term.LongOf &&
      expected is C.Type.Long?          -> C.Term.LongOf(term.value, C.Type.Long)

      term is S.Term.FloatOf &&
      expected is C.Type.Float?         -> C.Term.FloatOf(term.value, C.Type.Float)

      term is S.Term.DoubleOf &&
      expected is C.Type.Double?        -> C.Term.DoubleOf(term.value, C.Type.Double)

      term is S.Term.StringOf &&
      expected is C.Type.String?        -> C.Term.StringOf(term.value, C.Type.String)

      term is S.Term.ListOf &&
      term.values.isEmpty()             -> C.Term.ListOf(emptyList(), C.Type.List(C.Type.End))

      term is S.Term.ListOf &&
      expected is C.Type.List?    -> {
        val head = elaborateTerm(env, term.values.first(), expected?.element)
        val element = expected?.element ?: head.type
        val tail =
          term.values
            .drop(1)
            .map { elaborateTerm(env, it, element) }
        C.Term.ListOf(listOf(head) + tail, C.Type.List(element))
      }

      term is S.Term.CompoundOf &&
      expected == null            -> {
        val values = term.values.associate { (key, value) ->
          @Suppress("NAME_SHADOWING")
          val value = elaborateTerm(env, value)
          hover(value.type, key.range)
          key.value to value
        }
        C.Term.CompoundOf(values, C.Type.Compound(values.mapValues { it.value.type }))
      }

      term is S.Term.CompoundOf &&
      expected is C.Type.Compound -> {
        val values = mutableMapOf<String, C.Term>()
        term.values.forEach { (key, value) ->
          when (val element = expected.elements[key.value]) {
            null -> {
              diagnostics += Diagnostic.ExtraKey(key.value, key.range)
              @Suppress("NAME_SHADOWING")
              val value = elaborateTerm(env, value)
              hover(value.type, key.range)
              values[key.value] = value
            }
            else -> {
              hover(element, key.range)
              values[key.value] = elaborateTerm(env, value, element)
            }
          }
        }
        expected.elements.keys
          .minus(
            term.values
              .map { it.first.value }
              .toSet()
          )
          .forEach { diagnostics += Diagnostic.KeyNotFound(it, term.range) }
        C.Term.CompoundOf(values, C.Type.Compound(values.mapValues { it.value.type }))
      }

      term is S.Term.BoxOf &&
      expected is C.Type.Box?     -> {
        val value = elaborateTerm(env, term.value, expected?.element)
        C.Term.BoxOf(value, C.Type.Box(value.type))
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
        hover(init.type, term.name.range)
        val body = env.binding(term.name.value, init.type) {
          elaborateTerm(env, term.body, expected)
        }
        C.Term.Let(term.name.value, init, body, body.type)
      }

      term is S.Term.Var &&
      expected == null            ->
        when (val entry = env[term.name]) {
          null -> {
            diagnostics += Diagnostic.VarNotFound(term.name, term.range)
            C.Term.Hole(C.Type.Hole)
          }
          else -> if (entry.used) {
            diagnostics += Diagnostic.VarAlreadyUsed(term.name, term.range)
            C.Term.Hole(entry.type)
          } else {
            entry.used = true
            C.Term.Var(term.name, entry.type)
          }
        }

      term is S.Term.Run &&
      expected == null            ->
        when (val resource = env.findResource(term.name)) {
          null                        -> {
            diagnostics += Diagnostic.ResourceNotFound(term.name, term.range)
            C.Term.Hole(C.Type.Hole)
          }
          !is Core.Resource.Functions -> {
            diagnostics += Diagnostic.ExpectedFunction(term.range)
            C.Term.Hole(C.Type.Hole)
          }
          else                        -> {
            if (resource.params.size != term.args.size) {
              diagnostics += Diagnostic.MismatchedArity(resource.params.size, term.args.size, term.range.end..term.range.end)
            }
            val args = term.args.mapIndexed { index, arg ->
              elaborateTerm(env, arg, resource.params.getOrNull(index)?.second)
            }
            C.Term.Run(resource.name, args, resource.result)
          }
        }

      term is S.Term.Command &&
      expected is C.Type.Int?     ->
        C.Term.Command(term.value, C.Type.End)

      term is S.Term.Hole         ->
        C.Term.Hole(expected ?: C.Type.Hole)

      expected == null            -> throw IllegalArgumentException()

      else                        -> {
        val actual = elaborateTerm(env, term)
        if (!(actual.type isSubtypeOf expected)) {
          diagnostics += Diagnostic.NotConvertible(expected, actual.type, term.range)
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
      hover(it.type, term.range)
    }
  }

  private fun hover(
    type: C.Type,
    range: Range,
  ) {
    if (hover == null && position != null && position in range) {
      hover = type
    }
  }

  private infix fun C.Type.isSubtypeOf(
    type2: C.Type,
  ): Boolean {
    val type1 = this
    return when {
      type1 is C.Type.End &&
      type2 is C.Type.End      -> true

      type1 is C.Type.Bool &&
      type2 is C.Type.Bool     -> true

      type1 is C.Type.Byte &&
      type2 is C.Type.Byte     -> true

      type1 is C.Type.Short &&
      type2 is C.Type.Short    -> true

      type1 is C.Type.Int &&
      type2 is C.Type.Int      -> true

      type1 is C.Type.Long &&
      type2 is C.Type.Long     -> true

      type1 is C.Type.Float &&
      type2 is C.Type.Float    -> true

      type1 is C.Type.Double &&
      type2 is C.Type.Double   -> true

      type1 is C.Type.String &&
      type2 is C.Type.String   -> true

      type1 is C.Type.List &&
      type2 is C.Type.List     -> type1.element isSubtypeOf type2.element

      type1 is C.Type.Compound &&
      type2 is C.Type.Compound -> type1.elements.size == type2.elements.size &&
                                  type1.elements.all { (key1, element1) ->
                                    when (val element2 = type2.elements[key1]) {
                                      null -> false
                                      else -> element1 isSubtypeOf element2
                                    }
                                  }

      type1 is C.Type.Box &&
      type2 is C.Type.Box      -> type1.element isSubtypeOf type2.element

      type1 is C.Type.Hole     -> true
      type2 is C.Type.Hole     -> true

      else                     -> false
    }
  }

  private class Env private constructor(
    private val resources: Map<Location, C.Resource>,
    private val _entries: MutableList<Entry>,
  ) {
    val entries: List<Entry> get() = _entries

    operator fun get(name: String): Entry? =
      _entries.lastOrNull { it.name == name }

    fun findResource(
      expected: Location,
    ): C.Resource? =
      resources.entries.find { (actual, _) ->
        expected.parts.size <= actual.parts.size &&
        (expected.parts.asReversed() zip actual.parts.asReversed()).all { it.first == it.second }
      }?.value

    fun bind(
      name: String,
      type: C.Type,
    ) {
      _entries += Entry(name, false, type)
    }

    inline fun <R> binding(
      name: String,
      type: C.Type,
      action: () -> R,
    ): R {
      _entries += Entry(name, false, type)
      val result = action()
      _entries.removeLast()
      return result
    }

    fun copy(): Env =
      Env(
        resources,
        _entries
          .map { it.copy() }
          .toMutableList(),
      )

    data class Entry(
      val name: String,
      var used: Boolean,
      val type: C.Type,
    )

    companion object {
      fun emptyEnv(
        resources: Map<Location, Core.Resource>,
      ): Env =
        Env(resources, mutableListOf())
    }
  }

  data class Dependency(
    val location: Location,
    val root: C.Root?,
    val range: Range?,
  )

  data class Result(
    val root: C.Root,
    val diagnostics: List<Diagnostic>,
    val completionItems: List<CompletionItem>?,
    val hover: C.Type?,
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
