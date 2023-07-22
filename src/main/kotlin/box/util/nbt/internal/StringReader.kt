package box.util.nbt.internal

internal class StringReader(
  private val string: String,
) {
  private var cursor: Int = 0

  fun canRead(length: Int = 1): Boolean {
    return cursor + length <= string.length
  }

  fun peek(offset: Int = 0): Char {
    return string[cursor + offset]
  }

  private fun read(): Char {
    return string[cursor++]
  }

  fun skip() {
    ++cursor
  }

  fun expect(char: Char) {
    skipWhitespace()
    require(canRead())
    check(peek() == char)
    skip()
  }

  fun skipWhitespace() {
    while (canRead() && Character.isWhitespace(peek())) {
      skip()
    }
  }

  fun readQuotedString(): String {
    if (!canRead()) {
      return ""
    }
    val terminator = peek()
    require(terminator.isQuotedStringStart())
    skip()
    return readStringUntil(terminator)
  }

  private fun Char.isAllowedInUnquotedString(): Boolean {
    return when (this) {
      in '0'..'9', in 'A'..'Z', in 'a'..'z', '_', '-', '.', '+' -> true
      else                                                      -> false
    }
  }

  fun readUnquotedString(): String {
    val start = cursor
    while (canRead() && peek().isAllowedInUnquotedString()) {
      skip()
    }
    return string.substring(start, cursor)
  }

  fun readString(): String {
    if (!canRead()) {
      return ""
    }
    val terminator = peek()
    return if (terminator.isQuotedStringStart()) {
      readStringUntil(terminator)
    } else {
      readUnquotedString()
    }
  }

  private fun readStringUntil(terminator: Char): String {
    val result = StringBuilder()
    var escaped = false
    while (canRead()) {
      if (escaped) {
        when (val char = read()) {
          terminator, '\\' -> {
            result.append(char)
            escaped = false
          }
          else             -> {
            error("Invalid escape $char")
          }
        }
      } else {
        when (val char = read()) {
          '\\'       -> {
            escaped = true
          }
          terminator -> {
            return result.toString()
          }
          else       -> {
            result.append(char)
          }
        }
      }
    }
    error("Expected end of quote")
  }
}

fun Char.isQuotedStringStart(): Boolean {
  return when (this) {
    '"', '\'' -> true
    else      -> false
  }
}
