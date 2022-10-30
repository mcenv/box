package mcx.phase

fun interface Phase<A, B> {
  operator fun invoke(
    context: Context,
    input: A,
  ): B
}

@Suppress("NOTHING_TO_INLINE")
inline operator fun <A, B, C> Phase<A, B>.rangeTo(other: Phase<B, C>): Phase<A, C> =
  Phase { context, a ->
    other(
      context,
      this(
        context,
        a,
      )
    )
  }
