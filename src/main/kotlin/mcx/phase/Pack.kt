package mcx.phase

import mcx.ast.Json
import mcx.ast.Location
import mcx.ast.Packed
import mcx.phase.Pack.Env.Companion.emptyEnv
import mcx.util.hash
import java.security.MessageDigest
import mcx.ast.Lifted as L
import mcx.ast.Packed as P

class Pack private constructor() {
  private val digest = MessageDigest.getInstance("SHA3-256")

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
    val path = packLocation(resource.name)
    return when (resource) {
      is L.Resource.JsonResource -> {
        val body = packJson(resource.body)
        P.Resource.JsonResource(resource.registry, path, body)
      }
      is L.Resource.Functions    -> with(emptyEnv()) {
        +"# ${resource.name}"
        resource.params.forEach { (name, type) ->
          bind(name, eraseType(type))
        }
        packTerm(resource.body)
        val resultType = eraseType(resource.result)
        resource.params.forEach {
          val paramType = eraseType(it.second)
          drop(paramType, resultType)
        }
        P.Resource.Functions(path, commands)
      }
    }
  }

  private fun Env.packTerm(
    term: L.Term,
  ) {
    when (term) {
      is L.Term.BoolOf     -> +"data modify storage $MCX_STORAGE ${eraseType(term.type).toStack()} append value ${if (term.value) 1 else 0}b"
      is L.Term.ByteOf     -> +"data modify storage $MCX_STORAGE ${eraseType(term.type).toStack()} append value ${term.value}b"
      is L.Term.ShortOf    -> +"data modify storage $MCX_STORAGE ${eraseType(term.type).toStack()} append value ${term.value}s"
      is L.Term.IntOf      -> +"data modify storage $MCX_STORAGE ${eraseType(term.type).toStack()} append value ${term.value}"
      is L.Term.LongOf     -> +"data modify storage $MCX_STORAGE ${eraseType(term.type).toStack()} append value ${term.value}l"
      is L.Term.FloatOf    -> +"data modify storage $MCX_STORAGE ${eraseType(term.type).toStack()} append value ${term.value}f"
      is L.Term.DoubleOf   -> +"data modify storage $MCX_STORAGE ${eraseType(term.type).toStack()} append value ${term.value}"
      is L.Term.StringOf   -> +"data modify storage $MCX_STORAGE ${eraseType(term.type).toStack()} append value ${term.value}" // TODO: quote if necessary
      is L.Term.ListOf     -> +"# $term" // TODO
      is L.Term.CompoundOf -> +"# $term" // TODO
      is L.Term.BoxOf      -> +"# $term" // TODO
      is L.Term.If         -> {
        packTerm(term.condition)
        +"# then: ${term.thenName}" // TODO
        +"# else: ${term.elseName}" // TODO
      }
      is L.Term.Let        -> {
        val initType = eraseType(term.init.type)
        val bodyType = eraseType(term.body.type)
        packTerm(term.init)
        binding(term.name, initType) {
          packTerm(term.body)
        }
        drop(initType, bodyType)
      }
      is L.Term.Var        -> {
        val type = eraseType(term.type)
        val stack = type.toStack()
        val index = this[term.name, type]
        +"data modify storage $MCX_STORAGE $stack append from storage $MCX_STORAGE $stack[$index]"
      }
      is L.Term.Run        -> {
        term.args.forEach {
          packTerm(it)
        }
        +"function ${packLocation(term.name)}"
      }
      is L.Term.Command    -> +term.value
    }
  }

  private fun Env.drop(
    drop: P.Type,
    keep: P.Type,
  ) {
    when (drop) {
      P.Type.END -> Unit
      keep       -> +"data remove storage $MCX_STORAGE ${drop.toStack()}[-2]"
      else       -> +"data remove storage $MCX_STORAGE ${drop.toStack()}[-1]"
    }
  }

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

  private fun P.Type.toStack(): String =
    when (this) {
      P.Type.END      -> error("unexpected: end")
      P.Type.BYTE     -> "byte"
      P.Type.SHORT    -> "short"
      P.Type.INT      -> "int"
      P.Type.LONG     -> "long"
      P.Type.FLOAT    -> "float"
      P.Type.DOUBLE   -> "double"
      P.Type.STRING   -> "string"
      P.Type.LIST     -> "list"
      P.Type.COMPOUND -> "compound"
    }

  private fun packLocation(
    name: Location,
  ): String =
    hash(digest, name.toString())

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
    private val _commands: MutableList<String>,
  ) {
    val commands: List<String> get() = _commands
    private val entries: Map<P.Type, MutableList<String>> =
      P.Type
        .values()
        .associateWith { mutableListOf() }

    operator fun String.unaryPlus() {
      _commands += this
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
    private const val MCX_STORAGE: String = "mcx:"

    operator fun invoke(
      config: Config,
      module: L.Module,
    ): Packed.Module {
      return Pack().packModule(module)
    }
  }
}
