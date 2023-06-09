package mcx.pass.backend

import mcx.ast.DefinitionLocation
import mcx.ast.ModuleLocation
import mcx.ast.Packed
import mcx.ast.Packed.Command
import mcx.ast.Packed.Command.*
import mcx.ast.Packed.Command.Execute.Mode.RESULT
import mcx.ast.Packed.Command.Execute.StoreStorage.Type.INT
import mcx.ast.Packed.DataAccessor
import mcx.ast.Packed.DataManipulator
import mcx.ast.Packed.NbtNode.*
import mcx.ast.Packed.NbtPath
import mcx.ast.Packed.Operation.*
import mcx.ast.Packed.SourceProvider
import mcx.data.NbtType
import mcx.data.ResourceLocation
import mcx.pass.Context
import mcx.pass.core
import mcx.util.nbt.*
import mcx.ast.Lifted as L
import mcx.ast.Packed as P

class Pack private constructor(
  private val context: Context,
) {
  private val commands: MutableList<Command> = mutableListOf()
  private val stacks: Map<NbtType, MutableList<Entry>> = NbtType.entries.associateWith { mutableListOf() }

  private sealed class Entry {
    abstract var name: String?

    data class Val(override var name: String?) : Entry() {
      override fun toString(): String {
        return name ?: "_"
      }
    }

    data class Ref(override var name: String?) : Entry() {
      override fun toString(): String {
        return "&${name ?: "_"}"
      }
    }
  }

  private fun packDefinition(
    definition: L.Definition,
  ): P.Definition {
    val modifiers = definition.modifiers.mapNotNull { packModifier(it) }
    val path = packDefinitionLocation(definition.name)
    return when (definition) {
      is L.Definition.Function -> {
        !{ Raw("# function ${definition.name}\n") }

        if (L.Modifier.TEST in definition.modifiers) {
          packTerm(definition.body!!)
          val accessor = DataAccessor(MCX_TEST, nbtPath {
            definition.name.module.parts.fold(it) { acc, part -> acc(part) }(definition.name.name)
          })
          +RemoveData(accessor)
          +ManipulateData(accessor, DataManipulator.Set(SourceProvider.From(BYTE_TOP)))
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
            +SetScore(R0, MAIN, definition.restore)
          }

          // validate stacks
          if (context.config.debug) {
            !{ Raw("") }
            stacks.forEach { stack ->
              !{ Raw("# ${stack.key.toString().padEnd(10)}: ${stack.value.joinToString(", ", "[", "]")}") }
            }
            val remaining = if (drop) listOf(body.type) else definition.params.map { it.type } + body.type
            remaining.forEach {
              drop(it, relevant = false)
            }
          }
        }

        P.Definition.Function(modifiers, path, commands)
      }
    }
  }

  private fun packModifier(
    modifier: L.Modifier,
  ): P.Modifier? {
    return when (modifier) {
      L.Modifier.NO_DROP -> null
      L.Modifier.BUILTIN -> null
      L.Modifier.TEST    -> P.Modifier.TEST
    }
  }

  private fun packTerm(
    term: L.Term,
  ) {
    when (term) {
      is L.Term.If         -> {
        packTerm(term.condition)
        +Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(BYTE_TOP)))
        drop(NbtType.BYTE)
        +Execute.ConditionalScoreMatches(
          true, R0, MAIN, 1..Int.MAX_VALUE,
          Execute.Run(
            RunFunction(packDefinitionLocation(term.thenName))
          )
        )
        +Execute.ConditionalScoreMatches(
          true, R0, MAIN, Int.MIN_VALUE..0,
          Execute.Run(
            RunFunction(packDefinitionLocation(term.elseName))
          )
        )
        push(term.type, null)
      }

      is L.Term.I8Of       -> {
        push(NbtType.BYTE, SourceProvider.Value(ByteTag(term.value)))
      }

      is L.Term.I16Of      -> {
        push(NbtType.SHORT, SourceProvider.Value(ShortTag(term.value)))
      }

      is L.Term.I32Of      -> {
        push(NbtType.INT, SourceProvider.Value(IntTag(term.value)))
      }

      is L.Term.I64Of      -> {
        push(NbtType.LONG, SourceProvider.Value(LongTag(term.value)))
      }

      is L.Term.F32Of      -> {
        push(NbtType.FLOAT, SourceProvider.Value(FloatTag(term.value)))
      }

      is L.Term.F64Of      -> {
        push(NbtType.DOUBLE, SourceProvider.Value(DoubleTag(term.value)))
      }

      is L.Term.StrOf      -> {
        push(NbtType.STRING, SourceProvider.Value(StringTag(term.value)))
      }

      is L.Term.I8ArrayOf  -> {
        val elements = term.elements.map { (it as? L.Term.I8Of)?.value ?: 0 }
        push(NbtType.BYTE_ARRAY, SourceProvider.Value(ByteArrayTag(elements)))
        term.elements.forEachIndexed { index, element ->
          if (element !is L.Term.I8Of) {
            packTerm(element)
            +ManipulateData(DataAccessor(MCX, nbtPath { it(NbtType.BYTE_ARRAY.id)(-1)(index) }), DataManipulator.Set(SourceProvider.From(BYTE_TOP)))
            drop(NbtType.BYTE)
          }
        }
      }

      is L.Term.I32ArrayOf -> {
        val elements = term.elements.map { (it as? L.Term.I32Of)?.value ?: 0 }
        push(NbtType.INT_ARRAY, SourceProvider.Value(IntArrayTag(elements)))
        term.elements.forEachIndexed { index, element ->
          if (element !is L.Term.I32Of) {
            packTerm(element)
            +ManipulateData(DataAccessor(MCX, nbtPath { it(NbtType.INT_ARRAY.id)(-1)(index) }), DataManipulator.Set(SourceProvider.From(INT_TOP)))
            drop(NbtType.INT)
          }
        }
      }

      is L.Term.I64ArrayOf -> {
        val elements = term.elements.map { (it as? L.Term.I64Of)?.value ?: 0 }
        push(NbtType.LONG_ARRAY, SourceProvider.Value(LongArrayTag(elements)))
        term.elements.forEachIndexed { index, element ->
          if (element !is L.Term.I64Of) {
            packTerm(element)
            +ManipulateData(DataAccessor(MCX, nbtPath { it(NbtType.LONG_ARRAY.id)(-1)(index) }), DataManipulator.Set(SourceProvider.From(LONG_TOP)))
            drop(NbtType.LONG)
          }
        }
      }

      is L.Term.VecOf      -> {
        push(NbtType.LIST, SourceProvider.Value(buildByteListTag { } /* TODO: use end list tag */))
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

      is L.Term.StructOf   -> {
        push(NbtType.COMPOUND, SourceProvider.Value(buildCompoundTag { }))
        term.elements.forEach { (key, element) ->
          packTerm(element)
          val valueType = element.type
          val index = if (valueType == NbtType.COMPOUND) -2 else -1
          +ManipulateData(DataAccessor(MCX, nbtPath { it(NbtType.COMPOUND.id)(index)(key) }), DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, nbtPath { it(valueType.id)(-1) }))))
          drop(valueType)
        }
      }

      is L.Term.RefOf      -> {
        push(NbtType.INT, SourceProvider.Value(IntTag(0)), Entry.Ref(null))
        packTerm(term.element)
        val elementType = term.element.type
        val index = if (elementType == NbtType.INT) -2 else -1
        +Execute.StoreScore(RESULT, R0, MAIN, Execute.StoreStorage(RESULT, DataAccessor(MCX, nbtPath { it(NbtType.INT.id)(index) }), INT, 1.0, Execute.Run(AddScore(R0, FREE, 1))))
        +RunFunction(TOUCH)
        +ManipulateData(HEAP_CELL, DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, nbtPath { it(elementType.id)(-1) }))))
        drop(elementType)
      }

      is L.Term.ProcOf     -> {
        push(NbtType.INT, SourceProvider.Value(IntTag(term.function.restore!!)))
      }

      is L.Term.FuncOf     -> {
        push(NbtType.COMPOUND, SourceProvider.Value(buildCompoundTag { put("_", term.tag) }))
        term.entries.forEach { (name, type) ->
          val index = this[name, type]
          +ManipulateData(DataAccessor(MCX, nbtPath { it(NbtType.COMPOUND.id)(-1)(name) }), DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, nbtPath { it(type.id)(index) }))))
        }
      }

      is L.Term.Apply      -> {
        term.args.forEach { packTerm(it) }
        val func = term.func
        when {
          func is L.Term.ProcOf             -> {
            +RunFunction(packDefinitionLocation(func.function.name))
          }
          func is L.Term.Def && func.direct -> {
            +RunFunction(packDefinitionLocation(func.name.let { it.module / "${it.name}:0" }))
          }
          else                              -> {
            packTerm(term.func)
            +RunFunction(if (term.open) DISPATCH_FUNC else DISPATCH_PROC)
          }
        }

        push(term.type, null)
        val keeps = listOf(term.type)
        term.args.forEach { drop(it.type, keeps, false) }
      }

      is L.Term.Command    -> {
        +Raw(term.element)
        push(term.type, null)
      }

      is L.Term.Let        -> {
        packTerm(term.init)
        packPattern(term.binder)
        packTerm(term.body)

        dropPattern(term.binder, listOf(term.body.type))
      }

      is L.Term.Var        -> {
        val type = term.type
        val index = this[term.name, term.type]
        push(type, SourceProvider.From(DataAccessor(MCX, nbtPath { it(type.id)(index) })))
      }

      is L.Term.Def        -> {
        +RunFunction(packDefinitionLocation(term.name))
        push(term.type, null)
      }
    }
  }

  private fun packPattern(
    pattern: L.Pattern,
  ) {
    when (pattern) {
      is L.Pattern.I32Of    -> {}
      is L.Pattern.StructOf -> {
        pattern.elements.forEach { (name, element) ->
          push(element.type, SourceProvider.From(DataAccessor(MCX, nbtPath { it(NbtType.COMPOUND.id)(-1)(name) }))) // TODO: avoid immediate push
          packPattern(element)
        }
      }

      is L.Pattern.Var      -> bind(pattern.name, pattern.type)
      is L.Pattern.Drop     -> {}
    }
  }

  private fun dropPattern(
    pattern: L.Pattern,
    keeps: List<NbtType>,
  ) {
    when (pattern) {
      is L.Pattern.I32Of    -> drop(NbtType.INT, keeps)
      is L.Pattern.StructOf -> {
        pattern.elements.entries.reversed().forEach { (_, element) -> dropPattern(element, keeps) }
        drop(NbtType.COMPOUND, keeps)
      }

      is L.Pattern.Var      -> drop(pattern.type, keeps)
      is L.Pattern.Drop     -> drop(pattern.type, keeps)
    }
  }

  private fun push(
    type: NbtType,
    source: SourceProvider?,
    entry: Entry = Entry.Val(null),
  ) {
    if (source != null && type != NbtType.END) {
      !{ Raw("# push ${type.id}") }
      +ManipulateData(DataAccessor(MCX, nbtPath { it(type.id) }), DataManipulator.Append(source))
    }
    getStack(type) += entry
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
    val stack = getStack(drop)
    val normalizedIndex = stack.size + index
    if (relevant && stack[normalizedIndex] is Entry.Ref) {
      +PerformOperation(R0, MAIN, ASSIGN, R0, FREE)
      +RemoveScore(R0, FREE, 1)
      +RunFunction(TOUCH)
      +RemoveData(HEAP_CELL)
    }
    stack.removeAt(normalizedIndex)
  }

  private fun bind(
    name: String,
    type: NbtType,
  ) {
    val stack = getStack(type)
    val index = stack.indexOfLast { it.name == null }
    stack[index].name = name
  }

  operator fun get(
    name: String,
    type: NbtType,
  ): Int {
    val stack = getStack(type)
    return stack.indexOfLast { it.name == name }.also { require(it != -1) } - stack.size
  }

  private fun getStack(
    type: NbtType,
  ): MutableList<Entry> {
    return stacks[type]!!
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
    val INIT: ResourceLocation = packDefinitionLocation(DefinitionLocation(ModuleLocation(core), ":init"))
    private val TOUCH: ResourceLocation = packDefinitionLocation(DefinitionLocation(ModuleLocation(core), ":touch"))
    private val DISPATCH_PROC: ResourceLocation = packDefinitionLocation(DefinitionLocation(ModuleLocation(core), ":dispatch_proc"))
    private val DISPATCH_FUNC: ResourceLocation = packDefinitionLocation(DefinitionLocation(ModuleLocation(core), ":dispatch_func"))

    private val MAIN: Packed.Objective = Packed.Objective("mcx")
    private val FREE: Packed.Objective = Packed.Objective("mcx_free")
    private val `65536`: Packed.Objective = Packed.Objective("mcx_65536")

    private val R0: Packed.ScoreHolder = Packed.ScoreHolder("#0")
    private val R1: Packed.ScoreHolder = Packed.ScoreHolder("#1")

    private val MCX: ResourceLocation = ResourceLocation("mcx", "")
    private val MCX_HEAP: ResourceLocation = ResourceLocation("mcx_heap", "")
    private val MCX_TEST: ResourceLocation = ResourceLocation("mcx_test", "")

    private val BYTE_TOP: DataAccessor = DataAccessor(MCX, nbtPath { it(NbtType.BYTE.id)(-1) })
    private val INT_TOP: DataAccessor = DataAccessor(MCX, nbtPath { it(NbtType.INT.id)(-1) })
    private val LONG_TOP: DataAccessor = DataAccessor(MCX, nbtPath { it(NbtType.LONG.id)(-1) })
    private val HEAP_CELL: DataAccessor = DataAccessor(MCX_HEAP, nbtPath { it("heap")(-2)(-2)(-2)(-2)(-2)(-2)(-2)(-2)(-2)(-2)(-2)(-2)(-2)(-2)(-2)(-2)() })

    fun packDefinitionLocation(
      location: DefinitionLocation,
    ): ResourceLocation {
      return ResourceLocation(location.module.parts.first(), (location.module.parts.drop(1) + escape(location.name)).joinToString("/"))
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
      root: MatchRootObject? = null,
      block: (NbtPathBuilder) -> Unit,
    ): NbtPath {
      val builder = NbtPathBuilder(root)
      block(builder)
      return builder.build()
    }

    @Suppress("NOTHING_TO_INLINE")
    private class NbtPathBuilder(root: MatchRootObject? = null) {
      val nodes: MutableList<Packed.NbtNode> = mutableListOf()

      init {
        root?.let { nodes += it }
      }

      inline operator fun invoke(pattern: CompoundTag): NbtPathBuilder {
        return apply { nodes += MatchElement(pattern) }
      }

      inline operator fun invoke(): NbtPathBuilder {
        return apply { nodes += AllElements }
      }

      inline operator fun invoke(index: Int): NbtPathBuilder {
        return apply { nodes += IndexedElement(index) }
      }

      inline operator fun invoke(name: String, pattern: CompoundTag): NbtPathBuilder {
        return apply { nodes += MatchObject(name, pattern) }
      }

      inline operator fun invoke(name: String): NbtPathBuilder {
        return apply { nodes += CompoundChild(name) }
      }

      fun build(): NbtPath {
        return NbtPath(nodes)
      }
    }

    fun packInit(): P.Definition.Function {
      return P.Definition.Function(emptyList(), INIT, listOf(
        Raw("gamerule maxCommandChainLength ${Int.MAX_VALUE}"),
        Raw("scoreboard objectives remove ${MAIN.name}"),
        Raw("scoreboard objectives add ${MAIN.name} dummy"),
        Raw("scoreboard objectives remove ${FREE.name}"),
        Raw("scoreboard objectives add ${FREE.name} dummy"),
        Raw("scoreboard objectives remove ${`65536`.name}"),
        Raw("scoreboard objectives add ${`65536`.name} dummy"),
        SetScore(R0, FREE, -1),
        SetScore(R0, `65536`, 65536),
        ManipulateData(DataAccessor(MCX_HEAP, nbtPath { it("branch") }), DataManipulator.Set(SourceProvider.Value(buildListListTag { add(buildEndListTag()); add(buildEndListTag()) }))),
        *(0..<16).map { count ->
          ManipulateData(DataAccessor(MCX_HEAP, nbtPath { (0..<count).fold(it("heap")) { acc, _ -> acc() } }), DataManipulator.Set(SourceProvider.From(DataAccessor(MCX_HEAP, nbtPath { it("branch") }))))
        }.toTypedArray(),
        RemoveData(DataAccessor(MCX_HEAP, nbtPath { it("branch") })),
      ))
    }

    fun packTouch(): P.Definition.Function {
      return P.Definition.Function(emptyList(), TOUCH, (0..<16).flatMap { depth ->
        val path = nbtPath { (0..<depth).fold(it("heap")) { acc, _ -> acc(-2) } }
        listOf(
          PerformOperation(R0, MAIN, if (depth == 0) MUL else ADD, R0, if (depth == 0) `65536` else MAIN),
          Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX_HEAP, path)))),
          Execute.ConditionalScoreMatches(true, R1, MAIN, 3..Int.MAX_VALUE, Execute.ConditionalScoreMatches(true, R0, MAIN, 0..Int.MAX_VALUE, Execute.Run(RemoveData(DataAccessor(MCX_HEAP, NbtPath(path.nodes + IndexedElement(2))))))),
          Execute.ConditionalScoreMatches(true, R1, MAIN, Int.MIN_VALUE..2, Execute.ConditionalScoreMatches(true, R0, MAIN, Int.MIN_VALUE..-1, Execute.Run(ManipulateData(DataAccessor(MCX_HEAP, path), DataManipulator.Set(SourceProvider.Value(buildEndListTag())))))),
        )
      })
    }

    // TODO: specialize dispatcher by type
    fun packDispatchProcs(
      procs: List<L.Definition.Function>,
    ): P.Definition.Function {
      return P.Definition.Function(emptyList(), DISPATCH_PROC, run {
        val proc = DataAccessor(MCX, nbtPath { it(NbtType.INT.id)(-1) })
        listOf(
          Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(proc))),
          RemoveData(proc),
        )
      } + procs.sortedBy { it.restore }.map { function ->
        val tag = function.restore!!
        Execute.ConditionalScoreMatches(true, R0, MAIN, tag..tag, Execute.Run(RunFunction(packDefinitionLocation(function.name))))
      })
    }

    // TODO: specialize dispatcher by type
    fun packDispatchFuncs(
      funcs: List<L.Definition.Function>,
    ): P.Definition.Function {
      return P.Definition.Function(emptyList(), DISPATCH_FUNC, listOf(
        Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(DataAccessor(MCX, nbtPath { it(NbtType.COMPOUND.id)(-1)("_") })))),
        RemoveData(DataAccessor(MCX, nbtPath { it(NbtType.COMPOUND.id)(-1) })),
      ) + funcs.sortedBy { it.restore }.map { function ->
        val tag = function.restore!!
        Execute.ConditionalScoreMatches(true, R0, MAIN, tag..tag, Execute.Run(RunFunction(packDefinitionLocation(function.name))))
      })
    }

    operator fun invoke(
      context: Context,
      definition: L.Definition,
    ): P.Definition {
      return Pack(context).packDefinition(definition)
    }
  }
}
