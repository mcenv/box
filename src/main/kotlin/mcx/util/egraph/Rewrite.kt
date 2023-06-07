package mcx.util.egraph

data class Rewrite(
  val before: Pattern,
  val after: Pattern,
) {
  override fun toString(): String {
    return "$before -> $after"
  }
}

@Suppress("NOTHING_TO_INLINE")
inline infix fun Pattern.rewritesTo(after: Pattern): Rewrite {
  return Rewrite(this, after)
}
