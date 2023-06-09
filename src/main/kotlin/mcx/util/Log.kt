package mcx.util

fun info(
  summary: String,
  body: String,
) {
  return println("${green(summary)} $body")
}

fun debug(
  summary: String,
  body: String,
) {
  return println("${magenta(summary)} $body")
}
