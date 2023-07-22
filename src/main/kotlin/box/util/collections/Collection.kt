package box.util.collections

inline fun <S, T, R> Iterable<T>.mapWith(state: S, transform: S.(modify: (S) -> Unit, T) -> R): Pair<S, List<R>> {
  val result = mutableListOf<R>()
  var current = state
  for (element in this) {
    result += current.transform({ current = it }, element)
  }
  return current to result
}

inline fun <T> Iterable<T>.forEachSeparated(
  separate: () -> Unit,
  action: (T) -> Unit,
) {
  val iterator = iterator()
  if (iterator.hasNext()) {
    action(iterator.next())
  } else {
    return
  }
  while (iterator.hasNext()) {
    separate()
    action(iterator.next())
  }
}
