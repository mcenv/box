package mcx.phase

import mcx.ast.Location
import mcx.ast.Value
import kotlin.math.max
import kotlin.math.min

val BUILTINS: Map<Location, Builtin> = listOf(
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
).associateBy { it.name }

sealed class Builtin(
  val name: Location,
) {
  abstract val commands: List<String>

  abstract fun eval(arg: Value): Value?
}

object Command : Builtin(Location("prelude", "command")) {
  override val commands: List<String> get() = emptyList()

  override fun eval(arg: Value): Value? {
    if (arg !is Value.StringOf) return null
    return Value.CodeOf(lazyOf(Value.Command(arg.value)))
  }
}

object IntAdd : Builtin(Location("prelude", "+")) {
  override val commands: List<String> = listOf(
    "execute store result score #0 mcx run data get storage mcx: int[-1]",
    "data remove storage mcx: int[-1]",
    "execute store result score #1 mcx run data get storage mcx: int[-1]",
    "execute store result storage mcx: int[-1] int 1 run scoreboard players operation #1 mcx += #0 mcx",
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

object IntSub : Builtin(Location("prelude", "-")) {
  override val commands: List<String> = listOf(
    "execute store result score #0 mcx run data get storage mcx: int[-1]",
    "data remove storage mcx: int[-1]",
    "execute store result score #1 mcx run data get storage mcx: int[-1]",
    "execute store result storage mcx: int[-1] int 1 run scoreboard players operation #1 mcx -= #0 mcx",
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

object IntMul : Builtin(Location("prelude", "*")) {
  override val commands: List<String> = listOf(
    "execute store result score #0 mcx run data get storage mcx: int[-1]",
    "data remove storage mcx: int[-1]",
    "execute store result score #1 mcx run data get storage mcx: int[-1]",
    "execute store result storage mcx: int[-1] int 1 run scoreboard players operation #1 mcx *= #0 mcx",
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

object IntDiv : Builtin(Location("prelude", "/")) {
  override val commands: List<String> = listOf(
    "execute store result score #0 mcx run data get storage mcx: int[-1]",
    "data remove storage mcx: int[-1]",
    "execute store result score #1 mcx run data get storage mcx: int[-1]",
    "execute store result storage mcx: int[-1] int 1 run scoreboard players operation #1 mcx /= #0 mcx",
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

object IntMod : Builtin(Location("prelude", "%")) {
  override val commands: List<String> = listOf(
    "execute store result score #0 mcx run data get storage mcx: int[-1]",
    "data remove storage mcx: int[-1]",
    "execute store result score #1 mcx run data get storage mcx: int[-1]",
    "execute store result storage mcx: int[-1] int 1 run scoreboard players operation #1 mcx %= #0 mcx",
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

object IntMin : Builtin(Location("prelude", "min")) {
  override val commands: List<String> = listOf(
    "execute store result score #0 mcx run data get storage mcx: int[-1]",
    "data remove storage mcx: int[-1]",
    "execute store result score #1 mcx run data get storage mcx: int[-1]",
    "execute store result storage mcx: int[-1] int 1 run scoreboard players operation #1 mcx < #0 mcx",
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

object IntMax : Builtin(Location("prelude", "max")) {
  override val commands: List<String> = listOf(
    "execute store result score #0 mcx run data get storage mcx: int[-1]",
    "data remove storage mcx: int[-1]",
    "execute store result score #1 mcx run data get storage mcx: int[-1]",
    "execute store result storage mcx: int[-1] int 1 run scoreboard players operation #1 mcx > #0 mcx",
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

object IntEq : Builtin(Location("prelude", "=")) {
  override val commands: List<String> = listOf(
    "execute store result score #0 mcx run data get storage mcx: int[-1]",
    "data remove storage mcx: int[-1]",
    "execute store result score #1 mcx run data get storage mcx: int[-1]",
    "data modify storage mcx: byte append value 0b",
    "execute if score #1 mcx = #0 mcx run data modify storage mcx: byte[-1] set value 1b",
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

object IntLt : Builtin(Location("prelude", "<")) {
  override val commands: List<String> = listOf(
    "execute store result score #0 mcx run data get storage mcx: int[-1]",
    "data remove storage mcx: int[-1]",
    "execute store result score #1 mcx run data get storage mcx: int[-1]",
    "data modify storage mcx: byte append value 0b",
    "execute if score #1 mcx < #0 mcx run data modify storage mcx: byte[-1] set value 1b",
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

object IntLe : Builtin(Location("prelude", "<=")) {
  override val commands: List<String> = listOf(
    "execute store result score #0 mcx run data get storage mcx: int[-1]",
    "data remove storage mcx: int[-1]",
    "execute store result score #1 mcx run data get storage mcx: int[-1]",
    "data modify storage mcx: byte append value 0b",
    "execute if score #1 mcx <= #0 mcx run data modify storage mcx: byte[-1] set value 1b",
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

object IntGt : Builtin(Location("prelude", ">")) {
  override val commands: List<String> = listOf(
    "execute store result score #0 mcx run data get storage mcx: int[-1]",
    "data remove storage mcx: int[-1]",
    "execute store result score #1 mcx run data get storage mcx: int[-1]",
    "data modify storage mcx: byte append value 0b",
    "execute if score #1 mcx > #0 mcx run data modify storage mcx: byte[-1] set value 1b",
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

object IntGe : Builtin(Location("prelude", ">=")) {
  override val commands: List<String> = listOf(
    "execute store result score #0 mcx run data get storage mcx: int[-1]",
    "data remove storage mcx: int[-1]",
    "execute store result score #1 mcx run data get storage mcx: int[-1]",
    "data modify storage mcx: byte append value 0b",
    "execute if score #1 mcx >= #0 mcx run data modify storage mcx: byte[-1] set value 1b",
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

object IntNe : Builtin(Location("prelude", "!=")) {
  override val commands: List<String> = listOf(
    "execute store result score #0 mcx run data get storage mcx: int[-1]",
    "data remove storage mcx: int[-1]",
    "execute store result score #1 mcx run data get storage mcx: int[-1]",
    "data modify storage mcx: byte append value 1b",
    "execute if score #1 mcx = #0 mcx run data modify storage mcx: byte[-1] set value 0b",
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

object IntToByte : Builtin(Location("prelude", "int_to_byte")) {
  override val commands: List<String> = listOf(
    "data modify storage mcx: byte append value 0b",
    "execute store result storage mcx: byte[-1] byte 1 run data get storage mcx: int[-1]",
  )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntOf) return null
    return Value.ByteOf(arg.value.toByte())
  }
}

object IntToShort : Builtin(Location("prelude", "int_to_short")) {
  override val commands: List<String> = listOf(
    "data modify storage mcx: short append value 0s",
    "execute store result storage mcx: short[-1] short 1 run data get storage mcx: int[-1]",
  )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntOf) return null
    return Value.ShortOf(arg.value.toShort())
  }
}

object IntToInt : Builtin(Location("prelude", "int_to_int")) {
  override val commands: List<String> = emptyList()

  override fun eval(arg: Value): Value {
    return arg
  }
}

object IntToLong : Builtin(Location("prelude", "int_to_long")) {
  override val commands: List<String> = listOf(
    "data modify storage mcx: long append value 0l",
    "execute store result storage mcx: long[-1] long 1 run data get storage mcx: int[-1]",
  )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntOf) return null
    return Value.LongOf(arg.value.toLong())
  }
}

object IntToFloat : Builtin(Location("prelude", "int_to_float")) {
  override val commands: List<String> = listOf(
    "data modify storage mcx: float append value 0.0f",
    "execute store result storage mcx: float[-1] float 1 run data get storage mcx: int[-1]",
  )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntOf) return null
    return Value.FloatOf(arg.value.toFloat())
  }
}

object IntToDouble : Builtin(Location("prelude", "int_to_double")) {
  override val commands: List<String> = listOf(
    "data modify storage mcx: double append value 0.0",
    "execute store result storage mcx: double[-1] double 1 run data get storage mcx: int[-1]",
  )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntOf) return null
    return Value.DoubleOf(arg.value.toDouble())
  }
}

object IntDup : Builtin(Location("prelude", "int_dup")) {
  override val commands: List<String> = listOf(
    "data modify storage mcx: int append from storage mcx: int[-1]",
  )

  override fun eval(arg: Value): Value {
    return Value.TupleOf(listOf(lazyOf(arg), lazyOf(arg)))
  }
}
