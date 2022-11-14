package mcx.phase

import mcx.ast.Json
import mcx.ast.Lifted
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
      is L.Resource.JsonResource  -> {
        val body = packJson(resource.body)
        P.Resource.JsonResource(resource.registry, path, body)
      }
      is Lifted.Resource.Function -> with(emptyEnv()) {
        +"# ${resource.name}"

        val paramTypes = eraseType(resource.param)
        paramTypes.forEach { bind(null, it) }
        packPattern(resource.binder)
        packTerm(resource.body)

        if (L.Annotation.NoDrop !in resource.annotations) {
          val resultTypes = eraseType(resource.result)
          paramTypes.forEach { dropType(it, resultTypes) }
        }

        P.Resource.Function(path, commands)
      }
    }
  }

  private fun Env.packTerm(
    term: L.Term,
  ) {
    when (term) {
      is L.Term.BoolOf     -> pushType(P.Type.BYTE, "value ${if (term.value) 1 else 0}b")
      is L.Term.ByteOf     -> pushType(P.Type.BYTE, "value ${term.value}b")
      is L.Term.ShortOf    -> pushType(P.Type.SHORT, "value ${term.value}s")
      is L.Term.IntOf      -> pushType(P.Type.INT, "value ${term.value}")
      is L.Term.LongOf     -> pushType(P.Type.LONG, "value ${term.value}l")
      is L.Term.FloatOf    -> pushType(P.Type.FLOAT, "value ${term.value}f")
      is L.Term.DoubleOf   -> pushType(P.Type.DOUBLE, "value ${term.value}")
      is L.Term.StringOf   -> pushType(P.Type.STRING, "value ${term.value.quoted('"')}") // TODO: quote if necessary
      is L.Term.ListOf     -> {
        pushType(P.Type.LIST, "value []")
        if (term.elements.isNotEmpty()) {
          val elementType = eraseType(term.elements.first().type).first()
          term.elements.forEach { value ->
            packTerm(value)
            val index = if (elementType == P.Type.LIST) -2 else -1
            +"data modify storage $MCX_STORAGE ${P.Type.LIST.stack}[$index] append from storage $MCX_STORAGE ${elementType.stack}[-1]"
            dropType(elementType)
          }
        }
      }
      is L.Term.CompoundOf -> {
        pushType(P.Type.COMPOUND, "value {}")
        term.elements.forEach { (key, element) ->
          packTerm(element)
          val valueType = eraseType(element.type).first()
          val index = if (valueType == P.Type.COMPOUND) -2 else -1
          +"data modify storage $MCX_STORAGE ${P.Type.COMPOUND.stack}[$index].$key set from storage $MCX_STORAGE ${valueType.stack}[-1]"
          dropType(valueType)
        }
      }
      is L.Term.BoxOf      -> pushType(P.Type.INT, "TODO")
      is L.Term.TupleOf    -> {
        term.elements.forEach { element ->
          packTerm(element)
        }
      }
      is L.Term.If         -> {
        packTerm(term.condition)
        +"execute store result score #0 mcx run data get storage mcx: byte[-1]"
        dropType(P.Type.BYTE)
        +"execute if score #0 mcx matches 1.. run function ${packLocation(term.thenName)}"
        +"execute if score #0 mcx matches ..0 run function ${packLocation(term.elseName)}"
        eraseType(term.type).forEach { bind(null, it) }
      }
      is L.Term.Let        -> {
        packTerm(term.init)
        packPattern(term.binder)
        packTerm(term.body)

        if (L.Annotation.NoDrop !in term.binder.annotations) {
          val binderTypes = eraseType(term.binder.type)
          val bodyTypes = eraseType(term.body.type)
          binderTypes.forEach { binderType ->
            dropType(binderType, bodyTypes)
          }
        }
      }
      is L.Term.Var        -> {
        val type = eraseType(term.type).first() // TODO
        val index = this[term.name, type]
        pushType(type, "from storage $MCX_STORAGE ${type.stack}[$index]")
      }
      is L.Term.Run        -> {
        packTerm(term.arg)

        +"function ${packLocation(term.name)}"
        eraseType(term.arg.type).forEach {
          dropType(it, relevant = false)
        }
        eraseType(term.type).forEach { bind(null, it) }
      }
      is L.Term.Command    -> {
        bind(null, eraseType(term.type).first() /* TODO */)
        +term.value
      }
    }
  }

  private fun Env.packPattern(
    pattern: L.Pattern,
  ) {
    when (pattern) {
      is L.Pattern.TupleOf ->
        pattern.elements
          .asReversed()
          .forEach { packPattern(it) }
      is L.Pattern.Var     -> name(pattern.name, eraseType(pattern.type).first())
      is L.Pattern.Discard -> Unit
    }
  }

  private fun Env.pushType(
    type: P.Type,
    source: String,
  ) {
    +"data modify storage $MCX_STORAGE ${type.stack} append $source"
    bind(null, type)
  }

  private fun Env.dropType(
    drop: P.Type,
    keeps: List<P.Type> = emptyList(),
    relevant: Boolean = true,
  ) {
    val index = -1 - keeps.count { it == drop }
    if (relevant && drop != P.Type.END) {
      +"data remove storage $MCX_STORAGE ${drop.stack}[$index]"
    }
    drop(drop, index)
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
      is L.Term.ListOf     -> Json.ArrayOf(term.elements.map { packJson(it) })
      is L.Term.CompoundOf -> Json.ObjectOf(term.elements.mapValues { packJson(it.value) })
      else                 -> TODO()
    }
  }

  private class Env private constructor(
    private val _commands: MutableList<String>,
  ) {
    val commands: List<String> get() = _commands
    private val entries: Map<P.Type, MutableList<String?>> =
      P.Type
        .values()
        .associateWith { mutableListOf() }

    operator fun String.unaryPlus() {
      _commands += this
    }

    fun bind(
      name: String?,
      type: P.Type,
    ) {
      getEntry(type) += name
    }

    fun name(
      name: String,
      type: P.Type,
    ) {
      val entry = getEntry(type)
      val index = entry.indexOfLast { it == null }
      entry[index] = name
    }

    fun drop(
      type: P.Type,
      offset: Int,
    ) {
      val entry = getEntry(type)
      entry.removeAt(entry.size + offset)
    }

    operator fun get(
      name: String,
      type: P.Type,
    ): Int {
      val entry = getEntry(type)
      return entry
               .indexOfLast { it == name }
               .also { require(it != -1) { "not found: '$name'" } } - entry.size
    }

    private fun getEntry(
      type: P.Type,
    ): MutableList<String?> =
      entries[type]!!

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
