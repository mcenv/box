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
private val byte: ModuleLocation = ModuleLocation(Stack.BYTE.id)
private val short: ModuleLocation = ModuleLocation(Stack.SHORT.id)
private val int: ModuleLocation = ModuleLocation(Stack.INT.id)
private val long: ModuleLocation = ModuleLocation(Stack.LONG.id)
private val float: ModuleLocation = ModuleLocation(Stack.FLOAT.id)
private val double: ModuleLocation = ModuleLocation(Stack.DOUBLE.id)
private val string: ModuleLocation = ModuleLocation(Stack.STRING.id)
private val byteArray: ModuleLocation = ModuleLocation(Stack.BYTE_ARRAY.id)
private val intArray: ModuleLocation = ModuleLocation(Stack.INT_ARRAY.id)
private val longArray: ModuleLocation = ModuleLocation(Stack.LONG_ARRAY.id)

val BUILTINS: Map<DefinitionLocation, Builtin> = listOf(
  object : Builtin(prelude / "++") {
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
  },

  object : Builtin(magic / "erase") {
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
        is Type.Func      -> Value.StringOf(Stack.INT.id)
        is Type.Clos      -> Value.StringOf(Stack.COMPOUND.id)
        is Type.Code      -> error("unexpected type: ${prettyType(a)}")
        is Type.Var       -> error("unexpected type: ${prettyType(a)}")
        is Type.Run       -> error("unexpected type: ${prettyType(a)}")
        is Type.Meta      -> error("unexpected type: ${prettyType(a)}")
        is Type.Hole      -> error("unexpected type: ${prettyType(a)}")
      }
    }
  },

  object : Builtin(magic / "lift") {
    override fun eval(
      arg: Value,
      typeArgs: List<Type>,
    ): Value {
      return Value.CodeOf(lazyOf(arg))
    }
  },

  object : Builtin(byte / "to_string") {
    override fun eval(
      arg: Value,
      typeArgs: List<Type>,
    ): Value? {
      if (arg !is Value.ByteOf) return null
      return Value.StringOf(arg.value.toString())
    }
  },

  object : Builtin(short / "to_string") {
    override fun eval(
      arg: Value,
      typeArgs: List<Type>,
    ): Value? {
      if (arg !is Value.ShortOf) return null
      return Value.StringOf(arg.value.toString())
    }
  },

  object : Builtin(int / "+") {
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
  },

  object : Builtin(int / "-") {
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
  },

  object : Builtin(int / "*") {
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
  },

  object : Builtin(int / "/") {
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
  },

  object : Builtin(int / "%") {
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
  },

  object : Builtin(int / "min") {
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
  },

  object : Builtin(int / "max") {
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
  },

  object : Builtin(int / "=") {
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
  },

  object : Builtin(int / "<") {
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
  },

  object : Builtin(int / "<=") {
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
  },

  object : Builtin(int / ">") {
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
  },

  object : Builtin(int / ">=") {
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
  },

  object : Builtin(int / "!=") {
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
  },

  object : Builtin(int / "to_byte") {
    override fun eval(
      arg: Value,
      typeArgs: List<Type>,
    ): Value? {
      if (arg !is Value.IntOf) return null
      return Value.ByteOf(arg.value.toByte())
    }
  },

  object : Builtin(int / "to_short") {
    override fun eval(
      arg: Value,
      typeArgs: List<Type>,
    ): Value? {
      if (arg !is Value.IntOf) return null
      return Value.ShortOf(arg.value.toShort())
    }
  },

  object : Builtin(int / "to_int") {
    override fun eval(
      arg: Value,
      typeArgs: List<Type>,
    ): Value {
      return arg
    }
  },

  object : Builtin(int / "to_long") {
    override fun eval(
      arg: Value,
      typeArgs: List<Type>,
    ): Value? {
      if (arg !is Value.IntOf) return null
      return Value.LongOf(arg.value.toLong())
    }
  },

  object : Builtin(int / "to_float") {
    override fun eval(
      arg: Value,
      typeArgs: List<Type>,
    ): Value? {
      if (arg !is Value.IntOf) return null
      return Value.FloatOf(arg.value.toFloat())
    }
  },

  object : Builtin(int / "to_double") {
    override fun eval(
      arg: Value,
      typeArgs: List<Type>,
    ): Value? {
      if (arg !is Value.IntOf) return null
      return Value.DoubleOf(arg.value.toDouble())
    }
  },

  object : Builtin(int / "to_string") {
    override fun eval(
      arg: Value,
      typeArgs: List<Type>,
    ): Value? {
      if (arg !is Value.IntOf) return null
      return Value.StringOf(arg.value.toString())
    }
  },

  object : Builtin(int / "dup") {
    override fun eval(
      arg: Value,
      typeArgs: List<Type>,
    ): Value {
      return Value.TupleOf(listOf(lazyOf(arg), lazyOf(arg)))
    }
  },

  object : Builtin(long / "to_string") {
    override fun eval(
      arg: Value,
      typeArgs: List<Type>,
    ): Value? {
      if (arg !is Value.LongOf) return null
      return Value.StringOf(arg.value.toString())
    }
  },

  object : Builtin(float / "to_string") {
    override fun eval(
      arg: Value,
      typeArgs: List<Type>,
    ): Value? {
      if (arg !is Value.FloatOf) return null
      return Value.StringOf(arg.value.toString())
    }
  },

  object : Builtin(double / "to_string") {
    override fun eval(
      arg: Value,
      typeArgs: List<Type>,
    ): Value? {
      if (arg !is Value.DoubleOf) return null
      return Value.StringOf(arg.value.toString())
    }
  },

  object : Builtin(string / "size") {
    override fun eval(
      arg: Value,
      typeArgs: List<Type>,
    ): Value? {
      if (arg !is Value.StringOf) return null
      return Value.IntOf(arg.value.length)
    }
  },

  object : Builtin(string / "substring_from'") {
    override fun eval(
      arg: Value,
      typeArgs: List<Type>,
    ): Value? {
      if (arg !is Value.TupleOf) return null
      val string = arg.elements[0].value
      if (string !is Value.StringOf) return null
      val start = arg.elements[1].value
      if (start !is Value.IntOf) return null
      return Value.StringOf(string.value.substring(start.value))
    }
  },

  object : Builtin(string / "substring_between'") {
    override fun eval(
      arg: Value,
      typeArgs: List<Type>,
    ): Value? {
      if (arg !is Value.TupleOf) return null
      val string = arg.elements[0].value
      if (string !is Value.StringOf) return null
      val start = arg.elements[1].value
      if (start !is Value.IntOf) return null
      val end = arg.elements[2].value
      if (end !is Value.IntOf) return null
      return Value.StringOf(string.value.substring(start.value, end.value))
    }
  },

  object : Builtin(byteArray / "size") {
    override fun eval(
      arg: Value,
      typeArgs: List<Type>,
    ): Value? {
      if (arg !is Value.ByteArrayOf) return null
      return Value.IntOf(arg.elements.size)
    }
  },

  object : Builtin(intArray / "size") {
    override fun eval(
      arg: Value,
      typeArgs: List<Type>,
    ): Value? {
      if (arg !is Value.IntArrayOf) return null
      return Value.IntOf(arg.elements.size)
    }
  },

  object : Builtin(longArray / "size") {
    override fun eval(
      arg: Value,
      typeArgs: List<Type>,
    ): Value? {
      if (arg !is Value.LongArrayOf) return null
      return Value.IntOf(arg.elements.size)
    }
  },
).associateBy { it.name }

abstract class Builtin(
  val name: DefinitionLocation,
) {
  abstract fun eval(
    arg: Value,
    typeArgs: List<Type>,
  ): Value?
}
