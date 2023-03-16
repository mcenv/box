package mcx.ast

@JvmInline
value class Lvl(val value: Int) {
  operator fun plus(offset: Int): Lvl = Lvl(value + offset)
}

fun Lvl.toIdx(lvl: Lvl): Idx = Idx(this.value - lvl.value - 1)
