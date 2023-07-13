package mcx.pass.backend

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mcx.ast.Packed
import mcx.ast.Packed.Command
import mcx.ast.Packed.Command.*
import mcx.ast.Packed.Command.Execute.Mode.RESULT
import mcx.ast.Packed.DataAccessor
import mcx.ast.Packed.DataManipulator
import mcx.ast.Packed.NbtNode
import mcx.ast.Packed.NbtNode.*
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

        if (
          L.Modifier.TOP in definition.modifiers &&
          L.Modifier.TEST in definition.modifiers
        ) {
          packTerm(definition.body!!)
          val byteStack = DataAccessor(MCX, nbtPath(Repr.BYTE.id))
          +RemoveData(TEST_CELL)
          +ManipulateData(TEST_CELL, DataManipulator.Set(SourceProvider.From(byteStack)))
          +RemoveData(byteStack)
        } else {
          definition.context.forEach {
            !{ Raw("# context $it") }
            push(it.repr, null)
            packPattern(it)
          }

          definition.params.forEach {
            !{ Raw("# params $it") }
            push(it.repr, null)
            packPattern(it)
          }

          val body = definition.body!!
          packTerm(body)

          definition.params.forEach {
            dropPattern(it, listOf(body.repr))
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
            val remaining = listOf(body.repr)
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
      L.Modifier.BUILTIN -> null
      L.Modifier.TEST    -> P.Modifier.TEST
      L.Modifier.TOP     -> null
    }
  }

  private fun packTerm(
    term: L.Term,
  ) {
    when (term) {
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

      is L.Term.If      -> {
        packTerm(term.scrutinee)

        // TODO: optimize this using function tree
        val scrutinee = nbtPath(term.scrutinee.repr.id)(-1)
        +SetScore(R0, MAIN, 0)
        term.branches.forEach { (binder, name) ->
          // if not yet matched, set R0 to 1
          +Execute.ConditionalScoreMatches(true, R0, MAIN, until(0), Execute.Run(SetScore(R0, MAIN, 1)))
          // if the pattern does not match, set R0 to 0, otherwise R0 remains 1
          matchPattern(binder, scrutinee)
          // if R0 is 1, drop the scrutinee and run the branch, setting R0 to 2
          +Execute.ConditionalScoreMatches(true, R0, MAIN, exact(1), Execute.Run(RunFunction(packDefinitionLocation(name))))
        }

        // adjust stacks
        drop(term.scrutinee.repr, relevant = false)
        push(term.repr, null)
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

  private fun matchPattern(
    pattern: L.Pattern,
    scrutinee: PersistentList<NbtNode>,
  ) {
    when (pattern) {
      is L.Pattern.BoolOf   -> {
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, scrutinee))))
        +Execute.ConditionalScoreMatches(
          true, R1, MAIN, if (pattern.value) until(0) else from(1),
          Execute.Run(SetScore(R0, MAIN, 0))
        )
      }
      is L.Pattern.I32Of    -> {
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, scrutinee))))
        +Execute.ConditionalScoreMatches(
          false, R1, MAIN, exact(pattern.value),
          Execute.Run(SetScore(R0, MAIN, 0))
        )
      }
      is L.Pattern.VecOf    -> {
        if (pattern.elements.isNotEmpty()) {
          +Execute.CheckMatchingData(
            false, DataAccessor(MCX, scrutinee(pattern.elements.lastIndex)),
            Execute.Run(SetScore(R0, MAIN, 0))
          )
        }
        +Execute.CheckMatchingData(
          true, DataAccessor(MCX, scrutinee(pattern.elements.size)),
          Execute.Run(SetScore(R0, MAIN, 0))
        )
        pattern.elements.forEachIndexed { index, element ->
          matchPattern(element, scrutinee(index))
        }
      }
      is L.Pattern.StructOf -> {
        pattern.elements.forEach { (key, element) ->
          matchPattern(element, scrutinee(key))
        }
      }
      is L.Pattern.Var      -> {}
      is L.Pattern.Drop     -> {}
    }
  }

  // ?
  private fun packPattern(
    pattern: L.Pattern,
  ) {
    when (pattern) {
      is L.Pattern.BoolOf   -> {}
      is L.Pattern.I32Of    -> {}
      is L.Pattern.VecOf    -> {
        pattern.elements.forEachIndexed { index, element ->
          push(element.repr, SourceProvider.From(DataAccessor(MCX, nbtPath(NbtType.LIST.id)(-1)(index))))
          packPattern(element)
        }
      }
      is L.Pattern.StructOf -> {
        pattern.elements.forEach { (name, element) ->
          push(element.repr, SourceProvider.From(DataAccessor(MCX, nbtPath(NbtType.COMPOUND.id)(-1)(name))))
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
      is L.Pattern.BoolOf   -> drop(Repr.BYTE, keeps)
      is L.Pattern.I32Of    -> drop(Repr.INT, keeps)
      is L.Pattern.VecOf    -> {
        pattern.elements.reversed().forEach { element -> dropPattern(element, keeps) }
        drop(Repr.LIST, keeps)
      }
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
    return stack.indexOfLast { it == name }.also {
      require(it != -1) {
        "${definition.name}: variable '$name' not found in stack ${stack.joinToString(", ", "[", "]") { name -> name ?: "_" }}"
      }
    } - stack.size
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
    private val DISPATCH_PROC: ResourceLocation = packDefinitionLocation(DefinitionLocation(ModuleLocation(core), ":dispatch_proc"))
    private val DISPATCH_FUNC: ResourceLocation = packDefinitionLocation(DefinitionLocation(ModuleLocation(core), ":dispatch_func"))

    private val MAIN: Packed.Objective = Packed.Objective("mcx")

    private val R0: Packed.ScoreHolder = Packed.ScoreHolder("#0")
    private val R1: Packed.ScoreHolder = Packed.ScoreHolder("#1")

    private val MCX: ResourceLocation = ResourceLocation("mcx", "")
    private val MCX_DATA: ResourceLocation = ResourceLocation("mcx_data", "")
    private val MCX_TEST: ResourceLocation = ResourceLocation("mcx_test", "")

    private val BYTE_TOP: DataAccessor = DataAccessor(MCX, nbtPath(NbtType.BYTE.id)(-1))
    private val INT_TOP: DataAccessor = DataAccessor(MCX, nbtPath(NbtType.INT.id)(-1))
    private val LONG_TOP: DataAccessor = DataAccessor(MCX, nbtPath(NbtType.LONG.id)(-1))
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

    private inline fun exact(value: Int): IntRange {
      return value..value
    }

    private inline fun from(startInclusive: Int): IntRange {
      return startInclusive..Int.MAX_VALUE
    }

    private inline fun until(endInclusive: Int): IntRange {
      return Int.MIN_VALUE..endInclusive
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
        ManipulateData(DataAccessor(MCX_DATA, nbtPath("surrogate_pairs")), DataManipulator.Set(SourceProvider.Value(StringTag(
          ((Char.MIN_HIGH_SURROGATE..Char.MAX_HIGH_SURROGATE) zip (Char.MIN_LOW_SURROGATE..Char.MAX_LOW_SURROGATE)).joinToString("") { (high, low) -> "$high$low" }
        ))))
      ))
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
