package mcx.phase.frontend

import mcx.ast.Annotation
import mcx.ast.ModuleLocation
import mcx.ast.Surface
import mcx.phase.Context
import mcx.util.Ranged
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
    module: ModuleLocation,
  ): Surface.Module {
    skipTrivia()
    val imports = if (text.startsWith("import", cursor)) {
      skip("import".length)
      skipTrivia()
      parseList(',', '{', '}') {
        parseRanged {
          val parts = readWord().split('.')
          ModuleLocation(parts.dropLast(1)) / parts.last()
        }
      }
    } else {
      emptyList()
    }

    val definitions = mutableListOf<S.Definition>().also {
      while (true) {
        skipTrivia()
        if (!canRead()) {
          break
        }
        val start = cursor
        it += parseDefinition()
        if (start == cursor) {
          break
        }
        expect(';')
      }
    }

    skipTrivia()
    if (canRead()) {
      diagnostics += Diagnostic.ExpectedEndOfFile(here())
    }

    return S.Module(module, imports, definitions)
  }

  private fun parseDefinition(): S.Definition =
    ranging {
      val annotations = parseAnnotations()
      if (canRead()) {
        when (readWord()) {
          "function"        -> {
            skipTrivia()
            val name = parseRanged { readWord() }
            skipTrivia()
            val typeParams =
              if (canRead() && peek() == '⟨') {
                parseList(',', '⟨', '⟩') { readWord() }
              } else {
                emptyList()
              }
            skipTrivia()
            val binder = parsePattern()
            expect("→")
            skipTrivia()
            val result = parseType()
            val body = if (annotations.find { it.value == Annotation.BUILTIN } != null && annotations.find { it.value == Annotation.STATIC } != null) {
              null
            } else {
              expect('{')
              skipTrivia()
              val body = parseTerm()
              expect('}')
              body
            }
            S.Definition.Function(annotations, name, typeParams, binder, result, body, until())
          }
          "type"            -> {
            skipTrivia()
            val name = parseRanged { readWord() }
            expect(':')
            skipTrivia()
            val kind = parseKind()
            expect('=')
            skipTrivia()
            val body = parseType()
            S.Definition.Type(annotations, name, kind, body, until())
          }
          else              -> null
        }
      } else {
        null
      } ?: run {
        val range = until()
        diagnostics += Diagnostic.ExpectedDefinition(range)
        S.Definition.Hole(range)
      }
    }

  private fun parseAnnotations(): List<Ranged<Annotation>> {
    val annotations = mutableListOf<Ranged<Annotation>>()
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

  private fun parseAnnotation(): Ranged<Annotation> =
    ranging {
      if (canRead()) {
        when (readWord()) {
          "tick"    -> Ranged(Annotation.TICK, until())
          "load"    -> Ranged(Annotation.LOAD, until())
          "no_drop" -> Ranged(Annotation.NO_DROP, until())
          "leaf"    -> Ranged(Annotation.LEAF, until())
          "builtin" -> Ranged(Annotation.BUILTIN, until())
          "export"  -> Ranged(Annotation.EXPORT, until())
          "inline"  -> Ranged(Annotation.INLINE, until())
          "static"  -> Ranged(Annotation.STATIC, until())
          else      -> null
        }
      } else {
        null
      } ?: run {
        val range = until()
        diagnostics += Diagnostic.ExpectedAnnotation(range)
        Ranged(Annotation.HOLE, range)
      }
    }

  private fun parseKind(): S.Kind =
    ranging {
      if (canRead()) {
        when (peek()) {
          '*'  -> {
            skip()
            S.Kind.Type(1, until())
          }
          '('  -> {
            val elements = parseList(',', '(', ')') { expect('*') }
            S.Kind.Type(elements.size, until())
          }
          else -> null
        }
      } else {
        null
      } ?: run {
        val range = until()
        diagnostics += Diagnostic.ExpectedKind(range)
        S.Kind.Hole(range)
      }
    }

  private fun parseType(): S.Type =
    ranging {
      (if (canRead()) {
        when (peek()) {
          '('  -> {
            skip()
            skipTrivia()
            if (canRead() && peek() == ')') {
              skip()
              S.Type.Tuple(emptyList(), until())
            } else {
              val first = parseType()
              skipTrivia()
              if (canRead()) {
                when (peek()) {
                  ')'  -> {
                    skip()
                    first
                  }
                  ','  -> {
                    val tail = parseList(',', ',', ')') { parseType() }
                    S.Type.Tuple(listOf(first) + tail, until())
                  }
                  else -> null
                }
              } else {
                null
              }
            }
          }
          '"'  -> S.Type.String(readQuotedString(), until())
          '['  -> {
            skip()
            skipTrivia()
            val element = parseType()
            skipTrivia()
            if (canRead() && peek() == ';') {
              skip()
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
              expect(':')
              skipTrivia()
              val element = parseType()
              key to element
            }.toMap()
            S.Type.Compound(elements, until())
          }
          '&'  -> {
            skip()
            skipTrivia()
            S.Type.Ref(parseType(), until())
          }
          '⟨'  -> {
            val elements = parseList(',', '⟨', '⟩') { parseType() }
            S.Type.Union(elements, until())
          }
          '`'  -> {
            skip()
            skipTrivia()
            S.Type.Code(parseType(), until())
          }
          '?'  -> {
            skip()
            S.Type.Meta(until())
          }
          else -> when (val word = readWord()) {
            ""       -> null
            "bool"   -> S.Type.Bool(null, until())
            "byte"   -> S.Type.Byte(null, until())
            "short"  -> S.Type.Short(null, until())
            "int"    -> S.Type.Int(null, until())
            "long"   -> S.Type.Long(null, until())
            "float"  -> S.Type.Float(null, until())
            "double" -> S.Type.Double(null, until())
            "string" -> S.Type.String(null, until())
            "false"  -> S.Type.Bool(false, until())
            "true"   -> S.Type.Bool(true, until())
            else     ->
              word
                .lastOrNull()
                ?.let { suffix ->
                  when (suffix) {
                    'b'  ->
                      word
                        .dropLast(1)
                        .toByteOrNull()
                        ?.let { S.Type.Byte(it, until()) }
                    's'  ->
                      word
                        .dropLast(1)
                        .toShortOrNull()
                        ?.let { S.Type.Short(it, until()) }
                    'l'  ->
                      word
                        .dropLast(1)
                        .toLongOrNull()
                        ?.let { S.Type.Long(it, until()) }
                    'f'  ->
                      word
                        .dropLast(1)
                        .toFloatOrNull()
                        ?.let { S.Type.Float(it, until()) }
                    'd'  ->
                      word
                        .dropLast(1)
                        .toDoubleOrNull()
                        ?.let { S.Type.Double(it, until()) }
                    else ->
                      word
                        .toIntOrNull()
                        ?.let { S.Type.Int(it, until()) }
                      ?: word
                        .toDoubleOrNull()
                        ?.let { S.Type.Double(it, until()) }
                  }
                } ?: S.Type.Var(word, until())
          }
        }
      } else {
        null
      } ?: run {
        val range = until()
        diagnostics += Diagnostic.ExpectedType(range)
        S.Type.Hole(range)
      }).let { left ->
        skipTrivia()
        if (canRead()) {
          when (peek()) {
            '→'  -> {
              skip()
              skipTrivia()
              val result = parseType()
              S.Type.Fun(left, result, until())
            }
            else -> null
          }
        } else {
          null
        } ?: left
      }
    }

  private fun parseTermAtom(): S.Term =
    ranging {
      if (canRead()) {
        when (peek()) {
          '('  -> {
            val elements = parseList(',', '(', ')') { parseTerm() }
            if (elements.size == 1) {
              elements.first()
            } else {
              S.Term.TupleOf(elements, until())
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
            S.Term.RefOf(parseTerm(), until())
          }
          '`'  -> {
            skip()
            skipTrivia()
            S.Term.CodeOf(parseTerm(), until())
          }
          '$'  -> {
            skip()
            skipTrivia()
            S.Term.Splice(parseTerm(), until())
          }
          else -> {
            val word = parseRanged { readWord() }
            when (word.value) {
              ""      -> null
              "false" -> S.Term.BoolOf(false, until())
              "true"  -> S.Term.BoolOf(true, until())
              "fun"   -> {
                skipTrivia()
                val binder = parsePattern()
                expect('→')
                expect('{')
                skipTrivia()
                val body = parseTerm()
                expect('}')
                S.Term.FunOf(binder, body, until())
              }
              "if"    -> {
                skipTrivia()
                val condition = parseTerm()
                expect('{')
                skipTrivia()
                val thenClause = parseTerm()
                expect('}')
                expect("else")
                expect('{')
                skipTrivia()
                val elseClause = parseTerm()
                expect('}')
                S.Term.If(condition, thenClause, elseClause, until())
              }
              "let"   -> {
                skipTrivia()
                val name = parsePattern()
                expect('=')
                skipTrivia()
                val init = parseTerm()
                expect(';')
                skipTrivia()
                val body = parseTerm()
                S.Term.Let(name, init, body, until())
              }
              else    ->
                word.value
                  .lastOrNull()
                  ?.let { suffix ->
                    when (suffix) {
                      'b'  ->
                        word.value
                          .dropLast(1)
                          .toByteOrNull()
                          ?.let { S.Term.ByteOf(it, until()) }
                      's'  ->
                        word.value
                          .dropLast(1)
                          .toShortOrNull()
                          ?.let { S.Term.ShortOf(it, until()) }
                      'l'  ->
                        word.value
                          .dropLast(1)
                          .toLongOrNull()
                          ?.let { S.Term.LongOf(it, until()) }
                      'f'  ->
                        word.value
                          .dropLast(1)
                          .toFloatOrNull()
                          ?.let { S.Term.FloatOf(it, until()) }
                      'd'  ->
                        word.value
                          .dropLast(1)
                          .toDoubleOrNull()
                          ?.let { S.Term.DoubleOf(it, until()) }
                      else ->
                        word.value
                          .toIntOrNull()
                          ?.let { S.Term.IntOf(it, until()) }
                        ?: word.value
                          .toDoubleOrNull()
                          ?.let { S.Term.DoubleOf(it, until()) }
                    }
                  } ?: run {
                  if (canRead()) {
                    when (peek()) {
                      '⟨'  -> {
                        val typeArgs = parseRanged { parseList(',', '⟨', '⟩') { parseType() } }
                        expect('(')
                        skipTrivia()
                        val arg = parseTerm()
                        expect(')')
                        S.Term.Run(word, typeArgs, arg, until())
                      }
                      '('  -> {
                        skip()
                        skipTrivia()
                        val arg = parseTerm()
                        expect(')')
                        val range = until()
                        S.Term.Run(word, Ranged(emptyList(), range), arg, range)
                      }
                      else -> null
                    }
                  } else {
                    null
                  } ?: S.Term.Var(word.value, until())
                }
            }
          }
        }
      } else {
        null
      } ?: run {
        val range = until()
        diagnostics += Diagnostic.ExpectedTerm(range)
        S.Term.Hole(range)
      }
    }

  private fun parseTerm(): S.Term =
    ranging {
      var term = parseTermAtom()
      skipTrivia()
      while (canRead()) {
        val char = peek()
        when {
          char.isWordPart() -> {
            val name = parseRanged { readWord() }
            skipTrivia()
            term = when (name.value) {
              "is" -> {
                val right = parsePattern()
                S.Term.Is(term, right, until())
              }
              "of" -> {
                val right = parseTerm()
                S.Term.Apply(term, right, until())
              }
              else -> {
                val right = parseTermAtom()
                val range = until()
                S.Term.Run(name, Ranged(emptyList(), range), S.Term.TupleOf(listOf(term, right), range), range)
              }
            }
          }
          else              -> break
        }
        skipTrivia()
      }
      term
    }

  private fun parsePattern(): S.Pattern =
    ranging {
      val annotations = parseAnnotations()
      (if (canRead()) {
        when (peek()) {
          '(' -> {
            skip()
            skipTrivia()
            if (canRead() && peek() == ')') {
              skip()
              S.Pattern.TupleOf(emptyList(), annotations, until())
            } else {
              val first = parsePattern()
              skipTrivia()
              if (canRead()) {
                when (peek()) {
                  ')'  -> {
                    skip()
                    first
                  }
                  ','  -> {
                    val tail = parseList(',', ',', ')') {
                      parsePattern()
                    }
                    S.Pattern.TupleOf(listOf(first) + tail, annotations, until())
                  }
                  else -> null
                }
              } else {
                null
              }
            }
          }
          '['  -> {
            val elements = parseList(',', '[', ']') { parsePattern() }
            S.Pattern.ListOf(elements, annotations, until())
          }
          '{'  -> {
            val elements = parseList(',', '{', '}') {
              val key = parseRanged { readString() }
              expect(':')
              skipTrivia()
              val element = parsePattern()
              key to element
            }
            S.Pattern.CompoundOf(elements, annotations, until())
          }
          else -> when (val word = readWord()) {
            "_"  -> S.Pattern.Drop(annotations, until())
            else ->
              word
                .toIntOrNull()
                ?.let { S.Pattern.IntOf(it, annotations, until()) }
              ?: S.Pattern.Var(word, annotations, until())
          }
        }
      } else {
        null
      } ?: run {
        val range = until()
        diagnostics += Diagnostic.ExpectedPattern(range)
        S.Pattern.Hole(annotations, range)
      }).let { left ->
        skipTrivia()
        if (canRead()) {
          when (peek()) {
            '.'  -> {
              expect("..")
              (left as? S.Pattern.IntOf)?.let { min ->
                skipTrivia()
                readWord()
                  .toIntOrNull()
                  ?.let { max ->
                    S.Pattern.IntRangeOf(min.value, max, annotations, until())
                  }
              }
            }
            ':'  -> {
              skip()
              skipTrivia()
              val type = parseType()
              S.Pattern.Anno(left, type, annotations, until())
            }
            else -> null
          }
        } else {
          null
        } ?: left
      }
    }

  private fun <R> parseRanged(
    parse: () -> R,
  ): Ranged<R> =
    ranging {
      Ranged(parse(), until())
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

    while (true) {
      skipTrivia()
      if (!canRead()) {
        break
      }
      when (peek()) {
        postfix -> break
        else    -> skip()
      }
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
      ' ', '\n', '\r', ':', ';', ',', '(', ')', '[', ']', '{', '}', '⟨', '⟩' -> false
      else                                                                   -> true
    }

  private fun expect(
    expected: Char,
  ): Boolean {
    val position = here()
    skipTrivia()
    return if (canRead() && peek() == expected) {
      skip()
      true
    } else {
      diagnostics += Diagnostic.ExpectedToken(expected.toString(), position)
      false
    }
  }

  private fun expect(
    expected: String,
  ): Boolean {
    skipTrivia()
    return if (canRead(expected.length) && text.startsWith(expected, cursor)) {
      skip(expected.length)
      true
    } else {
      diagnostics += Diagnostic.ExpectedToken(expected, here())
      false
    }
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
      context: Context,
      module: ModuleLocation,
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
