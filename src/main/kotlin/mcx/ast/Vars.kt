package mcx.ast

@JvmInline
value class Idx(val value: Int)

@JvmInline
value class Lvl(val value: Int) {
  operator fun plus(offset: Int): Lvl = Lvl(value + offset)
}

fun Lvl.toLvl(idx: Idx): Lvl = Lvl(this.value - idx.value - 1)

fun Lvl.toIdx(lvl: Lvl): Idx = Idx(this.value - lvl.value - 1)

