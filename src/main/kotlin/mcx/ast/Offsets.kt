package mcx.ast

/**
 * A de Bruijn index.
 */
@JvmInline
value class Idx(val value: Int)

/**
 * A de Bruijn level.
 */
@JvmInline
value class Lvl(val value: Int) {
  operator fun plus(offset: Int): Lvl {
    return Lvl(value + offset)
  }
}

/**
 * Converts [idx] to the corresponding [Lvl] under the context of the [this] [Lvl] size.
 */
fun Lvl.toLvl(idx: Idx): Lvl {
  return Lvl(this.value - idx.value - 1)
}

/**
 * Converts [lvl] to the corresponding [Idx] under the context of the [this] [Lvl] size.
 */
fun Lvl.toIdx(lvl: Lvl): Idx {
  return Idx(this.value - lvl.value - 1)
}
