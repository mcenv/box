package mcx.pass.backend

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mcx.ast.Packed
import mcx.ast.Packed.Command
import mcx.ast.Packed.Command.*
import mcx.ast.Packed.Command.Execute.Mode.RESULT
import mcx.ast.Packed.Command.Execute.StoreStorage.Type.INT
import mcx.ast.Packed.DataAccessor
import mcx.ast.Packed.DataManipulator
import mcx.ast.Packed.NbtNode
import mcx.ast.Packed.NbtNode.*
import mcx.ast.Packed.Operation.*
import mcx.ast.Packed.SourceProvider
import mcx.ast.common.DefinitionLocation
import mcx.ast.common.ModuleLocation
import mcx.ast.common.Repr
import mcx.data.NbtType
import mcx.data.ResourceLocation
import mcx.pass.Context
import mcx.pass.core
import mcx.util.green
import mcx.util.nbt.*
import mcx.ast.Lifted as L
import mcx.ast.Packed as P

// TODO: refactor
@Suppress("NOTHING_TO_INLINE")
class Pack private constructor(
  private val context: Context,
  private val definition: L.Definition,
) {
  private val commands: MutableList<Command> = mutableListOf()
  private val stacks: Map<Repr, MutableList<String?>> = Repr.entries.associateWith { mutableListOf() }

  private fun packDefinition(): P.Definition {
    val modifiers = definition.modifiers.mapNotNull { packModifier(it) }
    val path = packDefinitionLocation(definition.name)
    return when (definition) {
      is L.Definition.Function -> {
        !{ Raw("# function ${definition.name}\n") }

        if (L.Modifier.TEST in definition.modifiers) {
          packTerm(definition.body!!)
          +RemoveData(TEST_CELL)
          +ManipulateData(TEST_CELL, DataManipulator.Set(SourceProvider.From(BYTE_TOP)))
          drop(Repr.BYTE)
        } else {
          definition.params.forEach {
            push(it.repr, null)
            packPattern(it)
          }

          val body = definition.body!!
          packTerm(body)

          val drop = L.Modifier.NO_DROP !in definition.modifiers
          if (drop) {
            definition.params.forEach { dropPattern(it, listOf(body.repr)) }
          }

          if (definition.restore != null) {
            +SetScore(R0, MAIN, definition.restore)
          }

          // validate stacks
          if (context.config.debug.verbose) {
            !{ Raw("") }
            stacks.forEach { stack ->
              !{ Raw("# ${stack.key.toString().padEnd(10)}: ${stack.value.joinToString(", ", "[", "]")}") }
            }
            val remaining = if (drop) listOf(body.repr) else definition.params.map { it.repr } + body.repr
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
        drop(Repr.BYTE)
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
        push(term.repr, null)
      }

      is L.Term.I8Of       -> {
        push(Repr.BYTE, SourceProvider.Value(ByteTag(term.value)))
      }

      is L.Term.I16Of      -> {
        push(Repr.SHORT, SourceProvider.Value(ShortTag(term.value)))
      }

      is L.Term.I32Of      -> {
        push(Repr.INT, SourceProvider.Value(IntTag(term.value)))
      }

      is L.Term.I64Of      -> {
        push(Repr.LONG, SourceProvider.Value(LongTag(term.value)))
      }

      is L.Term.F32Of      -> {
        push(Repr.FLOAT, SourceProvider.Value(FloatTag(term.value)))
      }

      is L.Term.F64Of      -> {
        push(Repr.DOUBLE, SourceProvider.Value(DoubleTag(term.value)))
      }

      is L.Term.Wtf16Of -> {
        push(Repr.STRING, SourceProvider.Value(StringTag(term.value)))
      }

      is L.Term.I8ArrayOf  -> {
        val elements = term.elements.map { (it as? L.Term.I8Of)?.value ?: 0 }
        push(Repr.BYTE_ARRAY, SourceProvider.Value(ByteArrayTag(elements)))
        term.elements.forEachIndexed { index, element ->
          if (element !is L.Term.I8Of) {
            packTerm(element)
            +ManipulateData(DataAccessor(MCX, nbtPath(NbtType.BYTE_ARRAY.id)(-1)(index)), DataManipulator.Set(SourceProvider.From(BYTE_TOP)))
            drop(Repr.BYTE)
          }
        }
      }

      is L.Term.I32ArrayOf -> {
        val elements = term.elements.map { (it as? L.Term.I32Of)?.value ?: 0 }
        push(Repr.INT_ARRAY, SourceProvider.Value(IntArrayTag(elements)))
        term.elements.forEachIndexed { index, element ->
          if (element !is L.Term.I32Of) {
            packTerm(element)
            +ManipulateData(DataAccessor(MCX, nbtPath(NbtType.INT_ARRAY.id)(-1)(index)), DataManipulator.Set(SourceProvider.From(INT_TOP)))
            drop(Repr.INT)
          }
        }
      }

      is L.Term.I64ArrayOf -> {
        val elements = term.elements.map { (it as? L.Term.I64Of)?.value ?: 0 }
        push(Repr.LONG_ARRAY, SourceProvider.Value(LongArrayTag(elements)))
        term.elements.forEachIndexed { index, element ->
          if (element !is L.Term.I64Of) {
            packTerm(element)
            +ManipulateData(DataAccessor(MCX, nbtPath(NbtType.LONG_ARRAY.id)(-1)(index)), DataManipulator.Set(SourceProvider.From(LONG_TOP)))
            drop(Repr.LONG)
          }
        }
      }

      is L.Term.VecOf      -> {
        push(Repr.LIST, SourceProvider.Value(buildByteListTag { } /* TODO: use end list tag */))
        if (term.elements.isNotEmpty()) {
          val elementRepr = term.elements.first().repr
          term.elements.forEach { element ->
            packTerm(element)
            val index = if (elementRepr == Repr.LIST) -2 else -1
            +ManipulateData(DataAccessor(MCX, nbtPath(NbtType.LIST.id)(index)), DataManipulator.Append(SourceProvider.From(DataAccessor(MCX, nbtPath(elementRepr.id)(-1)))))
            drop(elementRepr)
          }
        }
      }

      is L.Term.StructOf   -> {
        push(Repr.COMPOUND, SourceProvider.Value(buildCompoundTag { }))
        term.elements.forEach { (key, element) ->
          packTerm(element)
          val elementRepr = element.repr
          val index = if (elementRepr == Repr.COMPOUND) -2 else -1
          +ManipulateData(DataAccessor(MCX, nbtPath(NbtType.COMPOUND.id)(index)(key)), DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, nbtPath(elementRepr.id)(-1)))))
          drop(elementRepr)
        }
      }

      is L.Term.RefOf      -> {
        push(Repr.REF, SourceProvider.Value(IntTag(0)))
        packTerm(term.element)
        val elementRepr = term.element.repr
        val index = if (elementRepr == Repr.REF) -2 else -1
        +Execute.StoreScore(RESULT, R0, MAIN, Execute.StoreStorage(RESULT, DataAccessor(MCX, nbtPath(Repr.REF.id)(index)), INT, 1.0, Execute.Run(AddScore(R0, FREE, 1))))
        +RunFunction(TOUCH)
        +ManipulateData(HEAP_CELL, DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, nbtPath(elementRepr.id)(-1)))))
        drop(elementRepr)
      }

      is L.Term.ProcOf     -> {
        push(Repr.INT, SourceProvider.Value(IntTag(term.function.restore!!)))
      }

      is L.Term.FuncOf     -> {
        push(Repr.COMPOUND, SourceProvider.Value(buildCompoundTag { put("_", term.tag) }))
        term.entries.forEach { (name, repr) ->
          val index = this[name, repr]
          +ManipulateData(DataAccessor(MCX, nbtPath(NbtType.COMPOUND.id)(-1)(name)), DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, nbtPath(repr.id)(index)))))
        }
      }

      is L.Term.Apply   -> {
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

        push(term.repr, null)
        val keeps = listOf(term.repr)
        term.args.forEach { drop(it.repr, keeps, false) }
      }

      is L.Term.Command -> {
        +Raw(term.element)
        push(term.repr, null)
      }

      is L.Term.Let     -> {
        packTerm(term.init)
        packPattern(term.binder)
        packTerm(term.body)

        dropPattern(term.binder, listOf(term.body.repr))
      }

      is L.Term.Proj    -> {
        // TODO
        push(term.repr, null)
        +Raw("TODO: $term")
      }

      is L.Term.Var     -> {
        val repr = term.repr
        val index = this[term.name, term.repr]
        push(repr, SourceProvider.From(DataAccessor(MCX, nbtPath(repr.id)(index))))
      }

      is L.Term.Def     -> {
        +RunFunction(packDefinitionLocation(term.name))
        push(term.repr, null)
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
          push(element.repr, SourceProvider.From(DataAccessor(MCX, nbtPath(NbtType.COMPOUND.id)(-1)(name)))) // TODO: avoid immediate push
          packPattern(element)
        }
      }

      is L.Pattern.Var      -> bind(pattern.name, pattern.repr)
      is L.Pattern.Drop     -> {}
    }
  }

  private fun dropPattern(
    pattern: L.Pattern,
    keeps: List<Repr>,
  ) {
    when (pattern) {
      is L.Pattern.I32Of    -> drop(Repr.INT, keeps)
      is L.Pattern.StructOf -> {
        pattern.elements.entries.reversed().forEach { (_, element) -> dropPattern(element, keeps) }
        drop(Repr.COMPOUND, keeps)
      }

      is L.Pattern.Var      -> drop(pattern.repr, keeps)
      is L.Pattern.Drop     -> drop(pattern.repr, keeps)
    }
  }

  private fun push(
    repr: Repr,
    source: SourceProvider?,
  ) {
    if (source != null && repr != Repr.END) {
      !{ Raw("# push ${repr.id}") }
      +ManipulateData(DataAccessor(MCX, nbtPath(repr.id)), DataManipulator.Append(source))
    }
    getStack(repr) += null
  }

  private fun drop(
    drop: Repr,
    keeps: List<Repr> = emptyList(),
    relevant: Boolean = true,
  ) {
    val stack = getStack(drop)
    val index = -1 - keeps.count { it == drop }
    if (relevant && drop != Repr.END) {
      !{ Raw("# drop ${drop.id} under ${keeps.joinToString(", ", "[", "]") { it.id }}") }
      +RemoveData(DataAccessor(MCX, nbtPath(drop.id)(index)))
      when (drop) {
        Repr.REF -> {
          +PerformOperation(R0, MAIN, ASSIGN, R0, FREE)
          +RemoveScore(R0, FREE, 1)
          +RunFunction(TOUCH)
          +RemoveData(HEAP_CELL)
        }
        else     -> {}
      }
    }
    if (stack.size + index >= stack.size || stack.size + index < 0) {
      println("WARNING: [$definition]: dropping $drop at $index, but it's already null")
      stacks.forEach { stack ->
        println("# ${stack.key.toString().padEnd(10)}: ${stack.value.joinToString(", ", "[", "]")}")
      }
    }
    stack.removeAt(stack.size + index)
  }

  private fun bind(
    name: String,
    repr: Repr,
  ) {
    val stack = getStack(repr)
    val index = stack.indexOfLast { it == null }
    stack[index] = name
  }

  operator fun get(
    name: String,
    repr: Repr,
  ): Int {
    val stack = getStack(repr)
    return stack.indexOfLast { it == name }.also { require(it != -1) } - stack.size
  }

  private fun getStack(
    repr: Repr,
  ): MutableList<String?> {
    return stacks[repr]!!
  }

  private inline operator fun Command.unaryPlus() {
    commands += this
  }

  private inline operator fun (() -> Command).not() {
    if (context.config.debug.verbose) {
      +this()
    }
  }

  companion object {
    private val nbtPath: PersistentList<NbtNode> = persistentListOf()

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
    private val MCX_DATA: ResourceLocation = ResourceLocation("mcx_data", "")
    private val MCX_HEAP: ResourceLocation = ResourceLocation("mcx_heap", "")
    private val MCX_TEST: ResourceLocation = ResourceLocation("mcx_test", "")

    private val BYTE_TOP: DataAccessor = DataAccessor(MCX, nbtPath(NbtType.BYTE.id)(-1))
    private val INT_TOP: DataAccessor = DataAccessor(MCX, nbtPath(NbtType.INT.id)(-1))
    private val LONG_TOP: DataAccessor = DataAccessor(MCX, nbtPath(NbtType.LONG.id)(-1))
    private val HEAP_CELL: DataAccessor = DataAccessor(MCX_HEAP, nbtPath("heap")(-2)(-2)(-2)(-2)(-2)(-2)(-2)(-2)(-2)(-2)(-2)(-2)(-2)(-2)(-2)(-2)())
    private val TEST_CELL: DataAccessor = DataAccessor(MCX_TEST, nbtPath("test"))

    fun packDefinitionLocation(
      location: DefinitionLocation,
    ): ResourceLocation {
      return ResourceLocation(location.module.parts.first(), (location.module.parts.drop(1) + escape(location.name)).joinToString("/"))
    }

    private fun escape(
      string: String,
    ): String {
      return string.encodeToByteArray().joinToString("") {
        when (val char = it.toInt().toChar()) {
          in 'a'..'z', in '0'..'9', '_', '-' -> char.toString()
          else                               -> ".${it.toUByte().toString(Character.MAX_RADIX)}"
        }
      }
    }

    private inline operator fun PersistentList<NbtNode>.invoke(pattern: CompoundTag): PersistentList<NbtNode> {
      return this + MatchElement(pattern)
    }

    private inline operator fun PersistentList<NbtNode>.invoke(): PersistentList<NbtNode> {
      return this + AllElements
    }

    private inline operator fun PersistentList<NbtNode>.invoke(index: Int): PersistentList<NbtNode> {
      return this + IndexedElement(index)
    }

    private inline operator fun PersistentList<NbtNode>.invoke(name: String, pattern: CompoundTag): PersistentList<NbtNode> {
      return this + MatchObject(name, pattern)
    }

    private inline operator fun PersistentList<NbtNode>.invoke(name: String): PersistentList<NbtNode> {
      return this + CompoundChild(name)
    }

    fun packInit(): P.Definition.Function {
      return P.Definition.Function(emptyList(), INIT, listOf(
        Raw("say ${green("Initializing")} mcx"),
        Raw("gamerule maxCommandChainLength ${Int.MAX_VALUE}"),
        Raw("scoreboard objectives remove ${MAIN.name}"),
        Raw("scoreboard objectives add ${MAIN.name} dummy"),
        Raw("scoreboard objectives remove ${FREE.name}"),
        Raw("scoreboard objectives add ${FREE.name} dummy"),
        Raw("scoreboard objectives remove ${`65536`.name}"),
        Raw("scoreboard objectives add ${`65536`.name} dummy"),
        SetScore(R0, FREE, -1),
        SetScore(R0, `65536`, 65536),
        ManipulateData(DataAccessor(MCX_HEAP, nbtPath("branch")), DataManipulator.Set(SourceProvider.Value(buildListListTag { add(buildEndListTag()); add(buildEndListTag()) }))),
        *(0..<16).map { count ->
          ManipulateData(DataAccessor(MCX_HEAP, (0..<count).fold(nbtPath("heap")) { acc, _ -> acc() }), DataManipulator.Set(SourceProvider.From(DataAccessor(MCX_HEAP, nbtPath("branch")))))
        }.toTypedArray(),
        RemoveData(DataAccessor(MCX_HEAP, nbtPath("branch"))),
        ManipulateData(DataAccessor(MCX_DATA, nbtPath("surrogate_pairs")), DataManipulator.Set(SourceProvider.Value(StringTag(
          ((Char.MIN_HIGH_SURROGATE..Char.MAX_HIGH_SURROGATE) zip (Char.MIN_LOW_SURROGATE..Char.MAX_LOW_SURROGATE)).joinToString("") { (high, low) -> "$high$low" }
        ))))
      ))
    }

    fun packTouch(): P.Definition.Function {
      return P.Definition.Function(emptyList(), TOUCH, (0..<16).flatMap { depth ->
        val path = (0..<depth).fold(nbtPath("heap")) { acc, _ -> acc(-2) }
        listOf(
          PerformOperation(R0, MAIN, if (depth == 0) MUL else ADD, R0, if (depth == 0) `65536` else MAIN),
          RemoveData(DataAccessor(MCX_HEAP, path(2))),
          Execute.ConditionalScoreMatches(true, R0, MAIN, Int.MIN_VALUE..-1, Execute.Run(ManipulateData(DataAccessor(MCX_HEAP, path), DataManipulator.Append(SourceProvider.Value(buildEndListTag()))))),
        )
      })
    }

    // TODO: specialize dispatcher by type
    fun packDispatchProcs(
      procs: List<L.Definition.Function>,
    ): P.Definition.Function {
      return P.Definition.Function(emptyList(), DISPATCH_PROC, run {
        val proc = DataAccessor(MCX, nbtPath(NbtType.INT.id)(-1))
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
        Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(DataAccessor(MCX, nbtPath(NbtType.COMPOUND.id)(-1)("_"))))),
        RemoveData(DataAccessor(MCX, nbtPath(NbtType.COMPOUND.id)(-1))),
      ) + funcs.sortedBy { it.restore }.map { function ->
        val tag = function.restore!!
        Execute.ConditionalScoreMatches(true, R0, MAIN, tag..tag, Execute.Run(RunFunction(packDefinitionLocation(function.name))))
      })
    }

    operator fun invoke(
      context: Context,
      definition: L.Definition,
    ): P.Definition {
      return Pack(context, definition).packDefinition()
    }
  }
}
