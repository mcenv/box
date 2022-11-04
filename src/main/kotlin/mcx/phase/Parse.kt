package mcx.phase

import mcx.ast.Location
import mcx.ast.Surface
import mcx.util.rangeTo
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import mcx.ast.Surface as S

@Suppress("NOTHING_TO_INLINE")
class Parse private constructor(
  private val context: Context,
  private val text: String,
) {
  private val length: Int = text.length
  private var cursor: Int = 0
  private var line: Int = 0
  private var character: Int = 0

  private fun parseRoot(
    module: Location,
  ): S.Root {
    skipWhitespaces()
    val imports = if (
      text.startsWith(
        "import",
        cursor,
      )
    ) {
      skip("import".length)
      skipWhitespaces()
      parseList(
        ',',
        '{',
        '}',
      ) {
        parseRanged {
          val parts = mutableListOf(readWord())
          while (canRead() && peek() == '/') {
            skip()
            parts += readWord()
          }
          Location(parts)
        }
      }
    } else {
      emptyList()
    }

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

    return S.Root(
      module,
      imports,
      resources,
    )
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
            "int"    -> S.Type0.Int(until())
            "string" -> S.Type0.String(until())
            "ref"    -> {
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
            else     -> null
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
          '('                   -> {
            skip()
            skipWhitespaces()
            val term = parseTerm0()
            skipWhitespaces()
            expect(')')
            term
          }
          '-', '+', in '0'..'9' -> when (val value = readWord().toIntOrNull()) {
            null -> TODO()
            else -> S.Term0.IntOf(
              value,
              until(),
            )
          }
          '"'                   ->
            S.Term0.StringOf(
              readString(),
              until(),
            )
          '&'                   -> {
            skip()
            skipWhitespaces()
            S.Term0.RefOf(
              parseTerm0(),
              until(),
            )
          }
          else                  -> when (val word = readWord()) {
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
            else  -> if (canRead() && peek() == '[') {
              val args = parseList(
                ',',
                '[',
                ']',
              ) {
                parseTerm0()
              }
              S.Term0.Run(
                word,
                args,
                until(),
              )
            } else {
              S.Term0.Var(
                word,
                until(),
              )
            }
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

  private fun <R> parseRanged(
    parse: () -> R,
  ): S.Ranged<R> =
    ranging {
      S.Ranged(
        parse(),
        until(),
      )
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

  private fun readString(): String {
    if (!canRead()) {
      return ""
    }
    expect('"')

    val builder = StringBuilder()
    var escaped = false
    while (canRead()) {
      if (escaped) {
        when (val char = peek()) {
          '"', '\\' -> {
            builder.append(char)
          }
          else      -> context += Diagnostic.InvalidEscape(
            char,
            Position(
              line,
              character - 1,
            )..Position(
              line,
              character + 1,
            ),
          )
        }
        escaped = false
      } else {
        when (val char = peek()) {
          '\\' -> escaped = true
          '"'  -> break
          else -> builder.append(char)
        }
      }
      skip()
    }

    expect('"')
    return builder.toString()
  }

  private fun readWord(): String {
    val start = cursor
    while (canRead() && peek().isWordPart()) {
      skip()
    }
    return text.substring(
      start,
      cursor,
    )
  }

  private inline fun Char.isWordPart(): Boolean =
    when (this) {
      in 'a'..'z',
      in '0'..'9',
      '_',
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
    text[cursor]

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

  companion object {
    operator fun invoke(
      context: Context,
      module: Location,
      text: String,
    ): Surface.Root {
      return Parse(
        context,
        text,
      ).parseRoot(module)
    }
  }
}
