package mcx.phase

import mcx.phase.Pack.Env.Companion.emptyEnv
import mcx.ast.Core as C
import mcx.ast.Packed as P

class Pack private constructor() {
  private fun packRoot(
    root: C.Root,
  ): P.Root {
    return P.Root(
      root.module,
      root.resources.map {
        packResource(it)
      }
    )
  }

  private fun packResource(
    resource: C.Resource0,
  ): P.Resource {
    return when (resource) {
      is C.Resource0.Function -> {
        val env = emptyEnv()
        resource.params.forEach { (name, type) ->
          env.bind(
            name,
            eraseType(type),
          )
        }
        packTerm(
          env,
          resource.body,
        )
        val resultType = eraseType(resource.result)
        resource.params.forEach {
          val paramType = eraseType(it.second)
          env += P.Instruction.Drop(
            if (resultType == paramType) -2 else -1,
            paramType,
          )
        }
        P.Resource.Function(
          resource.module,
          resource.name,
          env.instructions,
        )
      }
      is C.Resource0.Hole     -> unexpectedHole()
    }
  }

  private fun packTerm(
    env: Env,
    term: C.Term0,
  ) {
    when (term) {
      is C.Term0.IntOf    -> env += P.Instruction.Push(
        P.Tag.IntOf(term.value),
        P.Type.INT,
      )
      is C.Term0.StringOf -> env += P.Instruction.Push(
        P.Tag.StringOf(term.value),
        P.Type.STRING,
      )
      is C.Term0.RefOf    -> env += P.Instruction.Debug("$term")
      is C.Term0.Let      -> {
        val initType = eraseType(term.init.type)
        val bodyType = eraseType(term.body.type)
        packTerm(
          env,
          term.init,
        )
        env.binding(
          term.name,
          initType,
        ) {
          packTerm(
            env,
            term.body,
          )
        }
        env += P.Instruction.Drop(
          if (initType == bodyType) -2 else -1,
          initType,
        )
      }
      is C.Term0.Var      -> {
        val type = eraseType(term.type)
        env += P.Instruction.Copy(
          env[term.name, type],
          type,
        )
      }
      is C.Term0.Run      -> {
        term.args.forEach {
          packTerm(
            env,
            it,
          )
        }
        env += P.Instruction.Run(
          term.module,
          term.name,
        )
      }
      is C.Term0.Hole     -> unexpectedHole()
    }
  }

  private class Env private constructor(
    private val _instructions: MutableList<P.Instruction>,
  ) {
    val instructions: List<P.Instruction> get() = _instructions
    private val entries: Map<P.Type, MutableList<String>> =
      P.Type
        .values()
        .associateWith { mutableListOf() }

    operator fun plusAssign(instruction: P.Instruction) {
      _instructions += instruction
    }

    fun bind(
      name: String,
      type: P.Type,
    ) {
      entries[type]!! += name
    }

    inline fun binding(
      name: String,
      type: P.Type,
      action: () -> Unit,
    ) {
      val entry = entries[type]!!
      entry += name
      action()
      entry.removeLast()
    }

    operator fun get(
      name: String,
      type: P.Type,
    ): Int {
      val entry = entries[type]!!
      return entry.indexOfLast { it == name } - entry.size
    }

    companion object {
      fun emptyEnv(): Env =
        Env(mutableListOf())
    }
  }

  companion object {
    private fun eraseType(
      type: C.Type0,
    ): P.Type {
      return when (type) {
        is C.Type0.Int    -> P.Type.INT
        is C.Type0.String -> P.Type.STRING
        is C.Type0.Ref    -> P.Type.INT
        is C.Type0.Hole   -> unexpectedHole()
      }
    }

    private fun unexpectedHole(): Nothing =
      error("unexpected: hole")

    operator fun invoke(
      root: C.Root,
    ): P.Root {
      return Pack().packRoot(root)
    }
  }
}