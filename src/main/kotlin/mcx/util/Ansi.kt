@file:Suppress("NOTHING_TO_INLINE")

package mcx.util

inline fun red(text: String): String {
  return "\u001B[31m$text\u001B[0m"
}

inline fun green(text: String): String {
  return "\u001B[32m$text\u001B[0m"
}

inline fun yellow(text: String): String {
  return "\u001B[33m$text\u001B[0m"
}

inline fun blue(text: String): String {
  return "\u001B[34m$text\u001B[0m"
}

inline fun magenta(text: String): String {
  return "\u001B[35m$text\u001B[0m"
}

inline fun cyan(text: String): String {
  return "\u001B[36m$text\u001B[0m"
}

inline fun white(text: String): String {
  return "\u001B[37m$text\u001B[0m"
}
