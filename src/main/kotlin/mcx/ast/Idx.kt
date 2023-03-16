package mcx.ast

@JvmInline
value class Idx(val value: Int)

fun Lvl.toLvl(idx: Idx): Lvl = Lvl(this.value - idx.value - 1)
