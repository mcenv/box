package mcx.phase

import mcx.ast.DefinitionLocation
import mcx.ast.ModuleLocation
import mcx.ast.Value
import kotlin.math.max
import kotlin.math.min

val PRELUDE = ModuleLocation("prelude")

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
).associateBy { it.name }

sealed class Builtin(
  val name: DefinitionLocation,
) {
  abstract val commands: List<String>

  abstract fun eval(arg: Value): Value?
}

object Command : Builtin(PRELUDE / "command") {
  override val commands: List<String> get() = emptyList()

  override fun eval(arg: Value): Value? {
    if (arg !is Value.StringOf) return null
    return Value.CodeOf(lazyOf(Value.Command(arg.value)))
  }
}

object IntAdd : Builtin(PRELUDE / "+") {
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

object IntSub : Builtin(PRELUDE / "-") {
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

object IntMul : Builtin(PRELUDE / "*") {
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

object IntDiv : Builtin(PRELUDE / "/") {
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

object IntMod : Builtin(PRELUDE / "%") {
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

object IntMin : Builtin(PRELUDE / "min") {
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

object IntMax : Builtin(PRELUDE / "max") {
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

object IntEq : Builtin(PRELUDE / "=") {
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

object IntLt : Builtin(PRELUDE / "<") {
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

object IntLe : Builtin(PRELUDE / "<=") {
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

object IntGt : Builtin(PRELUDE / ">") {
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

object IntGe : Builtin(PRELUDE / ">=") {
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

object IntNe : Builtin(PRELUDE / "!=") {
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

object IntToByte : Builtin(PRELUDE / "int_to_byte") {
  override val commands: List<String> = listOf(
    "data modify storage mcx: byte append value 0b",
    "execute store result storage mcx: byte[-1] byte 1 run data get storage mcx: int[-1]",
  )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntOf) return null
    return Value.ByteOf(arg.value.toByte())
  }
}

object IntToShort : Builtin(PRELUDE / "int_to_short") {
  override val commands: List<String> = listOf(
    "data modify storage mcx: short append value 0s",
    "execute store result storage mcx: short[-1] short 1 run data get storage mcx: int[-1]",
  )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntOf) return null
    return Value.ShortOf(arg.value.toShort())
  }
}

object IntToInt : Builtin(PRELUDE / "int_to_int") {
  override val commands: List<String> = emptyList()

  override fun eval(arg: Value): Value {
    return arg
  }
}

object IntToLong : Builtin(PRELUDE / "int_to_long") {
  override val commands: List<String> = listOf(
    "data modify storage mcx: long append value 0l",
    "execute store result storage mcx: long[-1] long 1 run data get storage mcx: int[-1]",
  )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntOf) return null
    return Value.LongOf(arg.value.toLong())
  }
}

object IntToFloat : Builtin(PRELUDE / "int_to_float") {
  override val commands: List<String> = listOf(
    "data modify storage mcx: float append value 0.0f",
    "execute store result storage mcx: float[-1] float 1 run data get storage mcx: int[-1]",
  )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntOf) return null
    return Value.FloatOf(arg.value.toFloat())
  }
}

object IntToDouble : Builtin(PRELUDE / "int_to_double") {
  override val commands: List<String> = listOf(
    "data modify storage mcx: double append value 0.0",
    "execute store result storage mcx: double[-1] double 1 run data get storage mcx: int[-1]",
  )

  override fun eval(arg: Value): Value? {
    if (arg !is Value.IntOf) return null
    return Value.DoubleOf(arg.value.toDouble())
  }
}

object IntDup : Builtin(PRELUDE / "int_dup") {
  override val commands: List<String> = listOf(
    "data modify storage mcx: int append from storage mcx: int[-1]",
  )

  override fun eval(arg: Value): Value {
    return Value.TupleOf(listOf(lazyOf(arg), lazyOf(arg)))
  }
}
