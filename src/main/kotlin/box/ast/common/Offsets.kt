package box.ast.common

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
 * Converts [this] [Idx] to the corresponding [Lvl] under the context of the [size].
 */
fun Idx.toLvl(size: Lvl): Lvl {
  return Lvl(size.value - this.value - 1)
}

/**
 * Converts [this] [Lvl] to the corresponding [Idx] under the context of the [size].
 */
fun Lvl.toIdx(size: Lvl): Idx {
  return Idx(size.value - this.value - 1)
}
