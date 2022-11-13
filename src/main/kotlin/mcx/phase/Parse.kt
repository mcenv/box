package mcx.phase

import mcx.ast.Location
import mcx.ast.Registry
import mcx.ast.Surface
import mcx.util.rangeTo
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import mcx.ast.Surface as S

@Suppress("NOTHING_TO_INLINE")
class Parse private constructor(
  private val text: String,
) {
  private val diagnostics: MutableList<Diagnostic> = mutableListOf()
  private val length: Int = text.length
  private var cursor: Int = 0
  private var line: Int = 0
  private var character: Int = 0

  private fun parseModule(
    module: Location,
  ): Surface.Module {
    skipTrivia()
    val imports = if (text.startsWith("import", cursor)) {
      skip("import".length)
      skipTrivia()
      parseList(',', '{', '}') {
        parseRanged { parseLocation() }
      }
    } else {
      emptyList()
    }

    val resources = mutableListOf<S.Resource>()
    while (true) {
      skipTrivia()
      if (!canRead()) {
        break
      }
      val start = cursor
      resources += parseResource()
      if (start == cursor) {
        break
      }
    }

    skipTrivia()
    if (canRead()) {
      diagnostics += Diagnostic.ExpectedEndOfFile(here())
    }

    return S.Module(module, imports, resources)
  }

  private fun parseResource(): S.Resource =
    ranging {
      val annotations = mutableListOf<S.Annotation>()
      while (true) {
        skipTrivia()
        if (!canRead() || peek() != '@') {
          break
        }
        skip()
        val start = cursor
        annotations += parseAnnotation()
        if (start == cursor) {
          break
        }
      }

      if (canRead()) {
        when (readWord()) {
          "predicates"     -> parseJsonResource(annotations, Registry.PREDICATES)
          "recipes"        -> parseJsonResource(annotations, Registry.RECIPES)
          "loot_tables"    -> parseJsonResource(annotations, Registry.LOOT_TABLES)
          "item_modifiers" -> parseJsonResource(annotations, Registry.ITEM_MODIFIERS)
          "advancements"   -> parseJsonResource(annotations, Registry.ADVANCEMENTS)
          "dimension_type" -> parseJsonResource(annotations, Registry.DIMENSION_TYPE)
          "worldgen"       -> {
            expect('/')
            when (readWord()) {
              "biome" -> parseJsonResource(annotations, Registry.WORLDGEN_BIOME)
              else    -> null
            }
          }
          "dimension"      -> parseJsonResource(annotations, Registry.DIMENSION)
          "functions"      -> {
            skipTrivia()
            val name = readWord()
            skipTrivia()
            val params = parseList(',', '(', ')') {
              skipTrivia()
              val key = readWord()
              skipTrivia()
              expect(':')
              skipTrivia()
              val value = parseType()
              key to value
            }
            skipTrivia()
            expect(':')
            skipTrivia()
            val result = parseType()
            skipTrivia()
            expect('=')
            skipTrivia()
            val body = parseTerm()
            S.Resource.Functions(annotations, name, params, result, body, until())
          }
          else             -> null
        }
      } else {
        null
      }
      ?: run {
        val range = until()
        diagnostics += Diagnostic.ExpectedResource(range)
        S.Resource.Hole(range)
      }
    }

  private fun parseJsonResource(
    annotations: List<S.Annotation>,
    registry: Registry,
  ): S.Resource.JsonResource =
    ranging {
      skipTrivia()
      val name = readWord()
      skipTrivia()
      expect('=')
      skipTrivia()
      val body = parseTerm()
      S.Resource.JsonResource(annotations, registry, name, body, until())
    }

  private fun parseAnnotation(): S.Annotation =
    ranging {
      if (canRead()) {
        when (readWord()) {
          "tick" -> S.Annotation.Tick(until())
          "load" -> S.Annotation.Load(until())
          else   -> null
        }
      } else {
        null
      }
      ?: run {
        val range = until()
        diagnostics += Diagnostic.ExpectedAnnotation(range)
        S.Annotation.Hole(range)
      }
    }

  private fun parseType(): S.Type =
    ranging {
      if (canRead()) {
        when (peek()) {
          '('  -> {
            skip()
            skipTrivia()
            val type = parseType()
            skipTrivia()
            expect(')')
            type
          }
          '['  -> {
            skip()
            skipTrivia()
            val element = parseType()
            skipTrivia()
            expect(']')
            S.Type.List(element, until())
          }
          '{'  -> {
            val elements = parseList(',', '{', '}') {
              val key = readString()
              skipTrivia()
              expect(':')
              skipTrivia()
              val element = parseType()
              key to element
            }.toMap()
            S.Type.Compound(elements, until())
          }
          '&'  -> {
            skip()
            S.Type.Box(parseType(), until())
          }
          else -> when (readWord()) {
            "end"    -> S.Type.End(until())
            "bool"   -> S.Type.Bool(until())
            "byte"   -> S.Type.Byte(until())
            "short"  -> S.Type.Short(until())
            "int"    -> S.Type.Int(until())
            "long"   -> S.Type.Long(until())
            "float"  -> S.Type.Float(until())
            "double" -> S.Type.Double(until())
            "string" -> S.Type.String(until())
            else     -> null
          }
        }
      } else {
        null
      }
      ?: run {
        val range = until()
        diagnostics += Diagnostic.ExpectedType(range)
        S.Type.Hole(range)
      }
    }

  private fun parseTerm(): S.Term =
    ranging {
      skipTrivia()
      if (canRead()) {
        when (peek()) {
          '('  -> {
            skip()
            skipTrivia()
            val term = parseTerm()
            skipTrivia()
            expect(')')
            term
          }
          '"'  -> S.Term.StringOf(readQuotedString(), until())
          '['  -> {
            val values = parseList(',', '[', ']') {
              parseTerm()
            }
            S.Term.ListOf(values, until())
          }
          '{'  -> {
            val values = parseList(',', '{', '}') {
              val key = parseRanged { readString() }
              skipTrivia()
              expect(':')
              skipTrivia()
              val value = parseTerm()
              key to value
            }
            S.Term.CompoundOf(values, until())
          }
          '&'  -> {
            skip()
            skipTrivia()
            S.Term.BoxOf(parseTerm(), until())
          }
          '/'  -> {
            skip()
            val value = readQuotedString()
            S.Term.Command(value, until())
          }
          else -> {
            val location = parseLocation()
            when (location.parts.size) {
              1 -> when (val word = location.parts.first()) {
                "false" -> S.Term.BoolOf(false, until())
                "true"  -> S.Term.BoolOf(true, until())
                "if"    -> {
                  skipTrivia()
                  val condition = parseTerm()
                  skipTrivia()
                  expect("then")
                  skipTrivia()
                  val thenClause = parseTerm()
                  skipTrivia()
                  expect("else")
                  skipTrivia()
                  val elseClause = parseTerm()
                  S.Term.If(condition, thenClause, elseClause, until())
                }
                "let"   -> {
                  skipTrivia()
                  val name = parseRanged { readWord() }
                  skipTrivia()
                  expect('=')
                  skipTrivia()
                  val init = parseTerm()
                  skipTrivia()
                  expect(';')
                  val body = parseTerm()
                  S.Term.Let(name, init, body, until())
                }
                else    -> if (canRead() && peek() == '(') {
                  val args = parseList(',', '(', ')') {
                    parseTerm()
                  }
                  S.Term.Run(location, args, until())
                } else {
                  word
                    .lastOrNull()
                    ?.let { suffix ->
                      when (suffix) {
                        'b'  ->
                          word
                            .dropLast(1)
                            .toByteOrNull()
                            ?.let { S.Term.ByteOf(it, until()) }
                        's'  ->
                          word
                            .dropLast(1)
                            .toShortOrNull()
                            ?.let { S.Term.ShortOf(it, until()) }
                        'l'  ->
                          word
                            .dropLast(1)
                            .toLongOrNull()
                            ?.let { S.Term.LongOf(it, until()) }
                        'f'  ->
                          word
                            .dropLast(1)
                            .toFloatOrNull()
                            ?.let { S.Term.FloatOf(it, until()) }
                        'd'  ->
                          word
                            .dropLast(1)
                            .toDoubleOrNull()
                            ?.let { S.Term.DoubleOf(it, until()) }
                        else ->
                          word
                            .toIntOrNull()
                            ?.let { S.Term.IntOf(it, until()) }
                          ?: word
                            .toDoubleOrNull()
                            ?.let { S.Term.DoubleOf(it, until()) }
                      }
                    }
                  ?: S.Term.Var(word, until())
                }
              }
              else -> {
                val args = parseList(',', '(', ')') {
                  parseTerm()
                }
                S.Term.Run(location, args, until())
              }
            }
          }
        }
      } else {
        null
      }
      ?: run {
        val range = until()
        diagnostics += Diagnostic.ExpectedTerm(range)
        S.Term.Hole(range)
      }
    }

  private fun parseLocation(): Location {
    val parts = mutableListOf(readWord())
    while (canRead() && peek() == '/') {
      skip()
      parts += readWord()
    }
    return Location(parts)
  }

  private fun <R> parseRanged(
    parse: () -> R,
  ): S.Ranged<R> =
    ranging {
      S.Ranged(parse(), until())
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
      skipTrivia()
      if (!canRead() || peek() == postfix) {
        break
      }
      val start = cursor
      elements += parse()
      if (start == cursor) {
        break
      }
      skipTrivia()
      if (!canRead()) {
        break
      }
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
    return when (peek()) {
      '"'  -> readQuotedString()
      else -> readWord()
    }
  }

  private fun readQuotedString(): String {
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
          else      -> diagnostics += Diagnostic.InvalidEscape(char, Position(line, character - 1)..Position(line, character + 1))
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
    return text.substring(start, cursor)
  }

  private inline fun Char.isWordPart(): Boolean =
    when (this) {
      ' ', '\n', '\r', '/', ':', ';', ',', '(', ')', '[', ']', '{', '}' -> false
      else                                                              -> true
    }

  private fun expect(
    expected: Char,
  ): Boolean =
    if (canRead() && peek() == expected) {
      skip()
      true
    } else {
      diagnostics += Diagnostic.ExpectedToken(expected.toString(), here())
      false
    }

  private fun expect(
    expected: String,
  ): Boolean =
    if (canRead(expected.length) && text.startsWith(expected, cursor)) {
      skip(expected.length)
      true
    } else {
      diagnostics += Diagnostic.ExpectedToken(expected, here())
      false
    }

  private inline fun <R> ranging(action: RangingContext.() -> R): R =
    RangingContext(here()).action()

  private fun skipTrivia() {
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
        '#'  -> {
          skip()
          while (
            canRead() && when (peek()) {
              '\n', '\r' -> false; else -> true
            }
          ) {
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

  data class Result(
    val module: Surface.Module,
    val diagnostics: List<Diagnostic>,
  )

  companion object {
    operator fun invoke(
      config: Config,
      module: Location,
      text: String,
    ): Result =
      Parse(text).run {
        Result(
          parseModule(module),
          diagnostics,
        )
      }
  }
}
