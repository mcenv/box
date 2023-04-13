package mcx.pass.backend

import mcx.ast.DefinitionLocation
import mcx.ast.Packed
import mcx.ast.Packed.Command
import mcx.ast.Packed.Command.*
import mcx.ast.Packed.Command.Execute.Mode.RESULT
import mcx.ast.Packed.Command.Execute.StoreStorage.Type
import mcx.ast.Packed.DataAccessor
import mcx.ast.Packed.DataManipulator
import mcx.ast.Packed.SourceProvider
import mcx.data.Nbt
import mcx.data.NbtType
import mcx.data.ResourceLocation
import mcx.pass.Context
import mcx.pass.Context.Companion.DISPATCH
import mcx.ast.Lifted as L
import mcx.ast.Packed as P

class Pack private constructor(
  private val context: Context,
) {
  private val commands: MutableList<Command> = mutableListOf()
  private val entries: Map<NbtType, MutableList<String?>> = NbtType.values().associateWith { mutableListOf() }

  private fun packDefinition(
    definition: L.Definition,
  ): P.Definition {
    val path = packDefinitionLocation(definition.name)
    return when (definition) {
      is L.Definition.Function -> {
        !{ Raw("# function ${definition.name}\n") }

        if (L.Modifier.BUILTIN in definition.modifiers) {
          // lookupBuiltin(definition.name)!!.pack()
        } else if (L.Modifier.TEST in definition.modifiers) {
          packTerm(definition.body!!)
          val accessor = DataAccessor(MCX_TEST, nbtPath {
            definition.name.module.parts.fold(it) { acc, part -> acc(part) }(definition.name.name)
          })
          +RemoveData(accessor)
          +ManipulateData(accessor, DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, nbtPath { it(NbtType.BYTE.id)(-1) }))))
          drop(NbtType.BYTE)
        } else {
          definition.params.forEach {
            push(it.type, null)
            packPattern(it)
          }

          val body = definition.body!!
          packTerm(body)

          val drop = L.Modifier.NO_DROP !in definition.modifiers
          if (drop) {
            definition.params.forEach { dropPattern(it, listOf(body.type)) }
          }

          if (definition.restore != null) {
            +SetScore(REG_0, REG, definition.restore)
          }

          // validate stacks
          if (context.config.debug) {
            !{ Raw("") }
            entries.forEach { entry ->
              !{ Raw("# ${entry.key.toString().padEnd(10)}: ${entry.value.joinToString(", ", "[", "]")}") }
            }
            val remaining = if (drop) listOf(body.type) else definition.params.map { it.type } + body.type
            remaining.forEach {
              drop(it, relevant = false)
            }
          }
        }

        P.Definition.Function(path, commands)
      }
    }
  }

  private fun packTerm(
    term: L.Term,
  ) {
    when (term) {
      is L.Term.If          -> {
        packTerm(term.condition)
        +Execute.StoreScore(RESULT, REG_0, REG, Execute.Run(GetData(DataAccessor(MCX, nbtPath { it(NbtType.BYTE.id)(-1) }))))
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
        push(term.type, null)
      }

      is L.Term.Is          -> {
        packTerm(term.scrutinee)
        matchPattern(term.scrutineer)
      }

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
            +ManipulateData(DataAccessor(MCX, nbtPath { it(NbtType.BYTE_ARRAY.id)(-1)(index) }), DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, nbtPath { it(NbtType.BYTE.id)(-1) }))))
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
            +ManipulateData(DataAccessor(MCX, nbtPath { it(NbtType.INT_ARRAY.id)(-1)(index) }), DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, nbtPath { it(NbtType.INT.id)(-1) }))))
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
            +ManipulateData(DataAccessor(MCX, nbtPath { it(NbtType.LONG_ARRAY.id)(-1)(index) }), DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, nbtPath { it(NbtType.LONG.id)(-1) }))))
            drop(NbtType.LONG)
          }
        }
      }

      is L.Term.ListOf      -> {
        push(NbtType.LIST, SourceProvider.Value(Nbt.List.End))
        if (term.elements.isNotEmpty()) {
          val elementType = term.elements.first().type
          term.elements.forEach { element ->
            packTerm(element)
            val index = if (elementType == NbtType.LIST) -2 else -1
            +ManipulateData(DataAccessor(MCX, nbtPath { it(NbtType.LIST.id)(index) }), DataManipulator.Append(SourceProvider.From(DataAccessor(MCX, nbtPath { it(elementType.id)(-1) }))))
            drop(elementType)
          }
        }
      }

      is L.Term.CompoundOf  -> {
        push(NbtType.COMPOUND, SourceProvider.Value(Nbt.Compound(emptyMap())))
        term.elements.forEach { (key, element) ->
          packTerm(element)
          val valueType = element.type
          val index = if (valueType == NbtType.COMPOUND) -2 else -1
          +ManipulateData(DataAccessor(MCX, nbtPath { it(NbtType.COMPOUND.id)(index)(key) }), DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, nbtPath { it(valueType.id)(-1) }))))
          drop(valueType)
        }
      }

      is L.Term.FuncOf      -> {
        push(NbtType.COMPOUND, SourceProvider.Value(Nbt.Compound(mapOf("_" to Nbt.Int(term.tag)))))
        term.entries.forEach { (name, type) ->
          val index = this[name, type]
          +ManipulateData(DataAccessor(MCX, nbtPath { it(NbtType.COMPOUND.id)(-1)(name) }), DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, nbtPath { it(type.id)(index) }))))
        }
      }

      is L.Term.Apply       -> {
        term.args.forEach { packTerm(it) }
        packTerm(term.func)
        +RunFunction(packDefinitionLocation(DISPATCH))

        push(term.type, null)
        val keeps = listOf(term.type)
        term.args.forEach { drop(it.type, keeps, false) }
      }

      is L.Term.Command     -> {
        +Raw(term.element)
        push(term.type, null)
      }

      is L.Term.Let         -> {
        packTerm(term.init)
        packPattern(term.binder)
        packTerm(term.body)

        dropPattern(term.binder, listOf(term.body.type))
      }

      is L.Term.Var         -> {
        val type = term.type
        val index = this[term.name, term.type]
        push(type, SourceProvider.From(DataAccessor(MCX, nbtPath { it(type.id)(index) })))
      }

      is L.Term.Def         -> {
        +RunFunction(packDefinitionLocation(term.name))
        push(term.type, null)
      }
    }
  }

  private fun packPattern(
    pattern: L.Pattern,
  ) {
    when (pattern) {
      is L.Pattern.IntOf      -> {}
      is L.Pattern.CompoundOf -> {
        pattern.elements.forEach { (name, element) ->
          push(element.type, SourceProvider.From(DataAccessor(MCX, nbtPath { it(NbtType.COMPOUND.id)(-1)(name) }))) // TODO: avoid immediate push
          packPattern(element)
        }
      }

      is L.Pattern.Var        -> bind(pattern.name, pattern.type)
      is L.Pattern.Drop       -> {}
    }
  }

  private fun dropPattern(
    pattern: L.Pattern,
    keeps: List<NbtType>,
  ) {
    when (pattern) {
      is L.Pattern.IntOf      -> drop(NbtType.INT, keeps)
      is L.Pattern.CompoundOf -> {
        pattern.elements.entries.reversed().forEach { (_, element) -> dropPattern(element, keeps) }
        drop(NbtType.COMPOUND, keeps)
      }

      is L.Pattern.Var        -> drop(pattern.type, keeps)
      is L.Pattern.Drop       -> drop(pattern.type, keeps)
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
              GetData(DataAccessor(MCX, nbtPath { it(NbtType.INT.id)(-1) }))
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
        is L.Pattern.Var        -> drop(scrutineer.type)
        is L.Pattern.Drop       -> drop(scrutineer.type)
      }
    }
    visit(scrutineer)
    push(NbtType.BYTE, SourceProvider.Value(Nbt.Byte(0)))
    +Execute.StoreStorage(
      RESULT, DataAccessor(MCX, nbtPath { it(NbtType.BYTE.id)(-1) }), Type.BYTE, 1.0,
      Execute.Run(
        GetScore(REG_0, REG)
      )
    )
  }

  private fun push(
    stack: NbtType,
    source: SourceProvider?,
  ) {
    if (source != null && stack != NbtType.END) {
      !{ Raw("# push ${stack.id}") }
      +ManipulateData(DataAccessor(MCX, nbtPath { it(stack.id) }), DataManipulator.Append(source))
    }
    getEntry(stack) += null
  }

  private fun drop(
    drop: NbtType,
    keeps: List<NbtType> = emptyList(),
    relevant: Boolean = true,
  ) {
    val index = -1 - keeps.count { it == drop }
    if (relevant && drop != NbtType.END) {
      !{ Raw("# drop ${drop.id} under ${keeps.joinToString(", ", "[", "]") { it.id }}") }
      +RemoveData(DataAccessor(MCX, nbtPath { it(drop.id)(index) }))
    }
    val entry = getEntry(drop)
    entry.removeAt(entry.size + index)
  }

  private fun bind(
    name: String,
    stack: NbtType,
  ) {
    val entry = getEntry(stack)
    val index = entry.indexOfLast { it == null }
    entry[index] = name
  }

  operator fun get(
    name: String,
    stack: NbtType,
  ): Int {
    val entry = getEntry(stack)
    return entry.indexOfLast { it == name }.also { require(it != -1) } - entry.size
  }

  private fun getEntry(
    stack: NbtType,
  ): MutableList<String?> {
    return entries[stack]!!
  }

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
    private val MCX_TEST: ResourceLocation = ResourceLocation("mcx_test", "")

    private fun packDefinitionLocation(
      location: DefinitionLocation,
    ): ResourceLocation {
      return ResourceLocation("minecraft", (location.module.parts + escape(location.name)).joinToString("/"))
    }

    private fun escape(
      string: String,
    ): String {
      return string
        .encodeToByteArray()
        .joinToString("") {
          when (val char = it.toInt().toChar()) {
            in 'a'..'z', in '0'..'9', '_', '-' -> char.toString()
            else                               -> ".${it.toUByte().toString(Character.MAX_RADIX)}"
          }
        }
    }

    private inline fun nbtPath(
      root: Packed.NbtNode.MatchRootObject? = null,
      block: (NbtPathBuilder) -> Unit,
    ): Packed.NbtPath {
      val builder = NbtPathBuilder(root)
      block(builder)
      return builder.build()
    }

    @Suppress("NOTHING_TO_INLINE")
    private class NbtPathBuilder(root: Packed.NbtNode.MatchRootObject? = null) {
      val nodes: MutableList<Packed.NbtNode> = mutableListOf()

      init {
        root?.let { nodes += it }
      }

      inline operator fun invoke(pattern: Nbt.Compound): NbtPathBuilder {
        return apply { nodes += Packed.NbtNode.MatchElement(pattern) }
      }

      inline operator fun invoke(): NbtPathBuilder {
        return apply { nodes += Packed.NbtNode.AllElements }
      }

      inline operator fun invoke(index: Int): NbtPathBuilder {
        return apply { nodes += Packed.NbtNode.IndexedElement(index) }
      }

      inline operator fun invoke(name: String, pattern: Nbt.Compound): NbtPathBuilder {
        return apply { nodes += Packed.NbtNode.MatchObject(name, pattern) }
      }

      inline operator fun invoke(name: String): NbtPathBuilder {
        return apply { nodes += Packed.NbtNode.CompoundChild(name) }
      }

      fun build(): Packed.NbtPath {
        return Packed.NbtPath(nodes)
      }
    }

    // TODO: specialize dispatcher by type
    fun packDispatch(
      functions: List<L.Definition.Function>,
    ): P.Definition.Function {
      val name = packDefinitionLocation(DISPATCH)
      val commands = listOf(
        Execute.StoreScore(RESULT, REG_0, REG, Execute.Run(GetData(DataAccessor(MCX, nbtPath { it(NbtType.COMPOUND.id)(-1)("_") }))))
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
