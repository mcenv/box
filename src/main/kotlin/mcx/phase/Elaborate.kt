package mcx.phase

import mcx.ast.Core
import mcx.ast.Location
import mcx.phase.Elaborate.Env.Companion.emptyEnv
import mcx.ast.Core as C
import mcx.ast.Surface as S

class Elaborate private constructor(
  private val imports: List<Pair<S.Ranged<Location>, C.Root?>>,
) {
  private val diagnostics: MutableList<Diagnostic> = mutableListOf()

  private fun elaborateRoot(
    root: S.Root,
  ): C.Root {
    val resources = hashMapOf<String, C.Resource0>().also {
      imports.forEach { (location, import) ->
        when (import) {
          null -> diagnostics += Diagnostic.ModuleNotFound(
            location.value,
            location.range,
          )
          else -> import.resources.forEach { resource ->
            it[resource.name] = resource // TODO: handle name duplication
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
      is S.Resource0.Function -> {
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
        val body = elaborateTerm0(
          env,
          resource.body,
          result,
        )
        C.Resource0.Function(
          module,
          resource.name,
          params,
          result,
          body,
        )
      }
      is S.Resource0.Hole     -> C.Resource0.Hole
    }
  }

  private fun elaborateType0(
    type: S.Type0,
  ): C.Type0 {
    return when (type) {
      is S.Type0.Int    -> C.Type0.Int
      is S.Type0.String -> C.Type0.String
      is S.Type0.Ref    -> C.Type0.Ref(elaborateType0(type.element))
      is S.Type0.Hole   -> C.Type0.Hole
    }
  }

  private fun elaborateTerm0(
    env: Env,
    term: S.Term0,
    expected: C.Type0? = null,
  ): C.Term0 {
    return when {
      term is S.Term0.IntOf &&
      expected is C.Type0.Int?    -> C.Term0.IntOf(term.value)

      term is S.Term0.StringOf &&
      expected is C.Type0.String? -> C.Term0.StringOf(term.value)

      term is S.Term0.RefOf &&
      expected is C.Type0.Ref?    -> C.Term0.RefOf(
        elaborateTerm0(
          env,
          term.value,
          expected?.element,
        )
      )

      term is S.Term0.Let         -> {
        val init = elaborateTerm0(
          env,
          term.init,
        )
        val body = env.binding(
          term.name,
          init.type,
        ) {
          elaborateTerm0(
            env,
            term.body,
            expected,
          )
        }
        C.Term0.Let(
          term.name,
          init,
          body,
        )
      }

      term is S.Term0.Var &&
      expected == null            -> when (val entry = env[term.name]) {
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
        null                        -> {
          diagnostics += Diagnostic.ResourceNotFound(
            term.name,
            term.range,
          )
          C.Term0.Hole(C.Type0.Hole)
        }
        !is Core.Resource0.Function -> {
          diagnostics += Diagnostic.ExpectedFunction(term.range)
          C.Term0.Hole(C.Type0.Hole)
        }
        else                        -> {
          val args = (term.args zip resource.params).map { (arg, param) ->
            elaborateTerm0(
              env,
              arg,
              param.second,
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
        if (
          !convType(
            expected,
            actual.type,
          )
        ) {
          diagnostics += Diagnostic.NotConvertible(
            expected,
            actual.type,
            term.range,
          )
        }
        actual
      }
    }
  }

  private fun convType(
    type1: C.Type0,
    type2: C.Type0,
  ): Boolean {
    return when {
      type1 is C.Type0.Int &&
      type2 is C.Type0.Int    -> true

      type1 is C.Type0.String &&
      type2 is C.Type0.String -> true

      type1 is C.Type0.Ref &&
      type2 is C.Type0.Ref    -> convType(
        type1.element,
        type2.element,
      )

      else                    -> false
    }
  }

  private class Env private constructor(
    val resources: Map<String, C.Resource0>,
    private val entries: MutableList<Entry>,
  ) {
    operator fun get(name: String): Entry? =
      entries.lastOrNull { it.name == name }

    fun bind(
      name: String,
      type: C.Type0,
    ) {
      entries += Entry(
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
      entries += Entry(
        name,
        false,
        type,
      )
      val result = action()
      entries.removeLast()
      return result
    }

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

  data class Result(
    val root: C.Root,
    val diagnostics: List<Diagnostic>,
  )

  companion object {
    operator fun invoke(
      imports: List<Pair<S.Ranged<Location>, C.Root?>>,
      input: Parse.Result,
    ): Result =
      Elaborate(
        imports,
      ).run {
        Result(
          elaborateRoot(input.root),
          input.diagnostics + diagnostics,
        )
      }
  }
}
