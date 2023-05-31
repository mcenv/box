package mcx.pass

import mcx.ast.DefinitionLocation
import mcx.ast.ModuleLocation
import kotlin.math.max
import kotlin.math.min

fun lookupBuiltin(name: DefinitionLocation): Builtin? {
  return builtins[name]
}

abstract class Builtin(val name: DefinitionLocation) {
  abstract fun eval(args: List<Lazy<Value>>): Value?
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
      val a = args[0].value as? Value.StrOf ?: return null
      val b = args[1].value as? Value.StrOf ?: return null
      return Value.StrOf(a.value + b.value)
    }
  },

  object : Builtin(byte / "to_string") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I8Of ?: return null
      return Value.StrOf(a.value.toString())
    }
  },

  object : Builtin(short / "to_string") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I16Of ?: return null
      return Value.StrOf(a.value.toString())
    }
  },

  object : Builtin(int / "+") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.I32Of(a.value + b.value)
    }
  },

  object : Builtin(int / "-") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.I32Of(a.value - b.value)
    }
  },

  object : Builtin(int / "*") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.I32Of(a.value * b.value)
    }
  },

  object : Builtin(int / "/") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val b = args[1].value as? Value.I32Of ?: return null
      return if (b.value == 0) {
        Value.I32Of(0)
      } else {
        val a = args[0].value as? Value.I32Of ?: return null
        Value.I32Of(Math.floorDiv(a.value, b.value))
      }
    }
  },

  object : Builtin(int / "%") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val b = args[1].value as? Value.I32Of ?: return null
      return if (b.value == 0) {
        Value.I32Of(0)
      } else {
        val a = args[0].value as? Value.I32Of ?: return null
        Value.I32Of(Math.floorMod(a.value, b.value))
      }
    }
  },

  object : Builtin(int / "min") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.I32Of(min(a.value, b.value))
    }
  },

  object : Builtin(int / "max") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.I32Of(max(a.value, b.value))
    }
  },

  object : Builtin(int / "=") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.BoolOf(a.value == b.value)
    }
  },

  object : Builtin(int / "<") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.BoolOf(a.value < b.value)
    }
  },

  object : Builtin(int / "<=") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.BoolOf(a.value <= b.value)
    }
  },

  object : Builtin(int / ">") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.BoolOf(a.value > b.value)
    }
  },

  object : Builtin(int / ">=") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.BoolOf(a.value >= b.value)
    }
  },

  object : Builtin(int / "!=") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.BoolOf(a.value != b.value)
    }
  },

  object : Builtin(int / "to_byte") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      return Value.I8Of(a.value.toByte())
    }
  },

  object : Builtin(int / "to_short") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      return Value.I16Of(a.value.toShort())
    }
  },

  object : Builtin(int / "to_long") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      return Value.I64Of(a.value.toLong())
    }
  },

  object : Builtin(int / "to_float") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      return Value.F32Of(a.value.toFloat())
    }
  },

  object : Builtin(int / "to_double") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      return Value.F64Of(a.value.toDouble())
    }
  },

  object : Builtin(int / "to_string") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      return Value.StrOf(a.value.toString())
    }
  },

  object : Builtin(long / "to_string") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I64Of ?: return null
      return Value.StrOf(a.value.toString())
    }
  },

  object : Builtin(float / "to_string") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.F32Of ?: return null
      return Value.StrOf(a.value.toString())
    }
  },

  object : Builtin(double / "to_string") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.F64Of ?: return null
      return Value.StrOf(a.value.toString())
    }
  },

  object : Builtin(string / "size") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.StrOf ?: return null
      return Value.I32Of(a.value.length)
    }
  },

  object : Builtin(byteArray / "size") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I8ArrayOf ?: return null
      return Value.I32Of(a.elements.size)
    }
  },

  object : Builtin(intArray / "size") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32ArrayOf ?: return null
      return Value.I32Of(a.elements.size)
    }
  },

  object : Builtin(longArray / "size") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I64ArrayOf ?: return null
      return Value.I32Of(a.elements.size)
    }
  },
).associateBy { it.name }
