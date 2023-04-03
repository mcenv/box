package mcx.ast

@JvmInline
value class Idx(val value: Int)

@JvmInline
value class Lvl(val value: Int) {
  operator fun plus(offset: Int): Lvl {
    return Lvl(value + offset)
  }
}

fun Lvl.toLvl(idx: Idx): Lvl {
  return Lvl(this.value - idx.value - 1)
}

fun Lvl.toIdx(lvl: Lvl): Idx {
  return Idx(this.value - lvl.value - 1)
}
