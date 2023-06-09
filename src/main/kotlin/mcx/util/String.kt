package mcx.util

import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Quotes [this] string with a given [quote] character.
 */
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

/**
 * Converts [this] integer to a subscript [String].
 */
fun Int.toSubscript(): String {
  return toString()
    .toCharArray()
    .joinToString("") {
      (it.code + ('₀' - '0'))
        .toChar()
        .toString()
    }
}

/**
 * Generates a cryptographically secure random [String] of 16 bytes.
 */
@OptIn(ExperimentalEncodingApi::class)
fun secureRandomString(): String {
  val random = SecureRandom()
  val bytes = ByteArray(16)
  random.nextBytes(bytes)
  return Base64.encode(bytes)
}
