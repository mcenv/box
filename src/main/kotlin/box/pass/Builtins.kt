package box.pass

import box.ast.common.ModuleLocation
import kotlin.math.max
import kotlin.math.min
import box.ast.Core as C

fun lookupBuiltin(name: String): Builtin? {
  return builtins[name]
}

// TODO: use sealed class
abstract class Builtin(val name: String) {
  abstract val type: C.Term.Func

  abstract operator fun invoke(args: List<Lazy<Value>>): Value?
}

const val core: String = "core"
val prelude: ModuleLocation = ModuleLocation(core, "prelude")

@Suppress("UNCHECKED_CAST")
private val builtins: Map<String, Builtin> = listOf(
  object : Builtin("prelude::++") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.Wtf16, C.Pattern.Drop to C.Term.Wtf16),
      C.Term.Wtf16,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<String> ?: return null
      val b = args[1].value as? Value.ConstOf<String> ?: return null
      return Value.ConstOf(a.value + b.value)
    }
  },

  object : Builtin("i8::=") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I8, C.Pattern.Drop to C.Term.I8),
      C.Term.Bool,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Byte> ?: return null
      val b = args[1].value as? Value.ConstOf<Byte> ?: return null
      return Value.ConstOf(a.value == b.value)
    }
  },

  object : Builtin("i8::to_i16") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I8),
      C.Term.I16,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Byte> ?: return null
      return Value.ConstOf(a.value.toShort())
    }
  },

  object : Builtin("i8::to_i32") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I8),
      C.Term.I32,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Byte> ?: return null
      return Value.ConstOf(a.value.toInt())
    }
  },

  object : Builtin("i8::to_i64") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I8),
      C.Term.I64,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Byte> ?: return null
      return Value.ConstOf(a.value.toLong())
    }
  },

  object : Builtin("i8::to_f32") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I8),
      C.Term.F32,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Byte> ?: return null
      return Value.ConstOf(a.value.toFloat())
    }
  },

  object : Builtin("i8::to_f64") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I8),
      C.Term.F64,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Byte> ?: return null
      return Value.ConstOf(a.value.toDouble())
    }
  },

  object : Builtin("i8::to_wtf16") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I8),
      C.Term.Wtf16,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Byte> ?: return null
      return Value.ConstOf(a.value.toString())
    }
  },

  object : Builtin("i16::=") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I16, C.Pattern.Drop to C.Term.I16),
      C.Term.Bool,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Short> ?: return null
      val b = args[1].value as? Value.ConstOf<Short> ?: return null
      return Value.ConstOf(a.value == b.value)
    }
  },

  object : Builtin("i16::to_i8") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I16),
      C.Term.I8,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Short> ?: return null
      return Value.ConstOf(a.value.toByte())
    }
  },

  object : Builtin("i16::to_i32") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I16),
      C.Term.I32,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Short> ?: return null
      return Value.ConstOf(a.value.toInt())
    }
  },

  object : Builtin("i16::to_i64") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I16),
      C.Term.I64,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Short> ?: return null
      return Value.ConstOf(a.value.toLong())
    }
  },

  object : Builtin("i16::to_f32") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I16),
      C.Term.F32,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Short> ?: return null
      return Value.ConstOf(a.value.toFloat())
    }
  },

  object : Builtin("i16::to_f64") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I16),
      C.Term.F64,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Short> ?: return null
      return Value.ConstOf(a.value.toDouble())
    }
  },

  object : Builtin("i16::to_wtf16") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I16),
      C.Term.Wtf16,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Short> ?: return null
      return Value.ConstOf(a.value.toString())
    }
  },

  object : Builtin("i32::+") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I32, C.Pattern.Drop to C.Term.I32),
      C.Term.I32,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Int> ?: return null
      val b = args[1].value as? Value.ConstOf<Int> ?: return null
      return Value.ConstOf(a.value + b.value)
    }
  },

  object : Builtin("i32::-") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I32, C.Pattern.Drop to C.Term.I32),
      C.Term.I32,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Int> ?: return null
      val b = args[1].value as? Value.ConstOf<Int> ?: return null
      return Value.ConstOf(a.value - b.value)
    }
  },

  object : Builtin("i32::*") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I32, C.Pattern.Drop to C.Term.I32),
      C.Term.I32,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Int> ?: return null
      val b = args[1].value as? Value.ConstOf<Int> ?: return null
      return Value.ConstOf(a.value * b.value)
    }
  },

  object : Builtin("i32::/") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I32, C.Pattern.Drop to C.Term.I32),
      C.Term.I32,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val b = args[1].value as? Value.ConstOf<Int> ?: return null
      return if (b.value == 0) {
        Value.ConstOf(0)
      } else {
        val a = args[0].value as? Value.ConstOf<Int> ?: return null
        Value.ConstOf(Math.floorDiv(a.value, b.value))
      }
    }
  },

  object : Builtin("i32::%") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I32, C.Pattern.Drop to C.Term.I32),
      C.Term.I32,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val b = args[1].value as? Value.ConstOf<Int> ?: return null
      return if (b.value == 0) {
        Value.ConstOf(0)
      } else {
        val a = args[0].value as? Value.ConstOf<Int> ?: return null
        Value.ConstOf(Math.floorMod(a.value, b.value))
      }
    }
  },

  object : Builtin("i32::min") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I32, C.Pattern.Drop to C.Term.I32),
      C.Term.I32,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Int> ?: return null
      val b = args[1].value as? Value.ConstOf<Int> ?: return null
      return Value.ConstOf(min(a.value, b.value))
    }
  },

  object : Builtin("i32::max") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I32, C.Pattern.Drop to C.Term.I32),
      C.Term.I32,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Int> ?: return null
      val b = args[1].value as? Value.ConstOf<Int> ?: return null
      return Value.ConstOf(max(a.value, b.value))
    }
  },

  object : Builtin("i32::=") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I32, C.Pattern.Drop to C.Term.I32),
      C.Term.Bool,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Int> ?: return null
      val b = args[1].value as? Value.ConstOf<Int> ?: return null
      return Value.ConstOf(a.value == b.value)
    }
  },

  object : Builtin("i32::<") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I32, C.Pattern.Drop to C.Term.I32),
      C.Term.Bool,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Int> ?: return null
      val b = args[1].value as? Value.ConstOf<Int> ?: return null
      return Value.ConstOf(a.value < b.value)
    }
  },

  object : Builtin("i32::<=") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I32, C.Pattern.Drop to C.Term.I32),
      C.Term.Bool,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Int> ?: return null
      val b = args[1].value as? Value.ConstOf<Int> ?: return null
      return Value.ConstOf(a.value <= b.value)
    }
  },

  object : Builtin("i32::>") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I32, C.Pattern.Drop to C.Term.I32),
      C.Term.Bool,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Int> ?: return null
      val b = args[1].value as? Value.ConstOf<Int> ?: return null
      return Value.ConstOf(a.value > b.value)
    }
  },

  object : Builtin("i32::>=") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I32, C.Pattern.Drop to C.Term.I32),
      C.Term.Bool,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Int> ?: return null
      val b = args[1].value as? Value.ConstOf<Int> ?: return null
      return Value.ConstOf(a.value >= b.value)
    }
  },

  object : Builtin("i32::!=") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I32, C.Pattern.Drop to C.Term.I32),
      C.Term.Bool,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Int> ?: return null
      val b = args[1].value as? Value.ConstOf<Int> ?: return null
      return Value.ConstOf(a.value != b.value)
    }
  },

  object : Builtin("i32::to_i8") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I32),
      C.Term.I8,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Int> ?: return null
      return Value.ConstOf(a.value.toByte())
    }
  },

  object : Builtin("i32::to_i16") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I32),
      C.Term.I16,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Int> ?: return null
      return Value.ConstOf(a.value.toShort())
    }
  },

  object : Builtin("i32::to_i64") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I32),
      C.Term.I64,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Int> ?: return null
      return Value.ConstOf(a.value.toLong())
    }
  },

  object : Builtin("i32::to_f32") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I32),
      C.Term.F32,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Int> ?: return null
      return Value.ConstOf(a.value.toFloat())
    }
  },

  object : Builtin("i32::to_f64") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I32),
      C.Term.F64,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Int> ?: return null
      return Value.ConstOf(a.value.toDouble())
    }
  },

  object : Builtin("i32::to_wtf16") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I32),
      C.Term.Wtf16,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Int> ?: return null
      return Value.ConstOf(a.value.toString())
    }
  },

  object : Builtin("i64::!=") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I64, C.Pattern.Drop to C.Term.I64),
      C.Term.Bool,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Long> ?: return null
      val b = args[1].value as? Value.ConstOf<Long> ?: return null
      return Value.ConstOf(a.value != b.value)
    }
  },

  object : Builtin("i64::to_wtf16") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.I64),
      C.Term.Wtf16,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Long> ?: return null
      return Value.ConstOf(a.value.toString())
    }
  },

  object : Builtin("f32::!=") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.F32, C.Pattern.Drop to C.Term.F32),
      C.Term.Bool,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Float> ?: return null
      val b = args[1].value as? Value.ConstOf<Float> ?: return null
      return Value.ConstOf(a.value != b.value)
    }
  },

  object : Builtin("f32::to_wtf16") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.F32),
      C.Term.Wtf16,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Float> ?: return null
      return Value.ConstOf(a.value.toString())
    }
  },

  object : Builtin("f64::!=") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.F64, C.Pattern.Drop to C.Term.F64),
      C.Term.Bool,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Double> ?: return null
      val b = args[1].value as? Value.ConstOf<Double> ?: return null
      return Value.ConstOf(a.value != b.value)
    }
  },

  object : Builtin("f64::to_wtf16") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.F64),
      C.Term.Wtf16,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<Double> ?: return null
      return Value.ConstOf(a.value.toString())
    }
  },

  object : Builtin("wtf16::!=") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.Wtf16, C.Pattern.Drop to C.Term.Wtf16),
      C.Term.Bool,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<String> ?: return null
      val b = args[1].value as? Value.ConstOf<String> ?: return null
      return Value.ConstOf(a.value != b.value)
    }
  },

  object : Builtin("wtf16::size") {
    override val type: C.Term.Func = C.Term.Func(
      false,
      listOf(C.Pattern.Drop to C.Term.Wtf16),
      C.Term.I32,
    )

    override fun invoke(args: List<Lazy<Value>>): Value? {
      val a = args[0].value as? Value.ConstOf<String> ?: return null
      return Value.ConstOf(a.value.length)
    }
  },
).associateBy { it.name }
