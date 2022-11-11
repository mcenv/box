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
  private var hover: C.Type0? = null

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
    val resources = hashMapOf<String, C.Resource0>().also { resources ->
      dependencies.forEach { dependency ->
        when (dependency.root) {
          null -> diagnostics += Diagnostic.ModuleNotFound(
            dependency.location,
            dependency.range!!,
          )
          else -> dependency.root.resources.forEach { resource ->
            if (resource !is C.Resource0.Hole) {
              resources[resource.name] = resource // TODO: handle name duplication
            }
          }
        }
      }
    }
    if (position != null) {
      resourceCompletionItems += resources.map {
        CompletionItem(it.key).apply {
          kind = when (it.value) {
            is C.Resource0.JsonResource -> CompletionItemKind.Struct
            is C.Resource0.Functions    -> CompletionItemKind.Function
            else                        -> error("unexpected: hole")
          }
        }
      }
    }
    return C.Root(
      root.module,
      root.resources.map {
        elaborateResource0(
          resources,
          root.module,
          it,
        )
      },
    )
  }

  private fun elaborateResource0(
    resources: Map<String, C.Resource0>,
    module: Location,
    resource: S.Resource0,
  ): C.Resource0 {
    return when (resource) {
      is S.Resource0.JsonResource -> {
        val annotations = resource.annotations.map {
          elaborateAnnotation(
            it,
          )
        }
        C.Resource0
          .JsonResource(
            annotations,
            resource.registry,
            module,
            resource.name,
          )
          .also {
            if (!signature) {
              val env = emptyEnv(resources)
              val body = elaborateTerm0(
                env,
                resource.body,
                /* TODO */
              )
              it.body = body
            }
          }
      }
      is S.Resource0.Functions    -> {
        val annotations = resource.annotations.map {
          elaborateAnnotation(
            it,
          )
        }
        val env = emptyEnv(resources)
        val params = resource.params.map {
          val type = elaborateType0(it.second)
          env.bind(
            it.first,
            type,
          )
          it.first to type
        }
        val result = elaborateType0(resource.result)
        C.Resource0
          .Functions(
            annotations,
            module,
            resource.name,
            params,
            result,
          )
          .also {
            if (!signature) {
              val body = elaborateTerm0(
                env,
                resource.body,
                result,
              )
              it.body = body
            }
          }
      }
      is S.Resource0.Hole         -> C.Resource0.Hole
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

  private fun elaborateType0(
    type: S.Type0,
  ): C.Type0 {
    return when (type) {
      is S.Type0.End      -> C.Type0.End
      is S.Type0.Bool     -> C.Type0.Bool
      is S.Type0.Byte     -> C.Type0.Byte
      is S.Type0.Short    -> C.Type0.Short
      is S.Type0.Int      -> C.Type0.Int
      is S.Type0.Long     -> C.Type0.Long
      is S.Type0.Float    -> C.Type0.Float
      is S.Type0.Double   -> C.Type0.Double
      is S.Type0.String   -> C.Type0.String
      is S.Type0.List     -> C.Type0.List(elaborateType0(type.element))
      is S.Type0.Compound -> C.Type0.Compound(type.elements.mapValues { elaborateType0(it.value) })
      is S.Type0.Box      -> C.Type0.Box(elaborateType0(type.element))
      is S.Type0.Hole     -> C.Type0.Hole
    }
  }

  private fun elaborateTerm0(
    env: Env,
    term: S.Term0,
    expected: C.Type0? = null,
  ): C.Term0 {
    return when {
      term is S.Term0.BoolOf &&
      expected is C.Type0.Bool?   -> C.Term0.BoolOf(term.value)

      term is S.Term0.ByteOf &&
      expected is C.Type0.Byte?   -> C.Term0.ByteOf(term.value)

      term is S.Term0.ShortOf &&
      expected is C.Type0.Short?  -> C.Term0.ShortOf(term.value)

      term is S.Term0.IntOf &&
      expected is C.Type0.Int?    -> C.Term0.IntOf(term.value)

      term is S.Term0.LongOf &&
      expected is C.Type0.Long?   -> C.Term0.LongOf(term.value)

      term is S.Term0.FloatOf &&
      expected is C.Type0.Float?  -> C.Term0.FloatOf(term.value)

      term is S.Term0.DoubleOf &&
      expected is C.Type0.Double? -> C.Term0.DoubleOf(term.value)

      term is S.Term0.StringOf &&
      expected is C.Type0.String? -> C.Term0.StringOf(term.value)

      term is S.Term0.ListOf &&
      term.values.isEmpty()       -> C.Term0.ListOf(
        emptyList(),
        C.Type0.List(C.Type0.End),
      )

      term is S.Term0.ListOf &&
      expected is C.Type0.List?   -> {
        val head = elaborateTerm0(
          env,
          term.values.first(),
          expected?.element,
        )
        val element =
          expected?.element
          ?: head.type
        val tail =
          term.values
            .drop(1)
            .map { value ->
              elaborateTerm0(
                env,
                value,
                element
              )
            }
        C.Term0.ListOf(
          listOf(head) + tail,
          C.Type0.List(element),
        )
      }

      term is S.Term0.CompoundOf &&
      expected == null             -> {
        val values = term.values.associate { (key, value) ->
          @Suppress("NAME_SHADOWING")
          val value = elaborateTerm0(
            env,
            value,
          )
          hover(
            value.type,
            key.range,
          )
          key.value to value
        }
        C.Term0.CompoundOf(
          values,
          C.Type0.Compound(values.mapValues { it.value.type }),
        )
      }

      term is S.Term0.CompoundOf &&
      expected is C.Type0.Compound -> {
        val values = mutableMapOf<String, C.Term0>()
        term.values.forEach { (key, value) ->
          when (val element = expected.elements[key.value]) {
            null -> {
              diagnostics += Diagnostic.ExtraKey(
                key.value,
                key.range,
              )
              @Suppress("NAME_SHADOWING")
              val value = elaborateTerm0(
                env,
                value,
              )
              hover(
                value.type,
                key.range,
              )
              values[key.value] = value
            }
            else -> {
              hover(
                element,
                key.range,
              )
              values[key.value] = elaborateTerm0(
                env,
                value,
                element,
              )
            }
          }
        }
        (expected.elements.keys - term.values
          .map { it.first.value }
          .toSet()).forEach { key ->
          diagnostics += Diagnostic.KeyNotFound(
            key,
            term.range,
          )
        }
        C.Term0.CompoundOf(
          values,
          C.Type0.Compound(values.mapValues { it.value.type }),
        )
      }

      term is S.Term0.BoxOf &&
      expected is C.Type0.Box?     -> {
        val value = elaborateTerm0(
          env,
          term.value,
          expected?.element,
        )
        C.Term0.BoxOf(
          value,
          C.Type0.Box(value.type),
        )
      }

      term is S.Term0.If           -> {
        val condition = elaborateTerm0(
          env,
          term.condition,
          C.Type0.Bool,
        )
        val elseEnv = env.copy()
        val thenClause = elaborateTerm0(
          env,
          term.thenClause,
          expected,
        )
        val elseClause = elaborateTerm0(
          elseEnv,
          term.elseClause,
          expected
          ?: thenClause.type,
        )
        C.Term0.If(
          condition,
          thenClause,
          elseClause,
        )
      }

      term is S.Term0.Let          -> {
        val init = elaborateTerm0(
          env,
          term.init,
        )
        hover(
          init.type,
          term.name.range,
        )
        val body = env.binding(
          term.name.value,
          init.type,
        ) {
          elaborateTerm0(
            env,
            term.body,
            expected,
          )
        }
        C.Term0.Let(
          term.name.value,
          init,
          body,
        )
      }

      term is S.Term0.Var &&
      expected == null             -> when (val entry = env[term.name]) {
        null -> {
          diagnostics += Diagnostic.VarNotFound(
            term.name,
            term.range,
          )
          C.Term0.Hole(C.Type0.Hole)
        }
        else -> if (entry.used) {
          diagnostics += Diagnostic.VarAlreadyUsed(
            term.name,
            term.range,
          )
          C.Term0.Hole(entry.type)
        } else {
          entry.used = true
          C.Term0.Var(
            term.name,
            entry.type,
          )
        }
      }

      term is S.Term0.Run &&
      expected == null            -> when (val resource = env.resources[term.name]) {
        null                         -> {
          diagnostics += Diagnostic.ResourceNotFound(
            term.name,
            term.range,
          )
          C.Term0.Hole(C.Type0.Hole)
        }
        !is Core.Resource0.Functions -> {
          diagnostics += Diagnostic.ExpectedFunction(term.range)
          C.Term0.Hole(C.Type0.Hole)
        }
        else                         -> {
          if (resource.params.size != term.args.size) {
            diagnostics += Diagnostic.MismatchedArity(
              resource.params.size,
              term.args.size,
              term.range.end..term.range.end,
            )
          }
          val args = term.args.mapIndexed { index, arg ->
            elaborateTerm0(
              env,
              arg,
              resource.params.getOrNull(index)?.second,
            )
          }
          C.Term0.Run(
            resource.module,
            term.name,
            args,
            resource.result,
          )
        }
      }

      term is S.Term0.Command &&
      expected is C.Type0.Int?    -> C.Term0.Command(term.value)

      term is S.Term0.Hole        -> C.Term0.Hole(
        expected
        ?: C.Type0.Hole
      )

      expected == null            -> throw IllegalArgumentException()

      else                        -> {
        val actual = elaborateTerm0(
          env,
          term,
        )
        if (!(actual.type isSubtypeOf expected)) {
          diagnostics += Diagnostic.NotConvertible(
            expected,
            actual.type,
            term.range,
          )
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
                val type = prettyType0(entry.type)
                documentation = forRight(highlight(type))
                labelDetails = CompletionItemLabelDetails().apply {
                  detail = " : $type"
                }
                kind = CompletionItemKind.Variable
              }
            }
        }
      }
      hover(
        it.type,
        term.range,
      )
    }
  }

  private fun hover(
    type: C.Type0,
    range: Range,
  ) {
    if (hover == null && position != null && position in range) {
      hover = type
    }
  }

  private infix fun C.Type0.isSubtypeOf(
    type2: C.Type0,
  ): Boolean {
    val type1 = this
    return when {
      type1 is C.Type0.End &&
      type2 is C.Type0.End      -> true

      type1 is C.Type0.Bool &&
      type2 is C.Type0.Bool     -> true

      type1 is C.Type0.Byte &&
      type2 is C.Type0.Byte     -> true

      type1 is C.Type0.Short &&
      type2 is C.Type0.Short    -> true

      type1 is C.Type0.Int &&
      type2 is C.Type0.Int      -> true

      type1 is C.Type0.Long &&
      type2 is C.Type0.Long     -> true

      type1 is C.Type0.Float &&
      type2 is C.Type0.Float    -> true

      type1 is C.Type0.Double &&
      type2 is C.Type0.Double   -> true

      type1 is C.Type0.String &&
      type2 is C.Type0.String   -> true

      type1 is C.Type0.List &&
      type2 is C.Type0.List     -> type1.element isSubtypeOf type2.element

      type1 is C.Type0.Compound &&
      type2 is C.Type0.Compound -> type1.elements.size == type2.elements.size &&
                                   type1.elements.all { (key1, element1) ->
                                     when (val element2 = type2.elements[key1]) {
                                       null -> false
                                       else -> element1 isSubtypeOf element2
                                     }
                                   }

      type1 is C.Type0.Box &&
      type2 is C.Type0.Box      -> type1.element isSubtypeOf type2.element

      type1 is C.Type0.Hole     -> true
      type2 is C.Type0.Hole     -> true

      else                      -> false
    }
  }

  private class Env private constructor(
    val resources: Map<String, C.Resource0>,
    private val _entries: MutableList<Entry>,
  ) {
    val entries: List<Entry> get() = _entries

    operator fun get(name: String): Entry? =
      _entries.lastOrNull { it.name == name }

    fun bind(
      name: String,
      type: C.Type0,
    ) {
      _entries += Entry(
        name,
        false,
        type,
      )
    }

    inline fun <R> binding(
      name: String,
      type: C.Type0,
      action: () -> R,
    ): R {
      _entries += Entry(
        name,
        false,
        type,
      )
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
      val type: C.Type0,
    )

    companion object {
      fun emptyEnv(resources: Map<String, Core.Resource0>): Env =
        Env(
          resources,
          mutableListOf(),
        )
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
    val hover: C.Type0?,
  )

  companion object {
    operator fun invoke(
      config: Config,
      dependencies: List<Dependency>,
      input: Parse.Result,
      signature: Boolean,
      position: Position? = null,
    ): Result =
      Elaborate(
        dependencies,
        signature,
        position,
      ).elaborateResult(input)
  }
}
