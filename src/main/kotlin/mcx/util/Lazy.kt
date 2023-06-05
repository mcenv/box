package mcx.util

inline fun <T, R> Lazy<T>.map(crossinline transform: (T) -> R): Lazy<R> {
  return lazy { transform(value) }
}
