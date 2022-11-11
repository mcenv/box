package mcx.phase

import mcx.ast.Json
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
    resource: C.Resource,
  ): P.Resource {
    return when (resource) {
      is C.Resource.JsonResource -> {
        val body = packJson(resource.body)
        P.Resource.JsonResource(
          resource.registry,
          resource.module,
          resource.name,
          body,
        )
      }
      is C.Resource.Functions    -> {
        val env = emptyEnv()
        env += P.Instruction.Debug("${resource.module}/${resource.name}")
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
          drop(
            env,
            paramType,
            resultType,
          )
        }
        P.Resource.Functions(
          resource.module,
          resource.name,
          env.instructions,
        )
      }
      is C.Resource.Hole         -> unexpectedHole()
    }
  }

  private fun packTerm(
    env: Env,
    term: C.Term,
  ) {
    when (term) {
      is C.Term.BoolOf     -> env += P.Instruction.Push(P.Tag.ByteOf(if (term.value) 1 else 0))
      is C.Term.ByteOf     -> env += P.Instruction.Push(P.Tag.ByteOf(term.value))
      is C.Term.ShortOf    -> env += P.Instruction.Push(P.Tag.ShortOf(term.value))
      is C.Term.IntOf      -> env += P.Instruction.Push(P.Tag.IntOf(term.value))
      is C.Term.LongOf     -> env += P.Instruction.Push(P.Tag.LongOf(term.value))
      is C.Term.FloatOf    -> env += P.Instruction.Push(P.Tag.FloatOf(term.value))
      is C.Term.DoubleOf   -> env += P.Instruction.Push(P.Tag.DoubleOf(term.value))
      is C.Term.StringOf   -> env += P.Instruction.Push(P.Tag.StringOf(term.value))
      is C.Term.ListOf     -> env += P.Instruction.Debug("$term") // TODO
      is C.Term.CompoundOf -> env += P.Instruction.Debug("$term") // TODO
      is C.Term.BoxOf      -> env += P.Instruction.Debug("$term") // TODO
      is C.Term.If         -> env += P.Instruction.Debug("$term") // TODO
      is C.Term.Let        -> {
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
        drop(
          env,
          initType,
          bodyType,
        )
      }
      is C.Term.Var        -> {
        val type = eraseType(term.type)
        env += P.Instruction.Copy(
          env[term.name, type],
          type,
        )
      }
      is C.Term.Run        -> {
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
      is C.Term.Command    -> env += P.Instruction.Command(term.value)
      is C.Term.Hole       -> unexpectedHole()
    }
  }

  private fun drop(
    env: Env,
    drop: P.Type,
    keep: P.Type,
  ) {
    when (drop) {
      P.Type.END -> Unit
      keep       -> env += P.Instruction.Drop(
        -2,
        drop,
      )
      else       -> env += P.Instruction.Drop(
        -2,
        drop,
      )
    }
  }

  private fun packJson(
    term: C.Term,
  ): Json {
    return when (term) {
      is C.Term.BoolOf     -> Json.BoolOf(term.value)
      is C.Term.ByteOf     -> Json.ByteOf(term.value)
      is C.Term.ShortOf    -> Json.ShortOf(term.value)
      is C.Term.IntOf      -> Json.IntOf(term.value)
      is C.Term.LongOf     -> Json.LongOf(term.value)
      is C.Term.FloatOf    -> Json.FloatOf(term.value)
      is C.Term.DoubleOf   -> Json.DoubleOf(term.value)
      is C.Term.StringOf   -> Json.StringOf(term.value)
      is C.Term.ListOf     -> Json.ArrayOf(term.values.map { packJson(it) })
      is C.Term.CompoundOf -> Json.ObjectOf(term.values.mapValues { packJson(it.value) })
      else                 -> TODO()
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
      type: C.Type,
    ): P.Type {
      return when (type) {
        is C.Type.End      -> P.Type.END
        is C.Type.Bool     -> P.Type.BYTE
        is C.Type.Byte     -> P.Type.BYTE
        is C.Type.Short    -> P.Type.SHORT
        is C.Type.Int      -> P.Type.INT
        is C.Type.Long     -> P.Type.LONG
        is C.Type.Float    -> P.Type.FLOAT
        is C.Type.Double   -> P.Type.DOUBLE
        is C.Type.String   -> P.Type.STRING
        is C.Type.List     -> P.Type.LIST
        is C.Type.Compound -> P.Type.COMPOUND
        is C.Type.Box      -> P.Type.INT
        is C.Type.Hole     -> unexpectedHole()
      }
    }

    private fun unexpectedHole(): Nothing =
      error("unexpected: hole")

    operator fun invoke(
      config: Config,
      root: C.Root,
    ): P.Root {
      return Pack().packRoot(root)
    }
  }
}
