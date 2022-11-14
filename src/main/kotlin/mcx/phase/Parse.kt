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
        parseRanged { readWord().toLocation() }
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
      val annotations = parseAnnotations()
      if (canRead()) {
        when (readWord()) {
          "/predicates"     -> parseJsonResource(annotations, Registry.PREDICATES)
          "/recipes"        -> parseJsonResource(annotations, Registry.RECIPES)
          "/loot_tables"    -> parseJsonResource(annotations, Registry.LOOT_TABLES)
          "/item_modifiers" -> parseJsonResource(annotations, Registry.ITEM_MODIFIERS)
          "/advancements"   -> parseJsonResource(annotations, Registry.ADVANCEMENTS)
          "/dimension_type" -> parseJsonResource(annotations, Registry.DIMENSION_TYPE)
          "/worldgen/biome" -> parseJsonResource(annotations, Registry.WORLDGEN_BIOME)
          "/dimension"      -> parseJsonResource(annotations, Registry.DIMENSION)
          "function"        -> {
            skipTrivia()
            val name = readWord()
            skipTrivia()
            val binder = parsePattern()
            skipTrivia()
            expect(':')
            skipTrivia()
            val param = parseType()
            skipTrivia()
            expect("->")
            skipTrivia()
            val result = parseType()
            skipTrivia()
            expect('=')
            skipTrivia()
            val body = parseTerm()
            S.Resource.Function(annotations, name, binder, param, result, body, until())
          }
          else              -> null
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

  private fun parseAnnotations(): List<S.Annotation> {
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
    return annotations
  }

  private fun parseAnnotation(): S.Annotation =
    ranging {
      if (canRead()) {
        when (readWord()) {
          "tick"    -> S.Annotation.Tick(until())
          "load"    -> S.Annotation.Load(until())
          "no_drop" -> S.Annotation.NoDrop(until())
          else      -> null
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
            val elements = parseList(',', '(', ')') {
              parseType()
            }
            S.Type.Tuple(elements, until())
          }
          '['  -> {
            skip()
            skipTrivia()
            val element = parseType()
            skipTrivia()
            if (canRead() && peek() == ';') {
              skip()
              skipTrivia()
              expect(']')
              when (element) {
                is S.Type.Byte -> S.Type.ByteArray(until())
                is S.Type.Int  -> S.Type.IntArray(until())
                is S.Type.Long -> S.Type.LongArray(until())
                else           -> null
              }
            } else {
              expect(']')
              S.Type.List(element, until())
            }
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
            if (canRead() && peek() == ')') {
              skip()
              S.Term.TupleOf(emptyList(), until())
            } else {
              val first = parseTerm()
              skipTrivia()
              if (canRead()) {
                when (peek()) {
                  ')'  -> {
                    skip()
                    S.Term.TupleOf(listOf(first), until())
                  }
                  ','  -> {
                    val tail = parseList(',', ',', ')') {
                      parseTerm()
                    }
                    S.Term.TupleOf(listOf(first) + tail, until())
                  }
                  else -> {
                    val second = parseTerm()
                    skipTrivia()
                    if (canRead() && peek() == ')') {
                      skip()
                      (first as? S.Term.Var)?.let { name ->
                        val range = until()
                        S.Term.Run(name.name.toLocation(), second, range)
                      }
                    } else {
                      (second as? S.Term.Var)?.let { name ->
                        val third = parseTerm()
                        skipTrivia()
                        expect(')')
                        val range = until()
                        S.Term.Run(name.name.toLocation(), S.Term.TupleOf(listOf(first, third), range), range)
                      }
                    }
                  }
                }
              } else {
                null
              }
            }
          }
          '"'  -> S.Term.StringOf(readQuotedString(), until())
          '['  -> {
            skip()
            skipTrivia()
            if (canRead() && peek() == ']') {
              skip()
              S.Term.ListOf(emptyList(), until())
            } else {
              val first = parseTerm()
              skipTrivia()
              if (canRead()) {
                when (peek()) {
                  ']'  -> {
                    skip()
                    S.Term.ListOf(listOf(first), until())
                  }
                  ';'  -> (first as? S.Term.Var)?.let { header ->
                    when (header.name) {
                      "byte" -> {
                        val elements = parseList(',', ';', ']') {
                          parseTerm()
                        }
                        S.Term.ByteArrayOf(elements, until())
                      }
                      "int"  -> {
                        val elements = parseList(',', ';', ']') {
                          parseTerm()
                        }
                        S.Term.IntArrayOf(elements, until())
                      }
                      "long" -> {
                        val elements = parseList(',', ';', ']') {
                          parseTerm()
                        }
                        S.Term.LongArrayOf(elements, until())
                      }
                      else   -> null // TODO: improve error message
                    }
                  }
                  ','  -> {
                    val tail = parseList(',', ',', ']') {
                      parseTerm()
                    }
                    S.Term.ListOf(listOf(first) + tail, until())
                  }
                  else -> null
                }
              } else {
                null
              }
            }
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
          else -> {
            when (val word = readWord()) {
              ""        -> null
              "false"   -> S.Term.BoolOf(false, until())
              "true"    -> S.Term.BoolOf(true, until())
              "if"      -> {
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
              "let"     -> {
                skipTrivia()
                val name = parsePattern()
                skipTrivia()
                expect('=')
                skipTrivia()
                val init = parseTerm()
                skipTrivia()
                expect(';')
                val body = parseTerm()
                S.Term.Let(name, init, body, until())
              }
              "command" -> {
                skipTrivia()
                val value = readQuotedString()
                S.Term.Command(value, until())
              }
              else      ->
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
                  } ?: S.Term.Var(word, until())
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

  private fun parsePattern(): S.Pattern =
    ranging {
      val annotations = parseAnnotations()
      if (canRead()) {
        when (peek()) {
          '('  -> {
            val elements = parseList(',', '(', ')') {
              parsePattern()
            }
            S.Pattern.TupleOf(elements, annotations, until())
          }
          else -> when (val word = readWord()) {
            "_"  -> S.Pattern.Discard(annotations, until())
            else -> S.Pattern.Var(word, annotations, until())
          }
        }
      } else {
        null
      }
      ?: run {
        val range = until()
        diagnostics += Diagnostic.ExpectedPattern(range)
        S.Pattern.Hole(annotations, range)
      }
    }

  private fun String.toLocation(): Location =
    Location(this.split('.'))

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
      ' ', '\n', '\r', ':', ';', ',', '(', ')', '[', ']', '{', '}' -> false
      else                                                         -> true
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
