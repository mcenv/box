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
    resource: C.Resource0,
  ): P.Resource {
    return when (resource) {
      is C.Resource0.JsonResource -> {
        val body = packJson(resource.body)
        P.Resource.JsonResource(
          resource.registry,
          resource.module,
          resource.name,
          body,
        )
      }
      is C.Resource0.Function     -> {
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
          drop(
            env,
            paramType,
            resultType,
          )
        }
        P.Resource.Function(
          resource.module,
          resource.name,
          env.instructions,
        )
      }
      is C.Resource0.Hole         -> unexpectedHole()
    }
  }

  private fun packTerm(
    env: Env,
    term: C.Term0,
  ) {
    when (term) {
      is C.Term0.BoolOf     -> env += P.Instruction.Push(P.Tag.ByteOf(if (term.value) 1 else 0))
      is C.Term0.ByteOf     -> env += P.Instruction.Push(P.Tag.ByteOf(term.value))
      is C.Term0.ShortOf    -> env += P.Instruction.Push(P.Tag.ShortOf(term.value))
      is C.Term0.IntOf      -> env += P.Instruction.Push(P.Tag.IntOf(term.value))
      is C.Term0.LongOf     -> env += P.Instruction.Push(P.Tag.LongOf(term.value))
      is C.Term0.FloatOf    -> env += P.Instruction.Push(P.Tag.FloatOf(term.value))
      is C.Term0.DoubleOf   -> env += P.Instruction.Push(P.Tag.DoubleOf(term.value))
      is C.Term0.StringOf   -> env += P.Instruction.Push(P.Tag.StringOf(term.value))
      is C.Term0.ListOf     -> env += P.Instruction.Debug("$term") // TODO
      is C.Term0.CompoundOf -> env += P.Instruction.Debug("$term") // TODO
      is C.Term0.BoxOf      -> env += P.Instruction.Debug("$term") // TODO
      is C.Term0.Let        -> {
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
      is C.Term0.Var        -> {
        val type = eraseType(term.type)
        env += P.Instruction.Copy(
          env[term.name, type],
          type,
        )
      }
      is C.Term0.Run        -> {
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
      is C.Term0.Command    -> env += P.Instruction.Command(term.value)
      is C.Term0.Hole       -> unexpectedHole()
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
    term: C.Term0,
  ): Json {
    return when (term) {
      is C.Term0.BoolOf     -> Json.BoolOf(term.value)
      is C.Term0.ByteOf     -> Json.ByteOf(term.value)
      is C.Term0.ShortOf    -> Json.ShortOf(term.value)
      is C.Term0.IntOf      -> Json.IntOf(term.value)
      is C.Term0.LongOf     -> Json.LongOf(term.value)
      is C.Term0.FloatOf    -> Json.FloatOf(term.value)
      is C.Term0.DoubleOf   -> Json.DoubleOf(term.value)
      is C.Term0.StringOf   -> Json.StringOf(term.value)
      is C.Term0.ListOf     -> Json.ArrayOf(term.values.map { packJson(it) })
      is C.Term0.CompoundOf -> Json.ObjectOf(term.values.mapValues { packJson(it.value) })
      else                  -> TODO()
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
        is C.Type0.End      -> P.Type.END
        is C.Type0.Bool     -> P.Type.BYTE
        is C.Type0.Byte     -> P.Type.BYTE
        is C.Type0.Short    -> P.Type.SHORT
        is C.Type0.Int      -> P.Type.INT
        is C.Type0.Long     -> P.Type.LONG
        is C.Type0.Float    -> P.Type.FLOAT
        is C.Type0.Double   -> P.Type.DOUBLE
        is C.Type0.String   -> P.Type.STRING
        is C.Type0.List     -> P.Type.LIST
        is C.Type0.Compound -> P.Type.COMPOUND
        is C.Type0.Box      -> P.Type.INT
        is C.Type0.Hole     -> unexpectedHole()
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
