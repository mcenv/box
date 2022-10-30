package mcx.phase

import mcx.phase.Elaborate.Env.Companion.envOf
import mcx.ast.Core as C
import mcx.ast.Surface as S

class Elaborate private constructor(
  private val context: Context,
) {
  private fun elaborateRoot(
    root: S.Root,
  ): C.Root {
    return C.Root(root.resources.map {
      elaborateResource0(it)
    })
  }

  private fun elaborateResource0(
    resource: S.Resource0,
  ): C.Resource0 {
    return when (resource) {
      is S.Resource0.Function -> {
        val params = resource.params.map { (name, type) ->
          name to elaborateType0(type)
        }
        val result = elaborateType0(resource.result)
        val body = elaborateTerm0(
          envOf(params),
          resource.body,
          result,
        )
        C.Resource0.Function(
          resource.name,
          params,
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
      term is S.Term0.IntOf && expected is C.Type0.Int?       -> C.Term0.IntOf(term.value)
      term is S.Term0.StringOf && expected is C.Type0.String? -> C.Term0.StringOf(term.value)
      term is S.Term0.RefOf && expected is C.Type0.Ref?       -> C.Term0.RefOf(
        elaborateTerm0(
          env,
          term.value,
          expected?.element,
        )
      )
      term is S.Term0.Let                                     -> {
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
      term is S.Term0.Var && expected == null -> {
        when (val type = env[term.name]) {
          null -> {
            context += Diagnostic.NotFound(
              term.name,
              term.range,
            )
            C.Term0.Hole(C.Type0.Hole)
          }
          else -> C.Term0.Var(
            term.name,
            type,
          )
        }
      }
      term is S.Term0.Hole                    -> C.Term0.Hole(
        expected
        ?: C.Type0.Hole
      )
      expected == null                        -> throw IllegalArgumentException()
      else                                    -> {
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
          context += Diagnostic.NotConvertible(
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
      type1 is C.Type0.Int && type2 is C.Type0.Int       -> true
      type1 is C.Type0.String && type2 is C.Type0.String -> true
      type1 is C.Type0.Ref && type2 is C.Type0.Ref       -> convType(
        type1.element,
        type2.element,
      )
      else                                               -> false
    }
  }

  private class Env private constructor(
    private val vars: MutableList<Pair<String, C.Type0>>,
  ) {
    operator fun get(name: String): C.Type0? =
      vars.lastOrNull { it.first == name }?.second

    inline fun <R> binding(
      name: String,
      type: C.Type0,
      action: () -> R,
    ): R {
      vars += name to type
      val result = action()
      vars.removeLast()
      return result
    }

    companion object {
      fun emptyEnv(): Env =
        Env(mutableListOf())

      fun envOf(vars: List<Pair<String, C.Type0>>): Env =
        Env(vars.toMutableList())
    }
  }

  companion object : Phase<S.Root, C.Root> {
    override fun invoke(
      context: Context,
      input: S.Root,
    ): C.Root {
      return Elaborate(context).elaborateRoot(input)
    }
  }
}
