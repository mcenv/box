package mcx.util

inline fun <S, T, R> Iterable<T>.mapWith(state: S, transform: S.(modify: (S) -> Unit, T) -> R): List<R> {
  val result = mutableListOf<R>()
  var current = state
  for (element in this) {
    result += current.transform({ current = it }, element)
  }
  return result
}
