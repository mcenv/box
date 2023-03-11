package mcx.phase.backend

import mcx.ast.DefinitionLocation
import mcx.ast.Packed
import mcx.ast.Packed.Command
import mcx.ast.Packed.Command.*
import mcx.ast.Packed.Command.Execute.ConditionalScore.Comparator.*
import mcx.ast.Packed.Command.Execute.Mode.*
import mcx.ast.Packed.DataAccessor
import mcx.ast.Packed.DataManipulator
import mcx.ast.Packed.Operation.*
import mcx.ast.Packed.SourceProvider
import mcx.data.NbtType
import mcx.data.ResourceLocation
import mcx.phase.*
import mcx.phase.Context.Companion.DISPATCH
import mcx.util.nbtPath
import mcx.ast.Lifted as L
import mcx.ast.Packed as P

class Pack private constructor(
  private val context: Context,
) {
  private val commands: MutableList<Command> = mutableListOf()
  private val entries: Map<NbtType, MutableList<Int?>> = NbtType.values().associateWith { mutableListOf() }

  private fun packDefinition(
    definition: L.Definition,
  ): P.Definition {
    val path = packDefinitionLocation(definition.name)
    return when (definition) {
      is L.Definition.Function -> {
        !{ Raw("# function ${definition.name}") }

        /*
        val binderTypes = eraseTypes(definition.binder.type)
        binderTypes.forEach { push(it, null) }
        packPattern(definition.binder)

        packTerm(definition.body)

        if (L.Modifier.NO_DROP !in definition.modifiers) {
          val resultTypes = eraseTypes(definition.body.type)
          dropPattern(definition.binder, resultTypes)
        }

        if (definition.restore != null) {
          +SetScore(REG_0, REG, definition.restore)
        }
        */

        P.Definition.Function(path, commands)
      }
    }
  }

  /*
  private fun packTerm(
    term: L.Term,
  ) {
    when (term) {
      is L.Term.BoolOf      -> push(NbtType.BYTE, SourceProvider.Value(Nbt.Byte(if (term.value) 1 else 0)))
      is L.Term.ByteOf      -> push(NbtType.BYTE, SourceProvider.Value(Nbt.Byte(term.value)))
      is L.Term.ShortOf     -> push(NbtType.SHORT, SourceProvider.Value(Nbt.Short(term.value)))
      is L.Term.IntOf       -> push(NbtType.INT, SourceProvider.Value(Nbt.Int(term.value)))
      is L.Term.LongOf      -> push(NbtType.LONG, SourceProvider.Value(Nbt.Long(term.value)))
      is L.Term.FloatOf     -> push(NbtType.FLOAT, SourceProvider.Value(Nbt.Float(term.value)))
      is L.Term.DoubleOf    -> push(NbtType.DOUBLE, SourceProvider.Value(Nbt.Double(term.value)))
      is L.Term.StringOf    -> push(NbtType.STRING, SourceProvider.Value(Nbt.String(term.value)))
      is L.Term.ByteArrayOf -> {
        val elements = term.elements.map { (it as? L.Term.ByteOf)?.value ?: 0 }
        push(NbtType.BYTE_ARRAY, SourceProvider.Value(Nbt.ByteArray(elements)))
        term.elements.forEachIndexed { index, element ->
          if (element !is L.Term.ByteOf) {
            packTerm(element)
            +ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(NbtType.BYTE_ARRAY.id)(-1)(index) }), DataManipulator.Set(SourceProvider.From(DataAccessor.Storage(MCX, nbtPath { it(NbtType.BYTE.id)(-1) }))))
            drop(NbtType.BYTE)
          }
        }
      }
      is L.Term.IntArrayOf  -> {
        val elements = term.elements.map { (it as? L.Term.IntOf)?.value ?: 0 }
        push(NbtType.INT_ARRAY, SourceProvider.Value(Nbt.IntArray(elements)))
        term.elements.forEachIndexed { index, element ->
          if (element !is L.Term.IntOf) {
            packTerm(element)
            +ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(NbtType.INT_ARRAY.id)(-1)(index) }), DataManipulator.Set(SourceProvider.From(DataAccessor.Storage(MCX, nbtPath { it(NbtType.INT.id)(-1) }))))
            drop(NbtType.INT)
          }
        }
      }
      is L.Term.LongArrayOf -> {
        val elements = term.elements.map { (it as? L.Term.LongOf)?.value ?: 0 }
        push(NbtType.LONG_ARRAY, SourceProvider.Value(Nbt.LongArray(elements)))
        term.elements.forEachIndexed { index, element ->
          if (element !is L.Term.LongOf) {
            packTerm(element)
            +ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(NbtType.LONG_ARRAY.id)(-1)(index) }), DataManipulator.Set(SourceProvider.From(DataAccessor.Storage(MCX, nbtPath { it(NbtType.LONG.id)(-1) }))))
            drop(NbtType.LONG)
          }
        }
      }
      is L.Term.ListOf      -> {
        push(NbtType.LIST, SourceProvider.Value(Nbt.List.End))
        if (term.elements.isNotEmpty()) {
          val elementType = eraseSingleType(term.elements.first().type)
          term.elements.forEach { element ->
            packTerm(element)
            val index = if (elementType == NbtType.LIST) -2 else -1
            +ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(NbtType.LIST.id)(index) }), DataManipulator.Append(SourceProvider.From(DataAccessor.Storage(MCX, nbtPath { it(elementType.id)(-1) }))))
            drop(elementType)
          }
        }
      }
      is L.Term.CompoundOf  -> {
        push(NbtType.COMPOUND, SourceProvider.Value(Nbt.Compound(emptyMap())))
        term.elements.forEach { (key, element) ->
          packTerm(element)
          val valueType = eraseSingleType(element.type)
          val index = if (valueType == NbtType.COMPOUND) -2 else -1
          +ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(NbtType.COMPOUND.id)(index)(key) }), DataManipulator.Set(SourceProvider.From(DataAccessor.Storage(MCX, nbtPath { it(valueType.id)(-1) }))))
          drop(valueType)
        }
      }
      is L.Term.FuncOf      -> {
        push(NbtType.INT, SourceProvider.Value(Nbt.Int(term.tag)))
      }
      is L.Term.ClosOf      -> {
        push(NbtType.COMPOUND, SourceProvider.Value(Nbt.Compound(mapOf("_" to Nbt.Int(term.tag)))))
        term.vars.forEach { (name, level, type) ->
          val stack = eraseSingleType(type)
          val index = this[level, stack]
          +ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(NbtType.COMPOUND.id)(-1)(name) }), DataManipulator.Set(SourceProvider.From(DataAccessor.Storage(MCX, nbtPath { it(stack.id)(index) }))))
        }
      }
      is L.Term.Apply       -> TODO()
      is L.Term.If          -> {
        packTerm(term.condition)
        +Execute.StoreScore(RESULT, REG_0, REG, Execute.Run(GetData(DataAccessor.Storage(MCX, nbtPath { it(NbtType.BYTE.id)(-1) }))))
        drop(NbtType.BYTE)
        +Execute.ConditionalScoreMatches(
          true, REG_0, REG, 1..Int.MAX_VALUE,
          Execute.Run(
            RunFunction(packDefinitionLocation(term.thenName))
          )
        )
        +Execute.ConditionalScoreMatches(
          true, REG_0, REG, Int.MIN_VALUE..0,
          Execute.Run(
            RunFunction(packDefinitionLocation(term.elseName))
          )
        )
        eraseTypes(term.type).forEach { push(it, null) }
      }
      is L.Term.Let         -> {
        packTerm(term.init)
        packPattern(term.binder)
        packTerm(term.body)

        val bodyTypes = eraseTypes(term.body.type)
        dropPattern(term.binder, bodyTypes)
      }
      is L.Term.Var         -> {
        val stacks = eraseTypes(term.type)
        stacks.forEachIndexed { offset, stack ->
          val index = this[term.level, stack] - stacks.size + offset + 1
          push(stack, SourceProvider.From(DataAccessor.Storage(MCX, nbtPath { it(stack.id)(index) })))
        }
      }
      is L.Term.Def         -> {
        +RunFunction(packDefinitionLocation(term.name))
        eraseTypes(term.type).forEach { push(it, null) }
      }
      is L.Term.Is          -> {
        packTerm(term.scrutinee)
        matchPattern(term.scrutineer)
      }
    }
  }

  private fun packPattern(
    pattern: L.Pattern,
  ) {
    when (pattern) {
      is L.Pattern.IntOf      -> Unit
      is L.Pattern.CompoundOf -> {
        pattern.elements.forEach { (name, element) ->
          push(eraseSingleType(element.type), SourceProvider.From(DataAccessor.Storage(MCX, nbtPath { it(NbtType.COMPOUND.id)(-1)(name) }))) // TODO: avoid immediate push
          packPattern(element)
        }
      }
      is L.Pattern.Var        -> eraseTypes(pattern.type).forEach { bind(pattern.level, it) }
      is L.Pattern.Drop       -> Unit
    }
  }

  private fun dropPattern(
    pattern: L.Pattern,
    keeps: List<NbtType>,
  ) {
    when (pattern) {
      is L.Pattern.IntOf      -> drop(NbtType.INT, keeps)
      is L.Pattern.CompoundOf -> {
        pattern.elements
          .asReversed()
          .forEach { (_, element) -> dropPattern(element, keeps) }
        drop(NbtType.COMPOUND, keeps)
      }
      is L.Pattern.Var        -> eraseTypes(pattern.type).forEach { drop(it, keeps) }
      is L.Pattern.Drop       -> eraseTypes(pattern.type).forEach { drop(it, keeps) }
    }
  }

  private fun matchPattern(
    scrutineer: L.Pattern,
  ) {
    +SetScore(REG_0, REG, 1)
    fun visit(
      scrutineer: L.Pattern,
    ) {
      when (scrutineer) {
        is L.Pattern.IntOf      -> {
          +Execute.StoreScore(
            RESULT, REG_1, REG,
            Execute.Run(
              GetData(DataAccessor.Storage(MCX, nbtPath { it(NbtType.INT.id)(-1) }))
            )
          )
          drop(NbtType.INT)
          +Execute.ConditionalScoreMatches(
            false, REG_1, REG, scrutineer.value..scrutineer.value,
            Execute.Run(
              SetScore(REG_0, REG, 0)
            )
          )
        }
        is L.Pattern.CompoundOf -> TODO()
        is L.Pattern.Var        -> eraseTypes(scrutineer.type).forEach { drop(it) }
        is L.Pattern.Drop       -> eraseTypes(scrutineer.type).forEach { drop(it) }
      }
    }
    visit(scrutineer)
    push(NbtType.BYTE, SourceProvider.Value(Nbt.Byte(0)))
    +Execute.StoreStorage(
      RESULT, DataAccessor.Storage(MCX, nbtPath { it(NbtType.BYTE.id)(-1) }), Type.BYTE, 1.0,
      Execute.Run(
        GetScore(REG_0, REG)
      )
    )
  }
  */

  private fun push(
    stack: NbtType,
    source: SourceProvider?,
  ) {
    if (source != null && stack != NbtType.END) {
      !{ Raw("# push ${stack.id}") }
      +ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(stack.id) }), DataManipulator.Append(source))
    }
    entry(stack) += null
  }

  private fun drop(
    drop: NbtType,
    keeps: List<NbtType> = emptyList(),
    relevant: Boolean = true,
  ) {
    val index = -1 - keeps.count { it == drop }
    if (relevant && drop != NbtType.END) {
      !{ Raw("# drop ${drop.id} under ${keeps.joinToString(", ", "[", "]") { it.id }}") }
      +RemoveData(DataAccessor.Storage(MCX, nbtPath { it(drop.id)(index) }))
    }
    val entry = entry(drop)
    entry.removeAt(entry.size + index)
  }

  private fun bind(
    level: Int,
    stack: NbtType,
  ) {
    val entry = entry(stack)
    val index = entry.indexOfLast { it == null }
    entry[index] = level
  }

  operator fun get(
    level: Int,
    stack: NbtType,
  ): Int {
    val entry = entry(stack)
    return entry.indexOfLast { it == level }.also { require(it != -1) } - entry.size
  }

  private fun entry(
    stack: NbtType,
  ): MutableList<Int?> =
    entries[stack]!!

  @Suppress("NOTHING_TO_INLINE")
  private inline operator fun Command.unaryPlus() {
    commands += this
  }

  @Suppress("NOTHING_TO_INLINE")
  private inline operator fun (() -> Command).not() {
    if (context.config.debug) {
      +this()
    }
  }

  companion object {
    private val REG_0: Packed.ScoreHolder = Packed.ScoreHolder("#0")
    private val REG_1: Packed.ScoreHolder = Packed.ScoreHolder("#1")
    private val REG: Packed.Objective = Packed.Objective("mcx")
    private val MCX: ResourceLocation = ResourceLocation("mcx", "")

    private fun packDefinitionLocation(
      location: DefinitionLocation,
    ): ResourceLocation =
      ResourceLocation("minecraft", (location.module.parts + escape(location.name)).joinToString("/"))

    private fun escape(
      string: String,
    ): String =
      string
        .encodeToByteArray()
        .joinToString("") {
          when (val char = it.toInt().toChar()) {
            in 'a'..'z', in '0'..'9', '_', '-' -> char.toString()
            else                               -> ".${it.toUByte().toString(Character.MAX_RADIX)}"
          }
        }

    // TODO: specialize dispatcher by type
    fun packDispatch(
      functions: List<L.Definition.Function>,
    ): P.Definition.Function {
      val name = packDefinitionLocation(DISPATCH)
      val commands = listOf(
        Execute.StoreScore(RESULT, REG_0, REG, Execute.Run(GetData(DataAccessor.Storage(MCX, nbtPath { it(NbtType.COMPOUND.id)(-1)("_") }))))
      ) + functions.mapIndexed { index, function ->
        Execute.ConditionalScoreMatches(true, REG_0, REG, index..index, Execute.Run(RunFunction(packDefinitionLocation(function.name))))
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
