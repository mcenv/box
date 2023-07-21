package mcx.pass.backend

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mcx.ast.Packed
import mcx.ast.Packed.Command
import mcx.ast.Packed.Command.*
import mcx.ast.Packed.Command.Execute.ConditionalScore.Comparator.*
import mcx.ast.Packed.Command.Execute.Mode.RESULT
import mcx.ast.Packed.Command.Execute.Mode.SUCCESS
import mcx.ast.Packed.Command.Execute.StoreStorage.Type
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
import mcx.pass.Builtin
import mcx.pass.Context
import mcx.pass.core
import mcx.util.green
import mcx.util.nbt.*
import mcx.util.unreachable
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
      is L.Term.I8Of       -> push(Repr.BYTE, SourceProvider.Value(ByteTag(term.value)))

      is L.Term.I16Of      -> push(Repr.SHORT, SourceProvider.Value(ShortTag(term.value)))

      is L.Term.I32Of      -> push(Repr.INT, SourceProvider.Value(IntTag(term.value)))

      is L.Term.I64Of      -> push(Repr.LONG, SourceProvider.Value(LongTag(term.value)))

      is L.Term.F32Of      -> push(Repr.FLOAT, SourceProvider.Value(FloatTag(term.value)))

      is L.Term.F64Of      -> push(Repr.DOUBLE, SourceProvider.Value(DoubleTag(term.value)))

      is L.Term.Wtf16Of    -> push(Repr.STRING, SourceProvider.Value(StringTag(term.value)))

      is L.Term.I8ArrayOf  -> {
        val elements = term.elements.map { (it as? L.Term.I8Of)?.value ?: 0 }
        push(Repr.BYTE_ARRAY, SourceProvider.Value(ByteArrayTag(elements)))
        term.elements.forEachIndexed { index, element ->
          if (element !is L.Term.I8Of) {
            packTerm(element)
            +ManipulateData(DataAccessor(MCX, `BYTE_ARRAY{-1}`(index)), DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, `BYTE{-1}`))))
            drop(Repr.BYTE)
          }
        }
      }

      is L.Term.I32ArrayOf -> {
        push(Repr.INT_ARRAY, SourceProvider.Value(IntArrayTag(term.elements.map { (it as? L.Term.I32Of)?.value ?: 0 })))
        term.elements.forEachIndexed { index, element ->
          if (element !is L.Term.I32Of) {
            packTerm(element)
            +ManipulateData(DataAccessor(MCX, `INT_ARRAY{-1}`(index)), DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, `INT{-1}`))))
            drop(Repr.INT)
          }
        }
      }

      is L.Term.I64ArrayOf -> {
        push(Repr.LONG_ARRAY, SourceProvider.Value(LongArrayTag(term.elements.map { (it as? L.Term.I64Of)?.value ?: 0L })))
        term.elements.forEachIndexed { index, element ->
          if (element !is L.Term.I64Of) {
            packTerm(element)
            +ManipulateData(DataAccessor(MCX, `LONG_ARRAY{-1}`(index)), DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, `LONG{-1}`))))
            drop(Repr.LONG)
          }
        }
      }

      is L.Term.ListOf     -> {
        val initializers = mutableListOf<() -> Unit>()
        val template = buildListOf(term, nbtPath, initializers)
        push(Repr.LIST, SourceProvider.Value(template))
        initializers.forEach { it() }
      }

      is L.Term.CompoundOf -> {
        val initializers = mutableListOf<() -> Unit>()
        val template = buildCompoundOf(term, nbtPath, initializers)
        push(Repr.COMPOUND, SourceProvider.Value(template))
        initializers.forEach { it() }
      }

      is L.Term.ProcOf     -> push(Repr.INT, SourceProvider.Value(IntTag(term.function.restore!!)))

      is L.Term.FuncOf     -> {
        push(Repr.COMPOUND, SourceProvider.Value(buildCompoundTag { put("_", term.tag) }))
        term.entries.forEach { (name, repr) ->
          val index = this[name, repr]
          +ManipulateData(DataAccessor(MCX, `COMPOUND{-1}`(name)), DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, nbtPath(repr.id)(index)))))
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

      is L.Term.Get     -> {
        val repr = term.repr
        val index = this[term.name, term.repr]
        push(repr, SourceProvider.From(DataAccessor(MCX, nbtPath(repr.id)(index))))
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

      is L.Term.Var        -> {
        val repr = term.repr
        val index = this[term.name, term.repr]
        push(repr, SourceProvider.From(DataAccessor(MCX, nbtPath(repr.id)(index))))
      }

      is L.Term.Def        -> {
        +RunFunction(packDefinitionLocation(term.name))
        push(term.repr, null)
      }

      is L.Term.Builtin    -> {
        !{ Raw("# builtin ${term.builtin.name}") }
        packBuiltin(term.builtin)
        push(term.repr, null)
      }
    }
  }

  private fun packBuiltin(builtin: Builtin) {
    when (builtin.name) {
      "prelude::++"   -> unreachable()
      "i8::="         -> {
        +Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(DataAccessor(MCX, `BYTE{-2}`))))
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, `BYTE{-1}`))))
        +ManipulateData(DataAccessor(MCX, BYTE), DataManipulator.Append(SourceProvider.Value(ByteTag(0))))
        +Execute.ConditionalScore(true, R0, MAIN, EQ, R1, MAIN, Execute.Run(ManipulateData(DataAccessor(MCX, `BYTE{-1}`), DataManipulator.Set(SourceProvider.Value(ByteTag(1))))))
      }
      "i8::to_i16"    -> {
        +ManipulateData(DataAccessor(MCX, SHORT), DataManipulator.Append(SourceProvider.Value(ShortTag(0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `SHORT{-1}`), Type.SHORT, 1.0, Execute.Run(GetData(DataAccessor(MCX, `BYTE{-1}`))))
      }
      "i8::to_i32"    -> {
        +ManipulateData(DataAccessor(MCX, INT), DataManipulator.Append(SourceProvider.Value(IntTag(0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `INT{-1}`), Type.INT, 1.0, Execute.Run(GetData(DataAccessor(MCX, `BYTE{-1}`))))
      }
      "i8::to_i64"    -> {
        +ManipulateData(DataAccessor(MCX, LONG), DataManipulator.Append(SourceProvider.Value(LongTag(0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `LONG{-1}`), Type.LONG, 1.0, Execute.Run(GetData(DataAccessor(MCX, `BYTE{-1}`))))
      }
      "i8::to_f32"    -> {
        +ManipulateData(DataAccessor(MCX, FLOAT), DataManipulator.Append(SourceProvider.Value(FloatTag(0.0f))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `FLOAT{-1}`), Type.FLOAT, 1.0, Execute.Run(GetData(DataAccessor(MCX, `BYTE{-1}`))))
      }
      "i8::to_f64"    -> {
        +ManipulateData(DataAccessor(MCX, DOUBLE), DataManipulator.Append(SourceProvider.Value(DoubleTag(0.0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `DOUBLE{-1}`), Type.DOUBLE, 1.0, Execute.Run(GetData(DataAccessor(MCX, `BYTE{-1}`))))
      }
      "i8::to_wtf16"  -> {
        +ManipulateData(DataAccessor(MCX, STRING), DataManipulator.Append(SourceProvider.String(DataAccessor(MCX, `BYTE{-1}`))))
      }
      "i16::="        -> {
        +Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(DataAccessor(MCX, `SHORT{-2}`))))
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, `SHORT{-1}`))))
        +ManipulateData(DataAccessor(MCX, BYTE), DataManipulator.Append(SourceProvider.Value(ByteTag(0))))
        +Execute.ConditionalScore(true, R0, MAIN, EQ, R1, MAIN, Execute.Run(ManipulateData(DataAccessor(MCX, `BYTE{-1}`), DataManipulator.Set(SourceProvider.Value(ByteTag(1))))))
      }
      "i16::to_i8"    -> {
        +ManipulateData(DataAccessor(MCX, BYTE), DataManipulator.Append(SourceProvider.Value(ByteTag(0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `BYTE{-1}`), Type.BYTE, 1.0, Execute.Run(GetData(DataAccessor(MCX, `SHORT{-1}`))))
      }
      "i16::to_i32"   -> {
        +ManipulateData(DataAccessor(MCX, INT), DataManipulator.Append(SourceProvider.Value(IntTag(0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `INT{-1}`), Type.INT, 1.0, Execute.Run(GetData(DataAccessor(MCX, `SHORT{-1}`))))
      }
      "i16::to_i64"   -> {
        +ManipulateData(DataAccessor(MCX, LONG), DataManipulator.Append(SourceProvider.Value(LongTag(0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `LONG{-1}`), Type.LONG, 1.0, Execute.Run(GetData(DataAccessor(MCX, `SHORT{-1}`))))
      }
      "i16::to_f32"   -> {
        +ManipulateData(DataAccessor(MCX, FLOAT), DataManipulator.Append(SourceProvider.Value(FloatTag(0.0f))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `FLOAT{-1}`), Type.FLOAT, 1.0, Execute.Run(GetData(DataAccessor(MCX, `SHORT{-1}`))))
      }
      "i16::to_f64"   -> {
        +ManipulateData(DataAccessor(MCX, DOUBLE), DataManipulator.Append(SourceProvider.Value(DoubleTag(0.0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `DOUBLE{-1}`), Type.DOUBLE, 1.0, Execute.Run(GetData(DataAccessor(MCX, `SHORT{-1}`))))
      }
      "i16::to_wtf16" -> {
        +ManipulateData(DataAccessor(MCX, STRING), DataManipulator.Append(SourceProvider.String(DataAccessor(MCX, `SHORT{-1}`))))
      }
      "i32::+"        -> {
        +Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-2}`))))
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-1}`))))
        +ManipulateData(DataAccessor(MCX, INT), DataManipulator.Append(SourceProvider.Value(IntTag(0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `INT{-1}`), Type.INT, 1.0, Execute.Run(PerformOperation(R0, MAIN, ADD, R1, MAIN)))
      }
      "i32::-"        -> {
        +Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-2}`))))
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-1}`))))
        +ManipulateData(DataAccessor(MCX, INT), DataManipulator.Append(SourceProvider.Value(IntTag(0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `INT{-1}`), Type.INT, 1.0, Execute.Run(PerformOperation(R0, MAIN, SUB, R1, MAIN)))
      }
      "i32::*"        -> {
        +Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-2}`))))
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-1}`))))
        +ManipulateData(DataAccessor(MCX, INT), DataManipulator.Append(SourceProvider.Value(IntTag(0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `INT{-1}`), Type.INT, 1.0, Execute.Run(PerformOperation(R0, MAIN, MUL, R1, MAIN)))
      }
      "i32::/"        -> {
        +Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-2}`))))
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-1}`))))
        +ManipulateData(DataAccessor(MCX, INT), DataManipulator.Append(SourceProvider.Value(IntTag(0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `INT{-1}`), Type.INT, 1.0, Execute.Run(PerformOperation(R0, MAIN, DIV, R1, MAIN)))
      }
      "i32::%"        -> {
        +Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-2}`))))
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-1}`))))
        +ManipulateData(DataAccessor(MCX, INT), DataManipulator.Append(SourceProvider.Value(IntTag(0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `INT{-1}`), Type.INT, 1.0, Execute.Run(PerformOperation(R0, MAIN, MOD, R1, MAIN)))
      }
      "i32::min"      -> {
        +Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-2}`))))
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-1}`))))
        +ManipulateData(DataAccessor(MCX, INT), DataManipulator.Append(SourceProvider.Value(IntTag(0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `INT{-1}`), Type.INT, 1.0, Execute.Run(PerformOperation(R0, MAIN, MIN, R1, MAIN)))
      }
      "i32::max"      -> {
        +Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-2}`))))
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-1}`))))
        +ManipulateData(DataAccessor(MCX, INT), DataManipulator.Append(SourceProvider.Value(IntTag(0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `INT{-1}`), Type.INT, 1.0, Execute.Run(PerformOperation(R0, MAIN, MAX, R1, MAIN)))
      }
      "i32::="        -> {
        +Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-2}`))))
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-1}`))))
        +ManipulateData(DataAccessor(MCX, BYTE), DataManipulator.Append(SourceProvider.Value(ByteTag(0))))
        +Execute.ConditionalScore(true, R0, MAIN, EQ, R1, MAIN, Execute.Run(ManipulateData(DataAccessor(MCX, `BYTE{-1}`), DataManipulator.Set(SourceProvider.Value(ByteTag(1))))))
      }
      "i32::<"        -> {
        +Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-2}`))))
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-1}`))))
        +ManipulateData(DataAccessor(MCX, BYTE), DataManipulator.Append(SourceProvider.Value(ByteTag(0))))
        +Execute.ConditionalScore(true, R0, MAIN, LT, R1, MAIN, Execute.Run(ManipulateData(DataAccessor(MCX, `BYTE{-1}`), DataManipulator.Set(SourceProvider.Value(ByteTag(1))))))
      }
      "i32::<="       -> {
        +Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-2}`))))
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-1}`))))
        +ManipulateData(DataAccessor(MCX, BYTE), DataManipulator.Append(SourceProvider.Value(ByteTag(0))))
        +Execute.ConditionalScore(true, R0, MAIN, LE, R1, MAIN, Execute.Run(ManipulateData(DataAccessor(MCX, `BYTE{-1}`), DataManipulator.Set(SourceProvider.Value(ByteTag(1))))))
      }
      "i32::>"        -> {
        +Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-2}`))))
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-1}`))))
        +ManipulateData(DataAccessor(MCX, BYTE), DataManipulator.Append(SourceProvider.Value(ByteTag(0))))
        +Execute.ConditionalScore(true, R0, MAIN, GT, R1, MAIN, Execute.Run(ManipulateData(DataAccessor(MCX, `BYTE{-1}`), DataManipulator.Set(SourceProvider.Value(ByteTag(1))))))
      }
      "i32::>="       -> {
        +Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-2}`))))
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-1}`))))
        +ManipulateData(DataAccessor(MCX, BYTE), DataManipulator.Append(SourceProvider.Value(ByteTag(0))))
        +Execute.ConditionalScore(true, R0, MAIN, GE, R1, MAIN, Execute.Run(ManipulateData(DataAccessor(MCX, `BYTE{-1}`), DataManipulator.Set(SourceProvider.Value(ByteTag(1))))))
      }
      "i32::!="       -> {
        +Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-2}`))))
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, `INT{-1}`))))
        +ManipulateData(DataAccessor(MCX, BYTE), DataManipulator.Append(SourceProvider.Value(ByteTag(1))))
        +Execute.ConditionalScore(true, R0, MAIN, EQ, R1, MAIN, Execute.Run(ManipulateData(DataAccessor(MCX, `BYTE{-1}`), DataManipulator.Set(SourceProvider.Value(ByteTag(0))))))
      }
      "i32::to_i8"    -> {
        +ManipulateData(DataAccessor(MCX, BYTE), DataManipulator.Append(SourceProvider.Value(ByteTag(0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `BYTE{-1}`), Type.BYTE, 1.0, Execute.Run(GetData(DataAccessor(MCX, `INT{-1}`))))
      }
      "i32::to_i16"   -> {
        +ManipulateData(DataAccessor(MCX, SHORT), DataManipulator.Append(SourceProvider.Value(ShortTag(0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `SHORT{-1}`), Type.SHORT, 1.0, Execute.Run(GetData(DataAccessor(MCX, `INT{-1}`))))
      }
      "i32::to_i64"   -> {
        +ManipulateData(DataAccessor(MCX, LONG), DataManipulator.Append(SourceProvider.Value(LongTag(0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `LONG{-1}`), Type.LONG, 1.0, Execute.Run(GetData(DataAccessor(MCX, `INT{-1}`))))
      }
      "i32::to_f32"   -> {
        +ManipulateData(DataAccessor(MCX, FLOAT), DataManipulator.Append(SourceProvider.Value(LongTag(0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `FLOAT{-1}`), Type.FLOAT, 1.0, Execute.Run(GetData(DataAccessor(MCX, `INT{-1}`))))
      }
      "i32::to_f64"   -> {
        +ManipulateData(DataAccessor(MCX, DOUBLE), DataManipulator.Append(SourceProvider.Value(LongTag(0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `DOUBLE{-1}`), Type.DOUBLE, 1.0, Execute.Run(GetData(DataAccessor(MCX, `INT{-1}`))))
      }
      "i32::to_wtf16" -> {
        +ManipulateData(DataAccessor(MCX, STRING), DataManipulator.Append(SourceProvider.String(DataAccessor(MCX, `INT{-1}`))))
      }
      "i64::!="       -> {
        +ManipulateData(DataAccessor(MCX, BYTE), DataManipulator.Append(SourceProvider.Value(ByteTag(0))))
        +ManipulateData(DataAccessor(MCX, LONG), DataManipulator.Append(SourceProvider.From(DataAccessor(MCX, `LONG{-2}`))))
        +Execute.StoreStorage(SUCCESS, DataAccessor(MCX, `BYTE{-1}`), Type.BYTE, 1.0, Execute.Run(ManipulateData(DataAccessor(MCX, `LONG{-1}`), DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, `LONG{-2}`))))))
      }
      "i64::to_wtf16" -> {
        +ManipulateData(DataAccessor(MCX, STRING), DataManipulator.Append(SourceProvider.String(DataAccessor(MCX, `LONG{-1}`))))
      }
      "f32::!="       -> {
        +ManipulateData(DataAccessor(MCX, BYTE), DataManipulator.Append(SourceProvider.Value(ByteTag(0))))
        +ManipulateData(DataAccessor(MCX, FLOAT), DataManipulator.Append(SourceProvider.From(DataAccessor(MCX, `FLOAT{-2}`))))
        +Execute.StoreStorage(SUCCESS, DataAccessor(MCX, `BYTE{-1}`), Type.BYTE, 1.0, Execute.Run(ManipulateData(DataAccessor(MCX, `FLOAT{-1}`), DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, `FLOAT{-2}`))))))
      }
      "f32::to_wtf16" -> {
        +ManipulateData(DataAccessor(MCX, STRING), DataManipulator.Append(SourceProvider.String(DataAccessor(MCX, `FLOAT{-1}`))))
      }
      "f64::!="       -> {
        +ManipulateData(DataAccessor(MCX, BYTE), DataManipulator.Append(SourceProvider.Value(ByteTag(0))))
        +ManipulateData(DataAccessor(MCX, DOUBLE), DataManipulator.Append(SourceProvider.From(DataAccessor(MCX, `DOUBLE{-2}`))))
        +Execute.StoreStorage(SUCCESS, DataAccessor(MCX, `BYTE{-1}`), Type.BYTE, 1.0, Execute.Run(ManipulateData(DataAccessor(MCX, `DOUBLE{-1}`), DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, `DOUBLE{-2}`))))))
      }
      "f64::to_wtf16" -> {
        +ManipulateData(DataAccessor(MCX, STRING), DataManipulator.Append(SourceProvider.String(DataAccessor(MCX, `DOUBLE{-1}`))))
      }
      "wtf16::!="     -> {
        +ManipulateData(DataAccessor(MCX, BYTE), DataManipulator.Append(SourceProvider.Value(ByteTag(0))))
        +ManipulateData(DataAccessor(MCX, STRING), DataManipulator.Append(SourceProvider.From(DataAccessor(MCX, `STRING{-2}`))))
        +Execute.StoreStorage(SUCCESS, DataAccessor(MCX, `BYTE{-1}`), Type.BYTE, 1.0, Execute.Run(ManipulateData(DataAccessor(MCX, `STRING{-1}`), DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, `STRING{-2}`))))))
      }
      "wtf16::size"   -> {
        +ManipulateData(DataAccessor(MCX, INT), DataManipulator.Append(SourceProvider.Value(IntTag(0))))
        +Execute.StoreStorage(RESULT, DataAccessor(MCX, `INT{-1}`), Type.INT, 1.0, Execute.Run(GetData(DataAccessor(MCX, `STRING{-1}`))))
      }
      else            -> error("Undefined builtin: ${builtin.name}")
    }
  }

  private fun buildDefault(term: L.Term): Tag {
    return when (term.repr) {
      Repr.END        -> unreachable()
      Repr.BYTE       -> ByteTag(0)
      Repr.SHORT      -> ShortTag(0)
      Repr.INT        -> IntTag(0)
      Repr.LONG       -> LongTag(0L)
      Repr.FLOAT      -> FloatTag(0.0f)
      Repr.DOUBLE     -> DoubleTag(0.0)
      Repr.STRING     -> StringTag("")
      Repr.BYTE_ARRAY -> ByteArrayTag(emptyList())
      Repr.INT_ARRAY  -> IntArrayTag(emptyList())
      Repr.LONG_ARRAY -> LongArrayTag(emptyList())
      Repr.LIST       -> buildListTag { }
      Repr.COMPOUND   -> buildCompoundTag { }
    }
  }

  private fun buildListOf(
    term: L.Term.ListOf,
    target: PersistentList<NbtNode>,
    initializers: MutableList<() -> Unit>,
  ): Tag {
    return buildListTag {
      term.elements.forEachIndexed { index, element ->
        when (element) {
          is L.Term.I8Of       -> add(ByteTag(element.value))
          is L.Term.I16Of      -> add(ShortTag(element.value))
          is L.Term.I32Of      -> add(IntTag(element.value))
          is L.Term.I64Of      -> add(LongTag(element.value))
          is L.Term.F32Of      -> add(FloatTag(element.value))
          is L.Term.F64Of      -> add(DoubleTag(element.value))
          is L.Term.Wtf16Of    -> add(StringTag(element.value))
          is L.Term.ListOf     -> add(buildListOf(element, target(index), initializers))
          is L.Term.CompoundOf -> add(buildCompoundOf(element, target(index), initializers))
          else                 -> {
            add(buildDefault(element))
            initializers += {
              packTerm(element)
              val targetIndex = if (element.repr == Repr.LIST) -2 else -1
              +ManipulateData(DataAccessor(MCX, LIST(targetIndex) + target(index)), DataManipulator.Append(SourceProvider.From(DataAccessor(MCX, `LIST{-1}`))))
              drop(element.repr)
            }
          }
        }
      }
    }
  }

  private fun buildCompoundOf(
    term: L.Term.CompoundOf,
    target: PersistentList<NbtNode>,
    initializers: MutableList<() -> Unit>,
  ): Tag {
    return buildCompoundTag {
      term.elements.forEach { (key, element) ->
        when (element) {
          is L.Term.I8Of       -> put(key, ByteTag(element.value))
          is L.Term.I16Of      -> put(key, ShortTag(element.value))
          is L.Term.I32Of      -> put(key, IntTag(element.value))
          is L.Term.I64Of      -> put(key, LongTag(element.value))
          is L.Term.F32Of      -> put(key, FloatTag(element.value))
          is L.Term.F64Of      -> put(key, DoubleTag(element.value))
          is L.Term.Wtf16Of    -> put(key, StringTag(element.value))
          is L.Term.ListOf     -> put(key, buildListOf(element, target(key), initializers))
          is L.Term.CompoundOf -> put(key, buildCompoundOf(element, target(key), initializers))
          else                 -> {
            put(key, buildDefault(element))
            initializers += {
              packTerm(element)
              val targetIndex = if (element.repr == Repr.COMPOUND) -2 else -1
              +ManipulateData(DataAccessor(MCX, COMPOUND(targetIndex) + target(key)), DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, `COMPOUND{-1}`))))
              drop(element.repr)
            }
          }
        }
      }
    }
  }

  private fun matchPattern(
    pattern: L.Pattern,
    scrutinee: PersistentList<NbtNode>,
  ) {
    when (pattern) {
      is L.Pattern.UnitOf     -> {}
      is L.Pattern.BoolOf     -> {
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, scrutinee))))
        +Execute.ConditionalScoreMatches(
          true, R1, MAIN, if (pattern.value) until(0) else from(1),
          Execute.Run(SetScore(R0, MAIN, 0))
        )
      }
      is L.Pattern.I8Of       -> {
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, scrutinee))))
        +Execute.ConditionalScoreMatches(
          false, R1, MAIN, exact(pattern.value.toInt()),
          Execute.Run(SetScore(R0, MAIN, 0))
        )
      }
      is L.Pattern.I16Of      -> {
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, scrutinee))))
        +Execute.ConditionalScoreMatches(
          false, R1, MAIN, exact(pattern.value.toInt()),
          Execute.Run(SetScore(R0, MAIN, 0))
        )
      }
      is L.Pattern.I32Of      -> {
        +Execute.StoreScore(RESULT, R1, MAIN, Execute.Run(GetData(DataAccessor(MCX, scrutinee))))
        +Execute.ConditionalScoreMatches(
          false, R1, MAIN, exact(pattern.value),
          Execute.Run(SetScore(R0, MAIN, 0))
        )
      }
      is L.Pattern.I64Of      -> {
        +ManipulateData(REGISTER, DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, scrutinee))))
        +Execute.CheckMatchingData(
          false, DataAccessor(MCX, listOf(MatchRootObject(buildCompoundTag { put(REGISTER_PATH, LongTag(pattern.value)) }))),
          Execute.Run(SetScore(R0, MAIN, 0))
        )
      }
      is L.Pattern.F32Of      -> {
        +ManipulateData(REGISTER, DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, scrutinee))))
        +Execute.CheckMatchingData(
          false, DataAccessor(MCX, listOf(MatchRootObject(buildCompoundTag { put(REGISTER_PATH, FloatTag(pattern.value)) }))),
          Execute.Run(SetScore(R0, MAIN, 0))
        )
      }
      is L.Pattern.F64Of      -> {
        +ManipulateData(REGISTER, DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, scrutinee))))
        +Execute.CheckMatchingData(
          false, DataAccessor(MCX, listOf(MatchRootObject(buildCompoundTag { put(REGISTER_PATH, DoubleTag(pattern.value)) }))),
          Execute.Run(SetScore(R0, MAIN, 0))
        )
      }
      is L.Pattern.Wtf16Of    -> {
        +ManipulateData(REGISTER, DataManipulator.Set(SourceProvider.From(DataAccessor(MCX, scrutinee))))
        +Execute.CheckMatchingData(
          false, DataAccessor(MCX, listOf(MatchRootObject(buildCompoundTag { put(REGISTER_PATH, StringTag(pattern.value)) }))),
          Execute.Run(SetScore(R0, MAIN, 0))
        )
      }
      is L.Pattern.I8ArrayOf  -> {
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
      is L.Pattern.I32ArrayOf -> {
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
      is L.Pattern.I64ArrayOf -> {
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
      is L.Pattern.ListOf     -> {
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
      is L.Pattern.CompoundOf -> {
        pattern.elements.forEach { (key, element) ->
          matchPattern(element, scrutinee(key))
        }
      }
      is L.Pattern.Var        -> {}
      is L.Pattern.Drop       -> {}
    }
  }

  private fun packPattern(
    pattern: L.Pattern,
  ) {
    when (pattern) {
      is L.Pattern.UnitOf     -> {}
      is L.Pattern.BoolOf     -> {}
      is L.Pattern.I8Of       -> {}
      is L.Pattern.I16Of      -> {}
      is L.Pattern.I32Of      -> {}
      is L.Pattern.I64Of      -> {}
      is L.Pattern.F32Of      -> {}
      is L.Pattern.F64Of      -> {}
      is L.Pattern.Wtf16Of    -> {}
      is L.Pattern.I8ArrayOf  -> {
        pattern.elements.forEachIndexed { index, element ->
          push(element.repr, SourceProvider.From(DataAccessor(MCX, `BYTE_ARRAY{-1}`(index)))) // ?
          packPattern(element)
        }
      }
      is L.Pattern.I32ArrayOf -> {
        pattern.elements.forEachIndexed { index, element ->
          push(element.repr, SourceProvider.From(DataAccessor(MCX, `INT_ARRAY{-1}`(index)))) // ?
          packPattern(element)
        }
      }
      is L.Pattern.I64ArrayOf -> {
        pattern.elements.forEachIndexed { index, element ->
          push(element.repr, SourceProvider.From(DataAccessor(MCX, `LONG_ARRAY{-1}`(index)))) // ?
          packPattern(element)
        }
      }
      is L.Pattern.ListOf     -> {
        pattern.elements.forEachIndexed { index, element ->
          push(element.repr, SourceProvider.From(DataAccessor(MCX, `LIST{-1}`(index)))) // ?
          packPattern(element)
        }
      }
      is L.Pattern.CompoundOf -> {
        pattern.elements.forEach { (name, element) ->
          push(element.repr, SourceProvider.From(DataAccessor(MCX, `COMPOUND{-1}`(name)))) // ?
          packPattern(element)
        }
      }
      is L.Pattern.Var        -> bind(pattern.name, pattern.repr)
      is L.Pattern.Drop       -> {}
    }
  }

  private fun dropPattern(
    pattern: L.Pattern,
    keeps: List<Repr>,
  ) {
    when (pattern) {
      is L.Pattern.UnitOf     -> drop(Repr.BYTE, keeps)
      is L.Pattern.BoolOf     -> drop(Repr.BYTE, keeps)
      is L.Pattern.I8Of       -> drop(Repr.BYTE, keeps)
      is L.Pattern.I16Of      -> drop(Repr.SHORT, keeps)
      is L.Pattern.I32Of      -> drop(Repr.INT, keeps)
      is L.Pattern.I64Of      -> drop(Repr.LONG, keeps)
      is L.Pattern.F32Of      -> drop(Repr.FLOAT, keeps)
      is L.Pattern.F64Of      -> drop(Repr.DOUBLE, keeps)
      is L.Pattern.Wtf16Of    -> drop(Repr.STRING, keeps)
      is L.Pattern.I8ArrayOf  -> {
        pattern.elements.reversed().forEach { element -> dropPattern(element, keeps) }
        drop(Repr.BYTE_ARRAY, keeps)
      }
      is L.Pattern.I32ArrayOf -> {
        pattern.elements.reversed().forEach { element -> dropPattern(element, keeps) }
        drop(Repr.INT_ARRAY, keeps)
      }
      is L.Pattern.I64ArrayOf -> {
        pattern.elements.reversed().forEach { element -> dropPattern(element, keeps) }
        drop(Repr.LONG_ARRAY, keeps)
      }
      is L.Pattern.ListOf     -> {
        pattern.elements.reversed().forEach { element -> dropPattern(element, keeps) }
        drop(Repr.LIST, keeps)
      }
      is L.Pattern.CompoundOf -> {
        pattern.elements.entries.reversed().forEach { (_, element) -> dropPattern(element, keeps) }
        drop(Repr.COMPOUND, keeps)
      }
      is L.Pattern.Var        -> drop(pattern.repr, keeps)
      is L.Pattern.Drop       -> drop(pattern.repr, keeps)
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

    val MAIN: Packed.Objective = Packed.Objective("mcx")

    val R0: Packed.ScoreHolder = Packed.ScoreHolder("#0")
    val R1: Packed.ScoreHolder = Packed.ScoreHolder("#1")

    val MCX: ResourceLocation = ResourceLocation("mcx", "")
    private val MCX_DATA: ResourceLocation = ResourceLocation("mcx_data", "")
    private val MCX_TEST: ResourceLocation = ResourceLocation("mcx_test", "")

    private const val REGISTER_PATH: String = "register"
    private val REGISTER: DataAccessor = DataAccessor(MCX, nbtPath(REGISTER_PATH))

    private val BYTE: PersistentList<NbtNode> = nbtPath(NbtType.BYTE.id)
    private val `BYTE{-1}`: PersistentList<NbtNode> = BYTE(-1)
    private val `BYTE{-2}`: PersistentList<NbtNode> = BYTE(-2)
    private val SHORT: PersistentList<NbtNode> = nbtPath(NbtType.SHORT.id)
    private val `SHORT{-1}`: PersistentList<NbtNode> = SHORT(-1)
    private val `SHORT{-2}`: PersistentList<NbtNode> = SHORT(-2)
    private val INT: PersistentList<NbtNode> = nbtPath(NbtType.INT.id)
    private val `INT{-1}`: PersistentList<NbtNode> = INT(-1)
    private val `INT{-2}`: PersistentList<NbtNode> = INT(-2)
    private val LONG: PersistentList<NbtNode> = nbtPath(NbtType.LONG.id)
    private val `LONG{-1}`: PersistentList<NbtNode> = LONG(-1)
    private val `LONG{-2}`: PersistentList<NbtNode> = LONG(-2)
    private val FLOAT: PersistentList<NbtNode> = nbtPath(NbtType.FLOAT.id)
    private val `FLOAT{-1}`: PersistentList<NbtNode> = FLOAT(-1)
    private val `FLOAT{-2}`: PersistentList<NbtNode> = FLOAT(-2)
    private val DOUBLE: PersistentList<NbtNode> = nbtPath(NbtType.DOUBLE.id)
    private val `DOUBLE{-1}`: PersistentList<NbtNode> = DOUBLE(-1)
    private val `DOUBLE{-2}`: PersistentList<NbtNode> = DOUBLE(-2)
    private val BYTE_ARRAY: PersistentList<NbtNode> = nbtPath(NbtType.BYTE_ARRAY.id)
    private val `BYTE_ARRAY{-1}`: PersistentList<NbtNode> = BYTE_ARRAY(-1)
    private val STRING: PersistentList<NbtNode> = nbtPath(NbtType.STRING.id)
    private val `STRING{-1}`: PersistentList<NbtNode> = STRING(-1)
    private val `STRING{-2}`: PersistentList<NbtNode> = STRING(-2)
    private val LIST: PersistentList<NbtNode> = nbtPath(NbtType.LIST.id)
    private val `LIST{-1}`: PersistentList<NbtNode> = LIST(-1)
    private val COMPOUND: PersistentList<NbtNode> = nbtPath(NbtType.COMPOUND.id)
    private val `COMPOUND{-1}`: PersistentList<NbtNode> = COMPOUND(-1)
    private val INT_ARRAY: PersistentList<NbtNode> = nbtPath(NbtType.INT_ARRAY.id)
    private val `INT_ARRAY{-1}`: PersistentList<NbtNode> = INT_ARRAY(-1)
    private val LONG_ARRAY: PersistentList<NbtNode> = nbtPath(NbtType.LONG_ARRAY.id)
    private val `LONG_ARRAY{-1}`: PersistentList<NbtNode> = LONG_ARRAY(-1)

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
        val proc = DataAccessor(MCX, `INT{-1}`)
        listOf(
          Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(proc))),
          RemoveData(proc),
        )
      } + procs.sortedBy { it.restore }.map { function ->
        val tag = function.restore!!
        Execute.ConditionalScoreMatches(true, R0, MAIN, exact(tag), Execute.Run(RunFunction(packDefinitionLocation(function.name))))
      })
    }

    // TODO: specialize dispatcher by type
    fun packDispatchFuncs(
      funcs: List<L.Definition.Function>,
    ): P.Definition.Function {
      return P.Definition.Function(emptyList(), DISPATCH_FUNC, listOf(
        Execute.StoreScore(RESULT, R0, MAIN, Execute.Run(GetData(DataAccessor(MCX, `COMPOUND{-1}`("_"))))),
        RemoveData(DataAccessor(MCX, `COMPOUND{-1}`)),
      ) + funcs.sortedBy { it.restore }.map { function ->
        val tag = function.restore!!
        Execute.ConditionalScoreMatches(true, R0, MAIN, exact(tag), Execute.Run(RunFunction(packDefinitionLocation(function.name))))
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
