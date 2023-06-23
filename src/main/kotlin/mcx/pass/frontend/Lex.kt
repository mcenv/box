package mcx.pass.frontend

import mcx.ast.Lexed
import mcx.lsp.diagnostic
import mcx.lsp.rangeTo
import mcx.pass.Context
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position

@Suppress("NOTHING_TO_INLINE")
class Lex private constructor(
  private val text: String,
) {
  private val diagnostics: MutableList<Diagnostic> = mutableListOf()
  private val length: Int = text.length
  private var cursor: Int = 0
  private var line: Int = 0
  private var character: Int = 0

  private fun lex(): List<Lexed> {
    TODO()
  }

  private inline fun <A> bracketed(prefix: Char, suffix: Char, parseA: () -> A): A {
    !prefix
    skipTrivia()
    val a = parseA()
    skipTrivia()
    if (!suffix == null) {
      skipWhile { it != suffix }
      !suffix
    }
    return a
  }

  private inline fun <A> choice(choice: (Char) -> () -> A): A? {
    return if (canRead()) {
      choice(peek())()
    } else {
      null
    }
  }

  private inline fun <A> separated(separator: Char, prefix: Char? = null, suffix: Char? = null, parseA: () -> A): List<A> {
    prefix?.let { !prefix }
    val `as` = mutableListOf<A>()
    while (canRead() && peek() != suffix) {
      skipTrivia()
      `as` += parseA() ?: break
      skipTrivia()
      if (!separator == null) {
        break
      }
    }
    suffix?.let {
      skipTrivia()
      if (!suffix == null) {
        skipWhile { it != suffix }
        !suffix
      }
    }
    return `as`
  }

  private inline fun <A> list(parseA: () -> A?): List<A> {
    val `as` = mutableListOf<A>()
    while (canRead()) {
      skipTrivia()
      `as` += parseA() ?: break
    }
    skipTrivia()
    return `as`
  }

  private inline fun <A> default(default: A, parseA: () -> A?): A {
    return parseA() ?: default
  }

  private inline fun word(predicate: (Char) -> Boolean): String {
    val start = cursor
    while (canRead() && predicate(peek())) {
      skipUnsafe()
    }
    return text.substring(start, cursor)
  }

  private inline operator fun String.not(): String? {
    return if (text.startsWith(this, cursor)) {
      skipUnsafe(length)
      this
    } else {
      diagnostics += expectedString(this, here())
      null
    }
  }

  private inline operator fun Char.not(): Char? {
    return if (canRead() && peek() == this) {
      skipUnsafe()
      this
    } else {
      diagnostics += expectedChar(this, here())
      null
    }
  }

  private inline fun skipWhile(predicate: (Char) -> Boolean) {
    while (canRead() && predicate(peek())) {
      skipUnsafe()
    }
  }

  private inline fun skipTrivia() {
    while (canRead() && skipWhitespace()) {
    }
  }

  private inline fun skipWhitespace(): Boolean {
    return when (peek()) {
      ' '  -> {
        skipUnsafe()
        true
      }
      '\n' -> {
        ++cursor
        ++line
        character = 0
        true
      }
      '\r' -> {
        ++cursor
        ++line
        character = 0
        if (canRead() && peek() == '\n') {
          ++cursor
        }
        true
      }
      '#'  -> {
        // ignore doc
        if (cursor + 1 < length && text[cursor + 1] == '|') {
          false
        } else {
          skipUnsafe()
          while (
            canRead() && when (peek()) {
              '\n', '\r' -> false
              else       -> true
            }
          ) {
            skipUnsafe()
          }
          true
        }
      }
      else -> {
        false
      }
    }
  }

  private inline fun skipUnsafe() {
    ++cursor
    ++character
  }

  private inline fun skipUnsafe(size: Int) {
    cursor += size
    character += size
  }

  private inline fun peek(): Char {
    return text[cursor]
  }

  private inline fun peek(offset: Int): Char {
    return text[cursor + offset]
  }

  private inline fun canRead(): Boolean {
    return cursor < length
  }

  private inline fun canRead(size: Int): Boolean {
    return cursor + size < length
  }

  private inline fun here(): Position {
    return Position(line, character)
  }

  data class Result(
    val tokens: List<Lexed>,
    val diagnostics: List<Diagnostic>,
  )

  companion object {
    private fun expectedString(
      string: String,
      position: Position,
    ): Diagnostic {
      return diagnostic(
        position..Position(position.line, position.character + string.length),
        "expected: '$string'",
        DiagnosticSeverity.Error,
      )
    }

    private fun expectedChar(
      char: Char,
      position: Position,
    ): Diagnostic {
      return diagnostic(
        position..Position(position.line, position.character + 1),
        "expected: '$char'",
        DiagnosticSeverity.Error,
      )
    }

    operator fun invoke(
      context: Context,
      text: String,
    ): Result {
      return Lex(text).run {
        Result(lex(), diagnostics)
      }
    }
  }
}
