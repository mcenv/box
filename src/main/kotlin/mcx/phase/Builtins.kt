package mcx.phase

import mcx.ast.Core.Type
import mcx.ast.DefinitionLocation
import mcx.ast.ModuleLocation
import mcx.ast.Packed.Stack
import mcx.ast.Value
import kotlin.math.max
import kotlin.math.min

val prelude: ModuleLocation = ModuleLocation("prelude")
private val magic: ModuleLocation = ModuleLocation("magic")
private val int: ModuleLocation = ModuleLocation(Stack.INT.id)
private val string: ModuleLocation = ModuleLocation(Stack.STRING.id)
private val byteArray: ModuleLocation = ModuleLocation(Stack.BYTE_ARRAY.id)
private val intArray: ModuleLocation = ModuleLocation(Stack.INT_ARRAY.id)
private val longArray: ModuleLocation = ModuleLocation(Stack.LONG_ARRAY.id)

val BUILTINS: Map<DefinitionLocation, Builtin> = listOf(
  MagicCommand,
  MagicErase,
  MagicLift,
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
  IntToString,
  IntDup,
  StringSize,
  StringConcat,
  ByteArraySize,
  IntArraySize,
  LongArraySize,
).associateBy { it.name }

sealed class Builtin(
  val name: DefinitionLocation,
) {
  abstract fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value?
}

private object MagicCommand : Builtin(magic / "command") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.StringOf) return null
    return Value.CodeOf(lazyOf(Value.Command(arg.value)))
  }
}

private object MagicErase : Builtin(magic / "erase") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    return when (val a = typeArgs.first()) {
      is Type.Bool      -> Value.StringOf(Stack.BYTE.id)
      is Type.Byte      -> Value.StringOf(Stack.BYTE.id)
      is Type.Short     -> Value.StringOf(Stack.SHORT.id)
      is Type.Int       -> Value.StringOf(Stack.INT.id)
      is Type.Long      -> Value.StringOf(Stack.LONG.id)
      is Type.Float     -> Value.StringOf(Stack.FLOAT.id)
      is Type.Double    -> Value.StringOf(Stack.DOUBLE.id)
      is Type.String    -> Value.StringOf(Stack.STRING.id)
      is Type.ByteArray -> Value.StringOf(Stack.BYTE_ARRAY.id)
      is Type.IntArray  -> Value.StringOf(Stack.INT_ARRAY.id)
      is Type.LongArray -> Value.StringOf(Stack.LONG_ARRAY.id)
      is Type.List      -> Value.StringOf(Stack.LIST.id)
      is Type.Compound  -> Value.StringOf(Stack.COMPOUND.id)
      is Type.Ref       -> Value.StringOf(Stack.INT.id)
      is Type.Tuple     -> error("unexpected type: ${prettyType(a)}")
      is Type.Union     -> null // TODO
      is Type.Fun       -> Value.StringOf(Stack.COMPOUND.id)
      is Type.Code      -> error("unexpected type: ${prettyType(a)}")
      is Type.Var       -> error("unexpected type: ${prettyType(a)}")
      is Type.Run       -> error("unexpected type: ${prettyType(a)}")
      is Type.Meta      -> error("unexpected type: ${prettyType(a)}")
      is Type.Hole      -> error("unexpected type: ${prettyType(a)}")
    }
  }
}

private object MagicLift : Builtin(magic / "lift") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value {
    return Value.CodeOf(lazyOf(arg))
  }
}

private object IntAdd : Builtin(int / "+") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.IntOf(a.value + b.value)
  }
}

private object IntSub : Builtin(int / "-") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.IntOf(a.value - b.value)
  }
}

private object IntMul : Builtin(int / "*") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.IntOf(a.value * b.value)
  }
}

private object IntDiv : Builtin(int / "/") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
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

private object IntMod : Builtin(int / "%") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
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

private object IntMin : Builtin(int / "min") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.IntOf(min(a.value, b.value))
  }
}

private object IntMax : Builtin(int / "max") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.IntOf(max(a.value, b.value))
  }
}

private object IntEq : Builtin(int / "=") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.BoolOf(a.value == b.value)
  }
}

private object IntLt : Builtin(int / "<") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.BoolOf(a.value < b.value)
  }
}

private object IntLe : Builtin(int / "<=") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.BoolOf(a.value <= b.value)
  }
}

private object IntGt : Builtin(int / ">") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.BoolOf(a.value > b.value)
  }
}

private object IntGe : Builtin(int / ">=") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.BoolOf(a.value >= b.value)
  }
}

private object IntNe : Builtin(int / "!=") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.IntOf) return null
    val b = arg.elements[1].value
    if (b !is Value.IntOf) return null
    return Value.BoolOf(a.value != b.value)
  }
}

private object IntToByte : Builtin(int / "to_byte") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.IntOf) return null
    return Value.ByteOf(arg.value.toByte())
  }
}

private object IntToShort : Builtin(int / "to_short") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.IntOf) return null
    return Value.ShortOf(arg.value.toShort())
  }
}

private object IntToInt : Builtin(int / "to_int") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value {
    return arg
  }
}

private object IntToLong : Builtin(int / "to_long") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.IntOf) return null
    return Value.LongOf(arg.value.toLong())
  }
}

private object IntToFloat : Builtin(int / "to_float") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.IntOf) return null
    return Value.FloatOf(arg.value.toFloat())
  }
}

private object IntToDouble : Builtin(int / "to_double") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.IntOf) return null
    return Value.DoubleOf(arg.value.toDouble())
  }
}

private object IntToString : Builtin(int / "to_string") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.IntOf) return null
    return Value.StringOf(arg.value.toString())
  }
}

private object IntDup : Builtin(int / "dup") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value {
    return Value.TupleOf(listOf(lazyOf(arg), lazyOf(arg)))
  }
}

private object StringSize : Builtin(string / "size") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.StringOf) return null
    return Value.IntOf(arg.value.length)
  }
}

private object StringConcat : Builtin(string / "++") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.TupleOf) return null
    val a = arg.elements[0].value
    if (a !is Value.StringOf) return null
    val b = arg.elements[1].value
    if (b !is Value.StringOf) return null
    return Value.StringOf(a.value + b.value)
  }
}

private object ByteArraySize : Builtin(byteArray / "size") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.ByteArrayOf) return null
    return Value.IntOf(arg.elements.size)
  }
}

private object IntArraySize : Builtin(intArray / "size") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.IntArrayOf) return null
    return Value.IntOf(arg.elements.size)
  }
}

private object LongArraySize : Builtin(longArray / "size") {
  override fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value? {
    if (arg !is Value.LongArrayOf) return null
    return Value.IntOf(arg.elements.size)
  }
}
