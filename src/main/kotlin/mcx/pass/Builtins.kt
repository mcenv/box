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

const val core: String = "core"
val prelude: ModuleLocation = ModuleLocation(core, "prelude")
private val i8: ModuleLocation = ModuleLocation(core, "i8")
private val i16: ModuleLocation = ModuleLocation(core, "i16")
private val i32: ModuleLocation = ModuleLocation(core, "i32")
private val i64: ModuleLocation = ModuleLocation(core, "i64")
private val f32: ModuleLocation = ModuleLocation(core, "f32")
private val f64: ModuleLocation = ModuleLocation(core, "f64")
private val str: ModuleLocation = ModuleLocation(core, "str")
private val i8_array: ModuleLocation = ModuleLocation(core, "i8_array")
private val i32_array: ModuleLocation = ModuleLocation(core, "i32_array")
private val i64_array: ModuleLocation = ModuleLocation(core, "i64_array")
private val vec: ModuleLocation = ModuleLocation(core, "vec")
private val struct: ModuleLocation = ModuleLocation(core, "struct")

private val builtins: Map<DefinitionLocation, Builtin> = listOf(
  object : Builtin(prelude / "++") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.StrOf ?: return null
      val b = args[1].value as? Value.StrOf ?: return null
      return Value.StrOf(a.value + b.value)
    }
  },

  object : Builtin(i8 / "to_string") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I8Of ?: return null
      return Value.StrOf(a.value.toString())
    }
  },

  object : Builtin(i16 / "to_string") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I16Of ?: return null
      return Value.StrOf(a.value.toString())
    }
  },

  object : Builtin(i32 / "+") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.I32Of(a.value + b.value)
    }
  },

  object : Builtin(i32 / "-") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.I32Of(a.value - b.value)
    }
  },

  object : Builtin(i32 / "*") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.I32Of(a.value * b.value)
    }
  },

  object : Builtin(i32 / "/") {
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

  object : Builtin(i32 / "%") {
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

  object : Builtin(i32 / "min") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.I32Of(min(a.value, b.value))
    }
  },

  object : Builtin(i32 / "max") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.I32Of(max(a.value, b.value))
    }
  },

  object : Builtin(i32 / "=") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.BoolOf(a.value == b.value)
    }
  },

  object : Builtin(i32 / "<") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.BoolOf(a.value < b.value)
    }
  },

  object : Builtin(i32 / "<=") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.BoolOf(a.value <= b.value)
    }
  },

  object : Builtin(i32 / ">") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.BoolOf(a.value > b.value)
    }
  },

  object : Builtin(i32 / ">=") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.BoolOf(a.value >= b.value)
    }
  },

  object : Builtin(i32 / "!=") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      val b = args[1].value as? Value.I32Of ?: return null
      return Value.BoolOf(a.value != b.value)
    }
  },

  object : Builtin(i32 / "to_byte") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      return Value.I8Of(a.value.toByte())
    }
  },

  object : Builtin(i32 / "to_short") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      return Value.I16Of(a.value.toShort())
    }
  },

  object : Builtin(i32 / "to_long") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      return Value.I64Of(a.value.toLong())
    }
  },

  object : Builtin(i32 / "to_float") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      return Value.F32Of(a.value.toFloat())
    }
  },

  object : Builtin(i32 / "to_double") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      return Value.F64Of(a.value.toDouble())
    }
  },

  object : Builtin(i32 / "to_string") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32Of ?: return null
      return Value.StrOf(a.value.toString())
    }
  },

  object : Builtin(i64 / "to_string") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I64Of ?: return null
      return Value.StrOf(a.value.toString())
    }
  },

  object : Builtin(f32 / "to_string") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.F32Of ?: return null
      return Value.StrOf(a.value.toString())
    }
  },

  object : Builtin(f64 / "to_string") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.F64Of ?: return null
      return Value.StrOf(a.value.toString())
    }
  },

  object : Builtin(str / "size") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.StrOf ?: return null
      return Value.I32Of(a.value.length)
    }
  },

  object : Builtin(i8_array / "size") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I8ArrayOf ?: return null
      return Value.I32Of(a.elements.size)
    }
  },

  object : Builtin(i32_array / "size") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I32ArrayOf ?: return null
      return Value.I32Of(a.elements.size)
    }
  },

  object : Builtin(i64_array / "size") {
    override fun eval(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.I64ArrayOf ?: return null
      return Value.I32Of(a.elements.size)
    }
  },
).associateBy { it.name }
