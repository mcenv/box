package mcx.phase

import mcx.ast.DefinitionLocation
import mcx.ast.Json
import mcx.phase.Context.Companion.DISPATCH
import mcx.util.quoted
import mcx.ast.Lifted as L
import mcx.ast.Packed as P

class Pack private constructor(
  private val context: Context,
) {
  private val commands: MutableList<String> = mutableListOf()
  private val entries: Map<P.Stack, MutableList<Int?>> =
    P.Stack
      .values()
      .associateWith { mutableListOf() }

  private fun packDefinition(
    definition: L.Definition,
  ): P.Definition {
    val path = packDefinitionLocation(definition.name)
    return when (definition) {
      is L.Definition.Resource -> {
        val body = packJson(definition.body)
        P.Definition.Resource(definition.registry, path, body)
      }
      is L.Definition.Function -> {
        !{ "# ${definition.name}" }

        val binderTypes = eraseType(definition.binder.type)
        binderTypes.forEach { push(it, null) }
        packPattern(definition.binder)

        packTerm(definition.body)

        if (L.Annotation.NoDrop !in definition.binder.annotations) {
          val resultTypes = eraseType(definition.body.type)
          dropPattern(definition.binder, resultTypes)
        }

        if (definition.restore != null) {
          +"scoreboard players set $REGISTER_0 ${definition.restore}"
        }

        P.Definition.Function(path, commands)
      }
      is L.Definition.Builtin  -> {
        +"# ${definition.name}"
        val builtin = BUILTINS[definition.name]!!
        builtin.commands.forEach { +it }
        P.Definition.Function(path, commands)
      }
    }
  }

  private fun packTerm(
    term: L.Term,
  ) {
    when (term) {
      is L.Term.BoolOf      -> push(P.Stack.BYTE, "value ${if (term.value) 1 else 0}b")
      is L.Term.ByteOf      -> push(P.Stack.BYTE, "value ${term.value}b")
      is L.Term.ShortOf     -> push(P.Stack.SHORT, "value ${term.value}s")
      is L.Term.IntOf       -> push(P.Stack.INT, "value ${term.value}")
      is L.Term.LongOf      -> push(P.Stack.LONG, "value ${term.value}l")
      is L.Term.FloatOf     -> push(P.Stack.FLOAT, "value ${term.value}f")
      is L.Term.DoubleOf    -> push(P.Stack.DOUBLE, "value ${term.value}")
      is L.Term.StringOf    -> push(P.Stack.STRING, "value ${term.value.quoted('"')}") // TODO: quote only if necessary
      is L.Term.ByteArrayOf -> {
        push(P.Stack.BYTE_ARRAY, "value ${term.elements.joinToString(",", "[B;", "]") { "0b" }}")
        term.elements.forEachIndexed { index, element ->
          packTerm(element)
          +"data modify storage $MCX_STORAGE ${P.Stack.BYTE_ARRAY}[-1][$index] set from storage $MCX_STORAGE ${P.Stack.BYTE}[-1]"
          drop(P.Stack.BYTE)
        }
      }
      is L.Term.IntArrayOf  -> {
        push(P.Stack.INT_ARRAY, "value ${term.elements.joinToString(",", "[I;", "]") { "0" }}")
        term.elements.forEachIndexed { index, element ->
          packTerm(element)
          +"data modify storage $MCX_STORAGE ${P.Stack.INT_ARRAY}[-1][$index] set from storage $MCX_STORAGE ${P.Stack.INT}[-1]"
          drop(P.Stack.INT)
        }
      }
      is L.Term.LongArrayOf -> {
        push(P.Stack.LONG_ARRAY, "value ${term.elements.joinToString(",", "[L;", "]") { "0l" }}")
        term.elements.forEachIndexed { index, element ->
          packTerm(element)
          +"data modify storage $MCX_STORAGE ${P.Stack.LONG_ARRAY}[-1][$index] set from storage $MCX_STORAGE ${P.Stack.LONG}[-1]"
          drop(P.Stack.LONG)
        }
      }
      is L.Term.ListOf      -> {
        push(P.Stack.LIST, "value []")
        if (term.elements.isNotEmpty()) {
          val elementType = eraseType(term.elements.first().type).first()
          term.elements.forEach { element ->
            packTerm(element)
            val index = if (elementType == P.Stack.LIST) -2 else -1
            +"data modify storage $MCX_STORAGE ${P.Stack.LIST}[$index] append from storage $MCX_STORAGE $elementType[-1]"
            drop(elementType)
          }
        }
      }
      is L.Term.CompoundOf  -> {
        push(P.Stack.COMPOUND, "value {}")
        term.elements.forEach { (key, element) ->
          packTerm(element)
          val valueType = eraseType(element.type).first()
          val index = if (valueType == P.Stack.COMPOUND) -2 else -1
          +"data modify storage $MCX_STORAGE ${P.Stack.COMPOUND}[$index].$key set from storage $MCX_STORAGE $valueType[-1]"
          drop(valueType)
        }
      }
      is L.Term.RefOf       -> {
        packTerm(term.element)
        +"function heap/${eraseType(term.element.type).first()}_ref"
        push(P.Stack.INT, null)
      }
      is L.Term.TupleOf     -> {
        term.elements.forEach { element ->
          packTerm(element)
        }
      }
      is L.Term.FunOf       -> {
        push(P.Stack.COMPOUND, "value {_:${term.tag}}")
        term.vars.forEach { (name, level, type) ->
          val stack = eraseType(type).first()
          val index = this[level, stack]
          +"data modify storage $MCX_STORAGE ${P.Stack.COMPOUND}[-1].$name set from storage $MCX_STORAGE $stack[$index]"
        }
      }
      is L.Term.If          -> {
        packTerm(term.condition)
        +"execute store result score #0 mcx run data get storage mcx: byte[-1]"
        drop(P.Stack.BYTE)
        +"execute if score #0 mcx matches 1.. run function ${packDefinitionLocation(term.thenName)}"
        +"execute if score #0 mcx matches ..0 run function ${packDefinitionLocation(term.elseName)}"
        eraseType(term.type).forEach { push(it, null) }
      }
      is L.Term.Let         -> {
        packTerm(term.init)
        packPattern(term.binder)
        packTerm(term.body)

        if (L.Annotation.NoDrop !in term.binder.annotations) {
          val bodyTypes = eraseType(term.body.type)
          dropPattern(term.binder, bodyTypes)
        }
      }
      is L.Term.Var         -> {
        val type = eraseType(term.type).first()
        val index = this[term.level, type]
        push(type, "from storage $MCX_STORAGE $type[$index]")
      }
      is L.Term.Run         -> {
        packTerm(term.arg)

        +"function ${packDefinitionLocation(term.name)}"
        eraseType(term.arg.type).forEach {
          drop(it, relevant = false)
        }
        eraseType(term.type).forEach { push(it, null) }
      }
      is L.Term.Is          -> {
        packTerm(term.scrutinee)
        matchPattern(term.scrutineer)
      }
      is L.Term.Command     -> {
        +term.value
        eraseType(term.type).forEach { push(it, null) }
      }
    }
  }

  private fun packPattern(
    pattern: L.Pattern,
  ) {
    when (pattern) {
      is L.Pattern.IntOf      -> Unit
      is L.Pattern.IntRangeOf -> Unit
      is L.Pattern.CompoundOf -> {
        pattern.elements.forEach { (name, element) ->
          push(eraseType(element.type).first(), "from storage $MCX_STORAGE ${P.Stack.COMPOUND}[-1].$name")
          packPattern(element)
        }
      }
      is L.Pattern.TupleOf    ->
        pattern.elements
          .asReversed()
          .forEach { packPattern(it) }
      is L.Pattern.Var        -> bind(pattern.level, eraseType(pattern.type).first())
      is L.Pattern.Drop       -> Unit
    }
  }

  private fun dropPattern(
    pattern: L.Pattern,
    keeps: List<P.Stack>,
  ) {
    when (pattern) {
      is L.Pattern.IntOf      -> drop(P.Stack.INT, keeps)
      is L.Pattern.IntRangeOf -> drop(P.Stack.INT, keeps)
      is L.Pattern.CompoundOf -> {
        pattern.elements
          .asReversed()
          .forEach { (_, element) -> dropPattern(element, keeps) }
        drop(P.Stack.COMPOUND, keeps)
      }
      is L.Pattern.TupleOf    ->
        pattern.elements
          .asReversed()
          .forEach { dropPattern(it, keeps) }
      is L.Pattern.Var        -> drop(eraseType(pattern.type).first(), keeps)
      is L.Pattern.Drop       -> eraseType(pattern.type).forEach { drop(it, keeps) }
    }
  }

  private fun matchPattern(
    scrutineer: L.Pattern,
  ) {
    +"scoreboard players set $REGISTER_0 1"
    fun visit(
      scrutineer: L.Pattern,
    ) {
      when (scrutineer) {
        is L.Pattern.IntOf      -> {
          +"execute store result score $REGISTER_1 run data get storage $MCX_STORAGE ${P.Stack.INT}[-1]"
          drop(P.Stack.INT)
          +"execute unless score $REGISTER_1 matches ${scrutineer.value} run scoreboard players set $REGISTER_0 0"
        }
        is L.Pattern.IntRangeOf -> {
          +"execute store result score $REGISTER_1 run data get storage $MCX_STORAGE ${P.Stack.INT}[-1]"
          drop(P.Stack.INT)
          +"execute unless score $REGISTER_1 matches ${scrutineer.min}..${scrutineer.max} run scoreboard players set $REGISTER_0 0"
        }
        is L.Pattern.CompoundOf -> TODO()
        is L.Pattern.TupleOf    ->
          scrutineer.elements
            .asReversed()
            .forEach { visit(it) } // TODO: short-circuit optimization (in lift phase?)
        is L.Pattern.Var        -> drop(eraseType(scrutineer.type).first())
        is L.Pattern.Drop       -> eraseType(scrutineer.type).forEach { drop(it) }
      }
    }
    visit(scrutineer)
    push(P.Stack.BYTE, "value 0b")
    +"execute store result storage $MCX_STORAGE ${P.Stack.BYTE}[-1] byte 1 run scoreboard players get $REGISTER_0"
  }

  private fun eraseType(
    type: L.Type,
  ): List<P.Stack> {
    return when (type) {
      is L.Type.Bool      -> listOf(P.Stack.BYTE)
      is L.Type.Byte      -> listOf(P.Stack.BYTE)
      is L.Type.Short     -> listOf(P.Stack.SHORT)
      is L.Type.Int       -> listOf(P.Stack.INT)
      is L.Type.Long      -> listOf(P.Stack.LONG)
      is L.Type.Float     -> listOf(P.Stack.FLOAT)
      is L.Type.Double    -> listOf(P.Stack.DOUBLE)
      is L.Type.String    -> listOf(P.Stack.STRING)
      is L.Type.ByteArray -> listOf(P.Stack.BYTE_ARRAY)
      is L.Type.IntArray  -> listOf(P.Stack.INT_ARRAY)
      is L.Type.LongArray -> listOf(P.Stack.LONG_ARRAY)
      is L.Type.List      -> listOf(P.Stack.LIST)
      is L.Type.Compound  -> listOf(P.Stack.COMPOUND)
      is L.Type.Ref       -> listOf(P.Stack.INT)
      is L.Type.Tuple     -> type.elements.flatMap { eraseType(it) }
      is L.Type.Fun       -> listOf(P.Stack.COMPOUND)
      is L.Type.Union     -> type.elements
                               .firstOrNull()
                               ?.let { eraseType(it) } ?: listOf(P.Stack.END)
    }
  }

  private fun packJson(
    term: L.Term,
  ): Json {
    return when (term) {
      is L.Term.BoolOf      -> Json.BoolOf(term.value)
      is L.Term.ByteOf      -> Json.ByteOf(term.value)
      is L.Term.ShortOf     -> Json.ShortOf(term.value)
      is L.Term.IntOf       -> Json.IntOf(term.value)
      is L.Term.LongOf      -> Json.LongOf(term.value)
      is L.Term.FloatOf     -> Json.FloatOf(term.value)
      is L.Term.DoubleOf    -> Json.DoubleOf(term.value)
      is L.Term.StringOf    -> Json.StringOf(term.value)
      is L.Term.ByteArrayOf -> Json.ArrayOf(term.elements.map { packJson(it) })
      is L.Term.IntArrayOf  -> Json.ArrayOf(term.elements.map { packJson(it) })
      is L.Term.LongArrayOf -> Json.ArrayOf(term.elements.map { packJson(it) })
      is L.Term.ListOf      -> Json.ArrayOf(term.elements.map { packJson(it) })
      is L.Term.CompoundOf  -> Json.ObjectOf(term.elements.mapValues { packJson(it.value) })
      is L.Term.RefOf       -> packJson(term.element)
      else                  -> TODO()
    }
  }

  private fun push(
    stack: P.Stack,
    source: String?,
  ) {
    if (source != null && stack != P.Stack.END) {
      !{ "# push $stack" }
      +"data modify storage $MCX_STORAGE $stack append $source"
    }
    entry(stack) += null
  }

  private fun drop(
    drop: P.Stack,
    keeps: List<P.Stack> = emptyList(),
    relevant: Boolean = true,
  ) {
    val index = -1 - keeps.count { it == drop }
    if (relevant && drop != P.Stack.END) {
      !{ "# drop $drop under ${keeps.joinToString(", ", "[", "]")}" }
      +"data remove storage $MCX_STORAGE $drop[$index]"
    }
    val entry = entry(drop)
    entry.removeAt(entry.size + index)
  }

  private fun bind(
    level: Int,
    stack: P.Stack,
  ) {
    val entry = entry(stack)
    val index = entry.indexOfLast { it == null }
    entry[index] = level
  }

  operator fun get(
    level: Int,
    stack: P.Stack,
  ): Int {
    val entry = entry(stack)
    return entry
             .indexOfLast { it == level }
             .also { require(it != -1) } - entry.size
  }

  private fun entry(
    stack: P.Stack,
  ): MutableList<Int?> =
    entries[stack]!!

  @Suppress("NOTHING_TO_INLINE")
  private inline operator fun String.unaryPlus() {
    commands += this
  }

  @Suppress("NOTHING_TO_INLINE")
  private inline operator fun (() -> String).not() {
    if (context.debug) {
      +this()
    }
  }

  companion object {
    private const val MCX_STORAGE: String = "mcx:"
    private const val MCX_OBJECTIVE: String = "mcx"
    private const val REGISTER_0: String = "#0 $MCX_OBJECTIVE"
    private const val REGISTER_1: String = "#1 $MCX_OBJECTIVE"

    private fun packDefinitionLocation(
      location: DefinitionLocation,
    ): String =
      (location.module.parts + escape(location.name)).joinToString("/")

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

    // TODO: specialize dispatcher by type
    fun packDispatch(
      functions: List<L.Definition.Function>,
    ): P.Definition.Function {
      val name = packDefinitionLocation(DISPATCH)
      val commands = listOf(
        "execute store result score $REGISTER_0 run data get storage $MCX_STORAGE compound[-1]._"
      ) + functions.mapIndexed { index, function ->
        "execute if score $REGISTER_0 matches $index run function ${packDefinitionLocation(function.name)}"
      }
      return P.Definition.Function(name, commands)
    }

    operator fun invoke(
      context: Context,
      definition: L.Definition,
    ): P.Definition {
      return Pack(context).packDefinition(definition)
    }
  }
}
