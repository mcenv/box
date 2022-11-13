package mcx.phase

import mcx.ast.Json
import mcx.ast.Location
import mcx.ast.Packed
import mcx.phase.Pack.Env.Companion.emptyEnv
import mcx.util.quoted
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
    val path = packLocation(resource.name)
    return when (resource) {
      is L.Resource.JsonResource -> {
        val body = packJson(resource.body)
        P.Resource.JsonResource(resource.registry, path, body)
      }
      is L.Resource.Functions    -> with(emptyEnv()) {
        +"# ${resource.name}"
        resource.params.forEach { (name, type) ->
          eraseType(type).forEach {
            bind(name, it)
          }
        }
        packTerm(resource.body)
        val resultTypes = eraseType(resource.result)
        resource.params.forEach { (_, type) ->
          eraseType(type).forEach { paramType ->
            dropType(paramType, resultTypes)
          }
        }
        P.Resource.Functions(path, commands)
      }
    }
  }

  private fun Env.packTerm(
    term: L.Term,
  ) {
    when (term) {
      is L.Term.BoolOf     -> +"data modify storage $MCX_STORAGE ${P.Type.BYTE.stack} append value ${if (term.value) 1 else 0}b"
      is L.Term.ByteOf     -> +"data modify storage $MCX_STORAGE ${P.Type.BYTE.stack} append value ${term.value}b"
      is L.Term.ShortOf    -> +"data modify storage $MCX_STORAGE ${P.Type.SHORT.stack} append value ${term.value}s"
      is L.Term.IntOf      -> +"data modify storage $MCX_STORAGE ${P.Type.INT.stack} append value ${term.value}"
      is L.Term.LongOf     -> +"data modify storage $MCX_STORAGE ${P.Type.LONG.stack} append value ${term.value}l"
      is L.Term.FloatOf    -> +"data modify storage $MCX_STORAGE ${P.Type.FLOAT.stack} append value ${term.value}f"
      is L.Term.DoubleOf   -> +"data modify storage $MCX_STORAGE ${P.Type.DOUBLE.stack} append value ${term.value}"
      is L.Term.StringOf   -> +"data modify storage $MCX_STORAGE ${P.Type.STRING.stack} append value ${term.value.quoted('"')}" // TODO: quote if necessary
      is L.Term.ListOf     -> {
        +"data modify storage $MCX_STORAGE ${P.Type.LIST.stack} append value []"
        if (term.values.isNotEmpty()) {
          val valueType = eraseType(term.values.first().type).first()
          term.values.forEach { value ->
            packTerm(value)
            val index = if (valueType == P.Type.LIST) -2 else -1
            +"data modify storage $MCX_STORAGE ${P.Type.LIST.stack}[$index] append from storage $MCX_STORAGE ${valueType.stack}[-1]"
            +"data remove storage $MCX_STORAGE ${valueType.stack}[-1]"
          }
        }
      }
      is L.Term.CompoundOf -> {
        +"data modify storage $MCX_STORAGE ${P.Type.COMPOUND.stack} append value {}"
        term.values.forEach { (key, value) ->
          packTerm(value)
          val valueType = eraseType(value.type).first()
          val index = if (valueType == P.Type.COMPOUND) -2 else -1
          +"data modify storage $MCX_STORAGE ${P.Type.COMPOUND.stack}[$index] append from storage $MCX_STORAGE ${valueType.stack}[-1]"
          +"data remove storage $MCX_STORAGE ${valueType.stack}[-1]"
        }
      }
      is L.Term.BoxOf      -> +"# $term" // TODO
      is L.Term.TupleOf    -> {
        term.values.forEach {
          packTerm(it)
        }
      }
      is L.Term.If         -> {
        packTerm(term.condition)
        +"execute store result score #0 mcx run data get storage mcx: byte[-1]"
        +"data remove storage mcx: byte[-1]"
        +"execute if score #0 mcx matches 1.. run function ${packLocation(term.thenName)}"
        +"execute if score #0 mcx matches ..0 run function ${packLocation(term.elseName)}"
      }
      is L.Term.Let        -> {
        packTerm(term.init)
        packPattern(term.binder)
        packTerm(term.body)

        val binderTypes = eraseType(term.binder.type)
        val bodyTypes = eraseType(term.body.type)
        binderTypes.forEach { binderType ->
          dropType(binderType, bodyTypes)
        }
      }
      is L.Term.Var        -> {
        val type = eraseType(term.type).first() // TODO
        val index = this[term.name, type]
        +"data modify storage $MCX_STORAGE ${type.stack} append from storage $MCX_STORAGE ${type.stack}[$index]"
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

  private fun Env.packPattern(
    pattern: L.Pattern,
  ) {
    when (pattern) {
      is L.Pattern.TupleOf -> pattern.elements.forEach { packPattern(it) }
      is L.Pattern.Var     -> bind(pattern.name, eraseType(pattern.type).first())
    }
  }

  private fun Env.dropType(
    drop: P.Type,
    keeps: List<P.Type>,
  ) {
    drop(drop)
    when (drop) {
      P.Type.END -> Unit
      else       -> +"data remove storage $MCX_STORAGE ${drop.stack}[${-1 - keeps.count { it == drop }}]"
    }
  }

  private fun eraseType(
    type: L.Type,
  ): List<P.Type> {
    return when (type) {
      is L.Type.End      -> listOf(P.Type.END)
      is L.Type.Bool     -> listOf(P.Type.BYTE)
      is L.Type.Byte     -> listOf(P.Type.BYTE)
      is L.Type.Short    -> listOf(P.Type.SHORT)
      is L.Type.Int      -> listOf(P.Type.INT)
      is L.Type.Long     -> listOf(P.Type.LONG)
      is L.Type.Float    -> listOf(P.Type.FLOAT)
      is L.Type.Double   -> listOf(P.Type.DOUBLE)
      is L.Type.String   -> listOf(P.Type.STRING)
      is L.Type.List     -> listOf(P.Type.LIST)
      is L.Type.Compound -> listOf(P.Type.COMPOUND)
      is L.Type.Box      -> listOf(P.Type.INT)
      is L.Type.Tuple    -> type.elements.flatMap { eraseType(it) }
    }
  }

  private fun packLocation(
    name: Location,
  ): String =
    "${
      name.parts
        .dropLast(1)
        .joinToString("/")
    }/${escape(name.parts.last())}"

  private fun escape(
    string: String,
  ): String =
    string
      .encodeToByteArray()
      .joinToString("") {
        when (
          val char =
            it
              .toInt()
              .toChar()
        ) {
          in 'a'..'z', in '0'..'9', '_', '-' -> char.toString()
          else                               -> ".${
            it
              .toUByte()
              .toString(Character.MAX_RADIX)
          }"
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

    fun drop(
      type: P.Type,
    ) {
      entries[type]!!.removeLast()
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
