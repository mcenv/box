package mcx.phase

import mcx.ast.Json
import mcx.ast.Packed
import mcx.phase.Pack.Env.Companion.emptyEnv
import mcx.ast.Lifted as L
import mcx.ast.Packed as P

class Pack private constructor() {
  private fun packModule(
    module: L.Module,
  ): Packed.Module {
    val resources = module.resources.map {
      packResource(it)
    }
    return P.Module(module.name, resources)
  }

  private fun packResource(
    resource: L.Resource,
  ): P.Resource {
    return when (resource) {
      is L.Resource.JsonResource -> {
        val body = packJson(resource.body)
        P.Resource.JsonResource(resource.registry, resource.name, body)
      }
      is L.Resource.Functions    -> {
        val env = emptyEnv()
        env += P.Instruction.Debug(resource.name.toString())
        resource.params.forEach { (name, type) ->
          env.bind(name, eraseType(type))
        }
        packTerm(env, resource.body)
        val resultType = eraseType(resource.result)
        resource.params.forEach {
          val paramType = eraseType(it.second)
          drop(env, paramType, resultType)
        }
        P.Resource.Functions(resource.name, env.instructions)
      }
    }
  }

  private fun packTerm(
    env: Env,
    term: L.Term,
  ) {
    when (term) {
      is L.Term.BoolOf     -> env += P.Instruction.Push(P.Tag.ByteOf(if (term.value) 1 else 0))
      is L.Term.ByteOf     -> env += P.Instruction.Push(P.Tag.ByteOf(term.value))
      is L.Term.ShortOf    -> env += P.Instruction.Push(P.Tag.ShortOf(term.value))
      is L.Term.IntOf      -> env += P.Instruction.Push(P.Tag.IntOf(term.value))
      is L.Term.LongOf     -> env += P.Instruction.Push(P.Tag.LongOf(term.value))
      is L.Term.FloatOf    -> env += P.Instruction.Push(P.Tag.FloatOf(term.value))
      is L.Term.DoubleOf   -> env += P.Instruction.Push(P.Tag.DoubleOf(term.value))
      is L.Term.StringOf   -> env += P.Instruction.Push(P.Tag.StringOf(term.value))
      is L.Term.ListOf     -> env += P.Instruction.Debug("$term") // TODO
      is L.Term.CompoundOf -> env += P.Instruction.Debug("$term") // TODO
      is L.Term.BoxOf      -> env += P.Instruction.Debug("$term") // TODO
      is L.Term.If         -> {
        packTerm(env, term.condition)
        env += P.Instruction.Debug("then: ${term.thenName}") // TODO
        env += P.Instruction.Debug("else: ${term.elseName}") // TODO
      }
      is L.Term.Let        -> {
        val initType = eraseType(term.init.type)
        val bodyType = eraseType(term.body.type)
        packTerm(env, term.init)
        env.binding(term.name, initType) {
          packTerm(env, term.body)
        }
        drop(env, initType, bodyType)
      }
      is L.Term.Var        -> {
        val type = eraseType(term.type)
        env += P.Instruction.Copy(env[term.name, type], type)
      }
      is L.Term.Run        -> {
        term.args.forEach {
          packTerm(env, it)
        }
        env += P.Instruction.Run(term.name)
      }
      is L.Term.Command    -> env += P.Instruction.Command(term.value)
    }
  }

  private fun drop(
    env: Env,
    drop: P.Type,
    keep: P.Type,
  ) {
    when (drop) {
      P.Type.END -> Unit
      keep       -> env += P.Instruction.Drop(-2, drop)
      else       -> env += P.Instruction.Drop(-2, drop)
    }
  }

  private fun packJson(
    term: L.Term,
  ): Json {
    return when (term) {
      is L.Term.BoolOf     -> Json.BoolOf(term.value)
      is L.Term.ByteOf     -> Json.ByteOf(term.value)
      is L.Term.ShortOf    -> Json.ShortOf(term.value)
      is L.Term.IntOf      -> Json.IntOf(term.value)
      is L.Term.LongOf     -> Json.LongOf(term.value)
      is L.Term.FloatOf    -> Json.FloatOf(term.value)
      is L.Term.DoubleOf   -> Json.DoubleOf(term.value)
      is L.Term.StringOf   -> Json.StringOf(term.value)
      is L.Term.ListOf     -> Json.ArrayOf(term.values.map { packJson(it) })
      is L.Term.CompoundOf -> Json.ObjectOf(term.values.mapValues { packJson(it.value) })
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
      type: L.Type,
    ): P.Type {
      return when (type) {
        is L.Type.End      -> P.Type.END
        is L.Type.Bool     -> P.Type.BYTE
        is L.Type.Byte     -> P.Type.BYTE
        is L.Type.Short    -> P.Type.SHORT
        is L.Type.Int      -> P.Type.INT
        is L.Type.Long     -> P.Type.LONG
        is L.Type.Float    -> P.Type.FLOAT
        is L.Type.Double   -> P.Type.DOUBLE
        is L.Type.String   -> P.Type.STRING
        is L.Type.List     -> P.Type.LIST
        is L.Type.Compound -> P.Type.COMPOUND
        is L.Type.Box      -> P.Type.INT
      }
    }

    operator fun invoke(
      config: Config,
      module: L.Module,
    ): Packed.Module {
      return Pack().packModule(module)
    }
  }
}
