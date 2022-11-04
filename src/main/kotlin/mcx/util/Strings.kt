package mcx.util

fun String.quoted(
  quote: Char,
): String {
  val builder = StringBuilder()
  builder.append(quote)
  forEach {
    when (it) {
      '\\', quote -> builder.append('\\')
    }
    builder.append(it)
  }
  builder.append(quote)
  return builder.toString()
}
