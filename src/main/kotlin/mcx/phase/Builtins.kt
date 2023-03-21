package mcx.phase

import mcx.ast.DefinitionLocation
import mcx.ast.ModuleLocation
import kotlin.math.max
import kotlin.math.min

fun lookupBuiltin(name: DefinitionLocation): Builtin? {
  return builtins[name]
}

abstract class Builtin(val name: DefinitionLocation) {
  abstract fun eval(args: List<Lazy<Value>>): Value?

  open fun pack() {
    TODO()
  }
}

val prelude: ModuleLocation = ModuleLocation("prelude")
private val byte: ModuleLocation = ModuleLocation("byte")
private val short: ModuleLocation = ModuleLocation("short")
private val int: ModuleLocation = ModuleLocation("int")
private val long: ModuleLocation = ModuleLocation("long")
private val float: ModuleLocation = ModuleLocation("float")
private val double: ModuleLocation = ModuleLocation("double")
private val string: ModuleLocation = ModuleLocation("string")
private val byteArray: ModuleLocation = ModuleLocation("byte_array")
private val intArray: ModuleLocation = ModuleLocation("int_array")
private val longArray: ModuleLocation = ModuleLocation("long_array")
private val list: ModuleLocation = ModuleLocation("list")
private val compound: ModuleLocation = ModuleLocation("compound")

private val builtins: Map<DefinitionLocation, Builtin> = listOf(
  object : Builtin(prelude / "++") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.StringOf ?: return null
      val b = args[1].value as? Value.StringOf ?: return null
      return Value.StringOf(a.value + b.value)
    }
  },

  object : Builtin(byte / "to_string") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ByteOf ?: return null
      return Value.StringOf(a.value.toString())
    }
  },

  object : Builtin(short / "to_string") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ShortOf ?: return null
      return Value.StringOf(a.value.toString())
    }
  },

  object : Builtin(int / "+") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.IntOf ?: return null
      val b = args[1].value as? Value.IntOf ?: return null
      return Value.IntOf(a.value + b.value)
    }
  },

  object : Builtin(int / "-") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.IntOf ?: return null
      val b = args[1].value as? Value.IntOf ?: return null
      return Value.IntOf(a.value - b.value)
    }
  },

  object : Builtin(int / "*") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.IntOf ?: return null
      val b = args[1].value as? Value.IntOf ?: return null
      return Value.IntOf(a.value * b.value)
    }
  },

  object : Builtin(int / "/") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val b = args[1].value as? Value.IntOf ?: return null
      return if (b.value == 0) {
        Value.IntOf(0)
      } else {
        val a = args[0].value as? Value.IntOf ?: return null
        Value.IntOf(Math.floorDiv(a.value, b.value))
      }
    }
  },

  object : Builtin(int / "%") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val b = args[1].value as? Value.IntOf ?: return null
      return if (b.value == 0) {
        Value.IntOf(0)
      } else {
        val a = args[0].value as? Value.IntOf ?: return null
        Value.IntOf(Math.floorMod(a.value, b.value))
      }
    }
  },

  object : Builtin(int / "min") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.IntOf ?: return null
      val b = args[1].value as? Value.IntOf ?: return null
      return Value.IntOf(min(a.value, b.value))
    }
  },

  object : Builtin(int / "max") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.IntOf ?: return null
      val b = args[1].value as? Value.IntOf ?: return null
      return Value.IntOf(max(a.value, b.value))
    }
  },

  object : Builtin(int / "=") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.IntOf ?: return null
      val b = args[1].value as? Value.IntOf ?: return null
      return Value.BoolOf(a.value == b.value)
    }
  },

  object : Builtin(int / "<") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.IntOf ?: return null
      val b = args[1].value as? Value.IntOf ?: return null
      return Value.BoolOf(a.value < b.value)
    }
  },

  object : Builtin(int / "<=") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.IntOf ?: return null
      val b = args[1].value as? Value.IntOf ?: return null
      return Value.BoolOf(a.value <= b.value)
    }
  },

  object : Builtin(int / ">") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.IntOf ?: return null
      val b = args[1].value as? Value.IntOf ?: return null
      return Value.BoolOf(a.value > b.value)
    }
  },

  object : Builtin(int / ">=") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.IntOf ?: return null
      val b = args[1].value as? Value.IntOf ?: return null
      return Value.BoolOf(a.value >= b.value)
    }
  },

  object : Builtin(int / "!=") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.IntOf ?: return null
      val b = args[1].value as? Value.IntOf ?: return null
      return Value.BoolOf(a.value != b.value)
    }
  },

  object : Builtin(int / "to_byte") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.IntOf ?: return null
      return Value.ByteOf(a.value.toByte())
    }
  },

  object : Builtin(int / "to_short") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.IntOf ?: return null
      return Value.ShortOf(a.value.toShort())
    }
  },

  object : Builtin(int / "to_long") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.IntOf ?: return null
      return Value.LongOf(a.value.toLong())
    }
  },

  object : Builtin(int / "to_float") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.IntOf ?: return null
      return Value.FloatOf(a.value.toFloat())
    }
  },

  object : Builtin(int / "to_double") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.IntOf ?: return null
      return Value.DoubleOf(a.value.toDouble())
    }
  },

  object : Builtin(int / "to_string") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.IntOf ?: return null
      return Value.StringOf(a.value.toString())
    }
  },

  object : Builtin(long / "to_string") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.LongOf ?: return null
      return Value.StringOf(a.value.toString())
    }
  },

  object : Builtin(float / "to_string") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.FloatOf ?: return null
      return Value.StringOf(a.value.toString())
    }
  },

  object : Builtin(double / "to_string") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.DoubleOf ?: return null
      return Value.StringOf(a.value.toString())
    }
  },

  object : Builtin(string / "size") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.StringOf ?: return null
      return Value.IntOf(a.value.length)
    }
  },

  object : Builtin(byteArray / "size") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ByteArrayOf ?: return null
      return Value.IntOf(a.elements.size)
    }
  },

  object : Builtin(intArray / "size") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.IntArrayOf ?: return null
      return Value.IntOf(a.elements.size)
    }
  },

  object : Builtin(longArray / "size") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.LongArrayOf ?: return null
      return Value.IntOf(a.elements.size)
    }
  },
).associateBy { it.name }
