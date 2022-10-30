package mcx.phase

import mcx.ast.Surface
import mcx.util.rangeTo
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import mcx.ast.Surface as S

@Suppress("NOTHING_TO_INLINE")
class Parse private constructor(
  private val context: Context,
  private val string: String,
) {
  private val length: Int = string.length
  private var cursor: Int = 0
  private var line: Int = 0
  private var character: Int = 0

  private fun parseRoot(): S.Root {
    val resources = mutableListOf<S.Resource0>()
    while (true) {
      skipWhitespaces()
      if (!canRead()) {
        break
      }
      val start = cursor
      resources += parseResource0()
      if (start == cursor) {
        break
      }
    }

    skipWhitespaces()
    if (canRead()) {
      context += Diagnostic.ExpectedEndOfFile(here())
    }

    return S.Root(resources)
  }

  private fun parseResource0(): S.Resource0 =
    ranging {
      if (canRead()) {
        when (readWord()) {
          "function" -> {
            skipWhitespaces()
            val name = readWord()
            skipWhitespaces()
            val params = parseList(
              ',',
              '[',
              ']',
            ) {
              skipWhitespaces()
              val key = readWord()
              skipWhitespaces()
              expect(':')
              skipWhitespaces()
              val value = parseType0()
              key to value
            }
            skipWhitespaces()
            expect(':')
            skipWhitespaces()
            val result = parseType0()
            skipWhitespaces()
            expect('=')
            skipWhitespaces()
            val body = parseTerm0()
            S.Resource0.Function(
              name,
              params,
              result,
              body,
              until(),
            )
          }
          else       -> null
        }
      } else {
        null
      }
      ?: run {
        val range = until()
        context += Diagnostic.ExpectedResource0(range)
        S.Resource0.Hole(range)
      }
    }

  private fun parseType0(): S.Type0 =
    ranging {
      if (canRead()) {
        when (peek()) {
          '('  -> {
            skip()
            skipWhitespaces()
            val type = parseType0()
            skipWhitespaces()
            expect(')')
            type
          }
          else -> when (readWord()) {
            "int" -> S.Type0.Int(until())
            "ref" -> {
              skipWhitespaces()
              expect('[')
              val type = S.Type0.Ref(
                parseType0(),
                until(),
              )
              skipWhitespaces()
              expect(']')
              type
            }
            else  -> null
          }
        }
      } else {
        null
      }
      ?: run {
        val range = until()
        context += Diagnostic.ExpectedType0(range)
        S.Type0.Hole(range)
      }
    }

  private fun parseTerm0(): S.Term0 =
    ranging {
      if (canRead()) {
        when (peek()) {
          '('  -> {
            skip()
            skipWhitespaces()
            val term = parseTerm0()
            skipWhitespaces()
            expect(')')
            term
          }
          '&'  -> {
            skip()
            skipWhitespaces()
            S.Term0.RefOf(
              parseTerm0(),
              until(),
            )
          }
          else -> when (val word = readWord()) {
            "let" -> {
              skipWhitespaces()
              val name = readWord()
              skipWhitespaces()
              expect('=')
              skipWhitespaces()
              val init = parseTerm0()
              skipWhitespaces()
              expect(';')
              skipWhitespaces()
              val body = parseTerm0()
              S.Term0.Let(
                name,
                init,
                body,
                until(),
              )
            }
            else  ->
              word
                .toIntOrNull()
                ?.let {
                  S.Term0.IntOf(
                    it,
                    until(),
                  )
                }
              ?: S.Term0.Var(
                word,
                until(),
              )
          }
        }
      } else {
        null
      }
      ?: run {
        val range = until()
        context += Diagnostic.ExpectedTerm0(range)
        S.Term0.Hole(range)
      }
    }

  private inline fun <R> parseList(
    separator: Char,
    prefix: Char,
    postfix: Char,
    parse: () -> R,
  ): List<R> {
    if (!expect(prefix)) {
      return emptyList()
    }

    val elements = mutableListOf<R>()
    while (true) {
      skipWhitespaces()
      if (!canRead() || peek() == postfix) {
        break
      }
      val start = cursor
      elements += parse()
      if (start == cursor) {
        break
      }
      skipWhitespaces()
      when (peek()) {
        separator -> skip()
        postfix   -> break
        else      -> expect(separator)
      }
    }

    while (canRead() && peek() != postfix) {
      skip()
    }
    expect(postfix)

    return elements
  }

  private fun readWord(): String {
    val start = cursor
    while (canRead() && peek().isWordPart()) {
      skip()
    }
    return string.substring(
      start,
      cursor,
    )
  }

  private inline fun Char.isWordPart(): Boolean =
    when (this) {
      in 'a'..'z',
      in '0'..'9',
      '_', '.',
      -> true

      else
      -> false
    }

  private fun expect(
    expected: Char,
  ): Boolean =
    if (canRead() && peek() == expected) {
      skip()
      true
    } else {
      context += Diagnostic.ExpectedToken(
        expected,
        here(),
      )
      false
    }

  private inline fun <R> ranging(action: RangingContext.() -> R): R =
    RangingContext(here()).action()

  private fun skipWhitespaces() {
    while (canRead()) {
      when (peek()) {
        ' '  -> skip()
        '\n' -> skipNewline()
        '\r' -> {
          skipNewline()
          if (canRead() && peek() == '\n') {
            skip()
          }
        }
        else -> break
      }
    }
  }

  private inline fun skipNewline() {
    skip()
    ++line
    character = 0
  }

  private inline fun skip() {
    ++cursor
    ++character
  }

  private inline fun skip(size: Int) {
    cursor += size
    character += size
  }

  private inline fun peek(): Char =
    string[cursor]

  private inline fun canRead(): Boolean =
    cursor < length

  private inline fun canRead(offset: Int): Boolean =
    cursor + offset < length

  private inline fun here(): Position =
    Position(
      line,
      character,
    )

  private inner class RangingContext(
    private val start: Position,
  ) {
    fun until(): Range =
      start..here()
  }

  companion object : Phase<String, S.Root> {
    override fun invoke(
      context: Context,
      input: String,
    ): Surface.Root {
      return Parse(
        context,
        input,
      ).parseRoot()
    }
  }
}
