package mcx.phase

import mcx.ast.DefinitionLocation
import mcx.ast.ModuleLocation
import mcx.ast.Packed.Command
import mcx.ast.Packed.Command.*
import mcx.ast.Packed.Command.Execute.ConditionalScore.Comparator.*
import mcx.ast.Packed.Command.Execute.Mode.RESULT
import mcx.ast.Packed.Command.Execute.StoreStorage.Type
import mcx.ast.Packed.DataAccessor
import mcx.ast.Packed.DataManipulator
import mcx.ast.Packed.Objective
import mcx.ast.Packed.Operation.*
import mcx.ast.Packed.ScoreHolder
import mcx.ast.Packed.SourceProvider
import mcx.ast.Packed.Stack
import mcx.ast.Value
import mcx.data.ResourceLocation
import mcx.util.nbt.Nbt
import mcx.util.nbtPath
import kotlin.math.max
import kotlin.math.min

val REG_0: ScoreHolder = ScoreHolder("#0")
val REG_1: ScoreHolder = ScoreHolder("#1")
val REG: Objective = Objective("mcx")
val MCX: ResourceLocation = ResourceLocation("mcx", "")

const val END: String = "end"
const val BYTE: String = "byte"
const val SHORT: String = "short"
const val INT: String = "int"
const val LONG: String = "long"
const val FLOAT: String = "float"
const val DOUBLE: String = "double"
const val STRING: String = "string"
const val BYTE_ARRAY: String = "byte_array"
const val INT_ARRAY: String = "int_array"
const val LONG_ARRAY: String = "long_array"
const val LIST: String = "list"
const val COMPOUND: String = "compound"

val PRELUDE: ModuleLocation = ModuleLocation("prelude")
private val INT_MODULE: ModuleLocation = ModuleLocation(INT)
private val BYTE_ARRAY_MODULE: ModuleLocation = ModuleLocation(BYTE_ARRAY)
private val INT_ARRAY_MODULE: ModuleLocation = ModuleLocation(INT_ARRAY)
private val LONG_ARRAY_MODULE: ModuleLocation = ModuleLocation(LONG_ARRAY)

val BUILTINS: Map<DefinitionLocation, Builtin> = listOf(
  Command,
  IntAdd,
  IntSub,
  IntMul,
  IntDiv,
  IntMod,
  IntMin,
  IntMax,
  IntEq,
  IntLt,
  IntLe,
  IntGt,
  IntGe,
  IntNe,
  IntToByte,
  IntToShort,
  IntToInt,
  IntToLong,
  IntToFloat,
  IntToDouble,
  IntDup,
  ByteArraySize,
  IntArraySize,
  LongArraySize,
).associateBy { it.name }

sealed class Builtin(
  val name: DefinitionLocation,
) {
  abstract fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command>

  abstract fun eval(arg: Value): Value?
}

object Command : Builtin(PRELUDE / "command") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    emptyList()

  override fun eval(arg: Value): Value? {
    if (arg !is Value.StringOf) return null
    return Value.CodeOf(lazyOf(Value.Command(arg.value)))
  }
}

object IntAdd : Builtin(INT_MODULE / "+") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    listOf(
      Execute.StoreScore(RESULT, REG_0, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      RemoveData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })),
      Execute.StoreScore(RESULT, REG_1, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      Execute.StoreStorage(RESULT, DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) }), Type.INT, 1.0,
                           Execute.Run(
                             PerformOperation(REG_1, REG, ADD, REG_0, REG))),
    )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.IntOf(a.value + b.value)
  }
}

object IntSub : Builtin(INT_MODULE / "-") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    listOf(
      Execute.StoreScore(RESULT, REG_0, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      RemoveData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })),
      Execute.StoreScore(RESULT, REG_1, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      Execute.StoreStorage(RESULT, DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) }), Type.INT, 1.0,
                           Execute.Run(
                             PerformOperation(REG_1, REG, SUB, REG_0, REG))),
    )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.IntOf(a.value - b.value)
  }
}

object IntMul : Builtin(INT_MODULE / "*") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    listOf(
      Execute.StoreScore(RESULT, REG_0, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      RemoveData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })),
      Execute.StoreScore(RESULT, REG_1, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      Execute.StoreStorage(RESULT, DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) }), Type.INT, 1.0,
                           Execute.Run(
                             PerformOperation(REG_1, REG, MUL, REG_0, REG))),
    )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.IntOf(a.value * b.value)
  }
}

object IntDiv : Builtin(INT_MODULE / "/") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    listOf(
      Execute.StoreScore(RESULT, REG_0, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      RemoveData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })),
      Execute.StoreScore(RESULT, REG_1, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      Execute.StoreStorage(RESULT, DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) }), Type.INT, 1.0,
                           Execute.Run(
                             PerformOperation(REG_1, REG, DIV, REG_0, REG))),
    )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return if (b.value == 0) {
      Value.IntOf(0)
    } else {
      Value.IntOf(Math.floorDiv(a.value, b.value))
    }
  }
}

object IntMod : Builtin(INT_MODULE / "%") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    listOf(
      Execute.StoreScore(RESULT, REG_0, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      RemoveData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })),
      Execute.StoreScore(RESULT, REG_1, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      Execute.StoreStorage(RESULT, DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) }), Type.INT, 1.0,
                           Execute.Run(
                             PerformOperation(REG_1, REG, MOD, REG_0, REG))),
    )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return if (b.value == 0) {
      Value.IntOf(0)
    } else {
      Value.IntOf(Math.floorMod(a.value, b.value))
    }
  }
}

object IntMin : Builtin(INT_MODULE / "min") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    listOf(
      Execute.StoreScore(RESULT, REG_0, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      RemoveData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })),
      Execute.StoreScore(RESULT, REG_1, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      Execute.StoreStorage(RESULT, DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) }), Type.INT, 1.0,
                           Execute.Run(
                             PerformOperation(REG_1, REG, MIN, REG_0, REG))),
    )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.IntOf(min(a.value, b.value))
  }
}

object IntMax : Builtin(INT_MODULE / "max") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    listOf(
      Execute.StoreScore(RESULT, REG_0, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      RemoveData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })),
      Execute.StoreScore(RESULT, REG_1, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      Execute.StoreStorage(RESULT, DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) }), Type.INT, 1.0,
                           Execute.Run(
                             PerformOperation(REG_1, REG, MAX, REG_0, REG))),
    )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.IntOf(max(a.value, b.value))
  }
}

object IntEq : Builtin(INT_MODULE / "=") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    listOf(
      Execute.StoreScore(RESULT, REG_0, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      RemoveData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })),
      Execute.StoreScore(RESULT, REG_1, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(BYTE) }), DataManipulator.Append(SourceProvider.Value(Nbt.Byte(0)))),
      Execute.ConditionalScore(true, REG_1, REG, EQ, REG_0, REG,
                               Execute.Run(
                                 ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(BYTE)(-1) }), DataManipulator.Set(SourceProvider.Value(Nbt.Byte(1)))))),
    )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.BoolOf(a.value == b.value)
  }
}

object IntLt : Builtin(INT_MODULE / "<") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    listOf(
      Execute.StoreScore(RESULT, REG_0, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      RemoveData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })),
      Execute.StoreScore(RESULT, REG_1, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(BYTE) }), DataManipulator.Append(SourceProvider.Value(Nbt.Byte(0)))),
      Execute.ConditionalScore(true, REG_1, REG, LT, REG_0, REG,
                               Execute.Run(
                                 ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(BYTE)(-1) }), DataManipulator.Set(SourceProvider.Value(Nbt.Byte(1)))))),
    )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.BoolOf(a.value < b.value)
  }
}

object IntLe : Builtin(INT_MODULE / "<=") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    listOf(
      Execute.StoreScore(RESULT, REG_0, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      RemoveData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })),
      Execute.StoreScore(RESULT, REG_1, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(BYTE) }), DataManipulator.Append(SourceProvider.Value(Nbt.Byte(0)))),
      Execute.ConditionalScore(true, REG_1, REG, LE, REG_0, REG,
                               Execute.Run(
                                 ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(BYTE)(-1) }), DataManipulator.Set(SourceProvider.Value(Nbt.Byte(1)))))),
    )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.BoolOf(a.value <= b.value)
  }
}

object IntGt : Builtin(INT_MODULE / ">") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    listOf(
      Execute.StoreScore(RESULT, REG_0, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      RemoveData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })),
      Execute.StoreScore(RESULT, REG_1, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(BYTE) }), DataManipulator.Append(SourceProvider.Value(Nbt.Byte(0)))),
      Execute.ConditionalScore(true, REG_1, REG, GT, REG_0, REG,
                               Execute.Run(
                                 ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(BYTE)(-1) }), DataManipulator.Set(SourceProvider.Value(Nbt.Byte(1)))))),
    )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.BoolOf(a.value > b.value)
  }
}

object IntGe : Builtin(INT_MODULE / ">=") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    listOf(
      Execute.StoreScore(RESULT, REG_0, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      RemoveData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })),
      Execute.StoreScore(RESULT, REG_1, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(BYTE) }), DataManipulator.Append(SourceProvider.Value(Nbt.Byte(0)))),
      Execute.ConditionalScore(true, REG_1, REG, GE, REG_0, REG,
                               Execute.Run(
                                 ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(BYTE)(-1) }), DataManipulator.Set(SourceProvider.Value(Nbt.Byte(1)))))),
    )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.BoolOf(a.value >= b.value)
  }
}

object IntNe : Builtin(INT_MODULE / "!=") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    listOf(
      Execute.StoreScore(RESULT, REG_0, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      RemoveData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })),
      Execute.StoreScore(RESULT, REG_1, REG,
                         Execute.Run(
                           GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
      ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(BYTE) }), DataManipulator.Append(SourceProvider.Value(Nbt.Byte(1)))),
      Execute.ConditionalScore(true, REG_1, REG, EQ, REG_0, REG,
                               Execute.Run(
                                 ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(BYTE)(-1) }), DataManipulator.Set(SourceProvider.Value(Nbt.Byte(0)))))),
    )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.BoolOf(a.value != b.value)
  }
}

object IntToByte : Builtin(INT_MODULE / "to_byte") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    listOf(
      ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(BYTE) }), DataManipulator.Append(SourceProvider.Value(Nbt.Byte(0)))),
      Execute.StoreStorage(RESULT, DataAccessor.Storage(MCX, nbtPath { it(BYTE)(-1) }), Type.BYTE, 1.0,
                           Execute.Run(
                             GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
    )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntOf) return null
    return Value.ByteOf(arg.value.toByte())
  }
}

object IntToShort : Builtin(INT_MODULE / "to_short") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    listOf(
      ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(SHORT) }), DataManipulator.Append(SourceProvider.Value(Nbt.Short(0)))),
      Execute.StoreStorage(RESULT, DataAccessor.Storage(MCX, nbtPath { it(SHORT)(-1) }), Type.SHORT, 1.0,
                           Execute.Run(
                             GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
    )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntOf) return null
    return Value.ShortOf(arg.value.toShort())
  }
}

object IntToInt : Builtin(INT_MODULE / "to_int") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    emptyList()

  override fun eval(arg: Value): Value {
    return arg
  }
}

object IntToLong : Builtin(INT_MODULE / "to_long") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    listOf(
      ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(LONG) }), DataManipulator.Append(SourceProvider.Value(Nbt.Long(0)))),
      Execute.StoreStorage(RESULT, DataAccessor.Storage(MCX, nbtPath { it(LONG)(-1) }), Type.LONG, 1.0,
                           Execute.Run(
                             GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
    )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntOf) return null
    return Value.LongOf(arg.value.toLong())
  }
}

object IntToFloat : Builtin(INT_MODULE / "to_float") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    listOf(
      ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(FLOAT) }), DataManipulator.Append(SourceProvider.Value(Nbt.Float(0.0f)))),
      Execute.StoreStorage(RESULT, DataAccessor.Storage(MCX, nbtPath { it(FLOAT)(-1) }), Type.FLOAT, 1.0,
                           Execute.Run(
                             GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
    )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntOf) return null
    return Value.FloatOf(arg.value.toFloat())
  }
}

object IntToDouble : Builtin(INT_MODULE / "to_double") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    listOf(
      ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(DOUBLE) }), DataManipulator.Append(SourceProvider.Value(Nbt.Double(0.0)))),
      Execute.StoreStorage(RESULT, DataAccessor.Storage(MCX, nbtPath { it(DOUBLE)(-1) }), Type.DOUBLE, 1.0,
                           Execute.Run(
                             GetData(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
    )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntOf) return null
    return Value.DoubleOf(arg.value.toDouble())
  }
}

object IntDup : Builtin(INT_MODULE / "dup") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    listOf(
      ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(INT) }), DataManipulator.Append(SourceProvider.From(DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) })))),
    )

  override fun eval(arg: Value): Value {
    return Value.TupleOf(listOf(lazyOf(arg), lazyOf(arg)))
  }
}

sealed class Size(
  module: ModuleLocation,
  private val stack: String,
) : Builtin(module / "size") {
  override fun pack(
    param: List<Stack>,
    result: List<Stack>,
  ): List<Command> =
    listOf(
      ManipulateData(DataAccessor.Storage(MCX, nbtPath { it(INT) }), DataManipulator.Append(SourceProvider.Value(Nbt.Int(0)))),
      Execute.StoreStorage(RESULT, DataAccessor.Storage(MCX, nbtPath { it(INT)(-1) }), Type.INT, 1.0,
                           Execute.CheckMatchingData(true, DataAccessor.Storage(MCX, nbtPath { it(stack)(-1)() })))
    )
}

object ByteArraySize : Size(BYTE_ARRAY_MODULE, BYTE_ARRAY) {
  override fun eval(arg: Value): Value? {
    if (arg !is Value.ByteArrayOf) return null
    return Value.IntOf(arg.elements.size)
  }
}

object IntArraySize : Size(INT_ARRAY_MODULE, INT_ARRAY) {
  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntArrayOf) return null
    return Value.IntOf(arg.elements.size)
  }
}

object LongArraySize : Size(LONG_ARRAY_MODULE, LONG_ARRAY) {
  override fun eval(arg: Value): Value? {
    if (arg !is Value.LongArrayOf) return null
    return Value.IntOf(arg.elements.size)
  }
}
