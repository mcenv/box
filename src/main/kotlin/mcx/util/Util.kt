@file:Suppress("NOTHING_TO_INLINE")

package mcx.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.nio.file.Path
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

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

data class DependencyTriple(
  val owner: String,
  val repository: String,
  val tag: String,
)

fun String.toDependencyTripleOrNull(): DependencyTriple? {
  val (_, owner, repository, tag) = Regex("""^([^/]+)/([^@]+)@(.+)$""").matchEntire(this)?.groupValues ?: return null
  return DependencyTriple(owner, repository, tag)
}

inline fun <A, B> Pair<A, A>.mapMono(transform: (A) -> B): Pair<B, B> {
  return transform(first) to transform(second)
}

inline fun <T, R> Lazy<T>.map(crossinline transform: (T) -> R): Lazy<R> {
  return lazy { transform(value) }
}

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

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> Path.decodeFromJson(json: Json = Json): T {
  return inputStream().buffered().use { json.decodeFromStream<T>(it) }
}

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> Path.encodeToJson(value: T, json: Json = Json) {
  outputStream().buffered().use { json.encodeToStream<T>(value, it) }
}

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


inline fun unreachable(): Nothing {
  error("Unreachable")
}
