package mcx.phase

import mcx.ast.DefinitionLocation
import mcx.ast.ModuleLocation
import mcx.ast.Packed.Objective
import mcx.ast.Packed.ScoreHolder
import mcx.ast.Value
import mcx.data.ResourceLocation
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
private val STRING_MODULE: ModuleLocation = ModuleLocation(STRING)
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
  StringSize,
  ByteArraySize,
  IntArraySize,
  LongArraySize,
).associateBy { it.name }

sealed class Builtin(
  val name: DefinitionLocation,
) {
  abstract fun eval(arg: Value): Value?
}

object Command : Builtin(PRELUDE / "command") {
  override fun eval(arg: Value): Value? {
    if (arg !is Value.StringOf) return null
    return Value.CodeOf(lazyOf(Value.Command(arg.value)))
  }
}

object IntAdd : Builtin(INT_MODULE / "+") {
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
  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntOf) return null
    return Value.ByteOf(arg.value.toByte())
  }
}

object IntToShort : Builtin(INT_MODULE / "to_short") {
  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntOf) return null
    return Value.ShortOf(arg.value.toShort())
  }
}

object IntToInt : Builtin(INT_MODULE / "to_int") {
  override fun eval(arg: Value): Value {
    return arg
  }
}

object IntToLong : Builtin(INT_MODULE / "to_long") {
  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntOf) return null
    return Value.LongOf(arg.value.toLong())
  }
}

object IntToFloat : Builtin(INT_MODULE / "to_float") {
  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntOf) return null
    return Value.FloatOf(arg.value.toFloat())
  }
}

object IntToDouble : Builtin(INT_MODULE / "to_double") {
  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntOf) return null
    return Value.DoubleOf(arg.value.toDouble())
  }
}

object IntDup : Builtin(INT_MODULE / "dup") {
  override fun eval(arg: Value): Value {
    return Value.TupleOf(listOf(lazyOf(arg), lazyOf(arg)))
  }
}

object StringSize : Builtin(STRING_MODULE / "size") {
  override fun eval(arg: Value): Value? {
    if (arg !is Value.StringOf) return null
    return Value.IntOf(arg.value.length)
  }
}

object ByteArraySize : Builtin(BYTE_ARRAY_MODULE / "size") {
  override fun eval(arg: Value): Value? {
    if (arg !is Value.ByteArrayOf) return null
    return Value.IntOf(arg.elements.size)
  }
}

object IntArraySize : Builtin(INT_ARRAY_MODULE / "size") {
  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntArrayOf) return null
    return Value.IntOf(arg.elements.size)
  }
}

object LongArraySize : Builtin(LONG_ARRAY_MODULE / "size") {
  override fun eval(arg: Value): Value? {
    if (arg !is Value.LongArrayOf) return null
    return Value.IntOf(arg.elements.size)
  }
}
