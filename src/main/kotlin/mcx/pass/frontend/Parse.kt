package mcx.pass.frontend

import mcx.ast.Annotation
import mcx.ast.Modifier
import mcx.ast.ModuleLocation
import mcx.ast.Surface
import mcx.data.NbtType
import mcx.lsp.Ranged
import mcx.lsp.diagnostic
import mcx.lsp.rangeTo
import mcx.pass.Context
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
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
          val parts = readLocation().split("::")
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
      diagnostics += expectedEndOfFile(here())
    }

    return S.Module(module, imports, definitions)
  }

  private fun parseDefinition(): S.Definition {
    return ranging {
      val annotations = parseAnnotations()
      val (modifiers, keyword) = parseModifiers()
      when (keyword) {
        "def" -> {
          skipTrivia()
          val name = parseRanged { readWord() }
          expect(':')
          skipTrivia()
          val type = parseTerm()
          val body = if (modifiers.find { it.value == Modifier.BUILTIN } != null) {
            null
          } else {
            expect(":=")
            skipTrivia()
            parseTerm()
          }
          S.Definition.Def(annotations, modifiers, name, type, body, until())
        }
        else  -> null
      } ?: run {
        val range = until()
        diagnostics += expectedDefinition(range)
        S.Definition.Hole(range)
      }
    }
  }

  private fun parseAnnotations(): List<Ranged<Annotation>> {
    val annotations = mutableListOf<Ranged<Annotation>>()
    while (canRead() && peek() == '@') {
      skip()
      annotations += ranging {
        when (readWord()) {
          "deprecated" -> Ranged(Annotation.Deprecated, until())
          else         -> Ranged(Annotation.Hole, until())
        }
      }
    }
    return annotations
  }

  private fun parseModifiers(): Pair<List<Ranged<Modifier>>, String?> {
    val modifiers = mutableListOf<Ranged<Modifier>>()
    while (true) {
      skipTrivia()
      if (!canRead()) {
        break
      }
      val start = cursor
      modifiers += ranging {
        when (val word = readWord()) {
          "builtin" -> Ranged(Modifier.BUILTIN, until())
          "export"  -> Ranged(Modifier.EXPORT, until())
          "rec"     -> Ranged(Modifier.REC, until())
          "const"   -> Ranged(Modifier.CONST, until())
          else      -> return modifiers to word
        }
      }
      if (start == cursor) {
        break
      }
    }
    return modifiers to null
  }

  private fun parseTerm0(): S.Term {
    return ranging {
      if (canRead()) {
        when (peek()) {
          '('  -> {
            skip()
            skipTrivia()
            val term = parseTerm()
            expect(')')
            term
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
                        val elements = parseList(',', ';', ']') { parseTerm() }
                        S.Term.ByteArrayOf(elements, until())
                      }
                      "int"  -> {
                        val elements = parseList(',', ';', ']') { parseTerm() }
                        S.Term.IntArrayOf(elements, until())
                      }
                      "long" -> {
                        val elements = parseList(',', ';', ']') { parseTerm() }
                        S.Term.LongArrayOf(elements, until())
                      }
                      else   -> null // TODO: improve error message
                    }
                  }
                  ','  -> {
                    val tail = parseList(',', ',', ']') { parseTerm() }
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
            val elements = parseList(',', '{', '}') {
              val key = parseRanged { readString() }
              expect(':')
              skipTrivia()
              val element = parseTerm()
              key to element
            }
            S.Term.CompoundOf(elements, until())
          }
          '\\' -> {
            skip()
            skipTrivia()
            val params = parseList(',', '(', ')') { parseTerm() }
            expect("->")
            skipTrivia()
            val result = parseTerm()
            S.Term.FuncOf(params, result, until())
          }
          '`'  -> {
            skip()
            skipTrivia()
            val element = parseTerm0()
            S.Term.CodeOf(element, until())
          }
          '$'  -> {
            skip()
            skipTrivia()
            val element = parseTerm0()
            S.Term.Splice(element, until())
          }
          '/'  -> {
            skip()
            skipTrivia()
            val element = parseTerm0()
            S.Term.Command(element, until())
          }
          else -> {
            val word = parseRanged { readLocation() }
            when (word.value) {
              ""               -> null
              "tag"            -> S.Term.Tag(until())
              "end_tag"        -> S.Term.TagOf(NbtType.END, until())
              "byte_tag"       -> S.Term.TagOf(NbtType.BYTE, until())
              "short_tag"      -> S.Term.TagOf(NbtType.SHORT, until())
              "int_tag"        -> S.Term.TagOf(NbtType.INT, until())
              "long_tag"       -> S.Term.TagOf(NbtType.LONG, until())
              "float_tag"      -> S.Term.TagOf(NbtType.FLOAT, until())
              "double_tag"     -> S.Term.TagOf(NbtType.DOUBLE, until())
              "string_tag"     -> S.Term.TagOf(NbtType.STRING, until())
              "byte_array_tag" -> S.Term.TagOf(NbtType.BYTE_ARRAY, until())
              "int_array_tag"  -> S.Term.TagOf(NbtType.INT_ARRAY, until())
              "long_array_tag" -> S.Term.TagOf(NbtType.LONG_ARRAY, until())
              "list_tag"       -> S.Term.TagOf(NbtType.LIST, until())
              "compound_tag"   -> S.Term.TagOf(NbtType.COMPOUND, until())
              "type"           -> {
                skipTrivia()
                val tag = parseTerm0()
                S.Term.Type(tag, until())
              }
              "bool"           -> S.Term.Bool(until())
              "false"          -> S.Term.BoolOf(false, until())
              "true"           -> S.Term.BoolOf(true, until())
              "if"             -> {
                skipTrivia()
                val condition = parseTerm1()
                expect("then")
                skipTrivia()
                val thenBranch = parseTerm1()
                expect("else")
                skipTrivia()
                val elseBranch = parseTerm1()
                S.Term.If(condition, thenBranch, elseBranch, until())
              }
              "byte"           -> S.Term.Byte(until())
              "short"          -> S.Term.Short(until())
              "int"            -> S.Term.Int(until())
              "long"           -> S.Term.Long(until())
              "float"          -> S.Term.Float(until())
              "double"         -> S.Term.Double(until())
              "string"         -> S.Term.String(until())
              "byte_array"     -> S.Term.ByteArray(until())
              "int_array"      -> S.Term.IntArray(until())
              "long_array"     -> S.Term.LongArray(until())
              "list"           -> {
                skipTrivia()
                val element = parseTerm0()
                S.Term.List(element, until())
              }
              "compound"       -> {
                skipTrivia()
                val elements = parseList(',', '{', '}') {
                  val key = parseRanged { readString() }
                  expect(':')
                  skipTrivia()
                  val value = parseTerm()
                  key to value
                }
                S.Term.Compound(elements, until())
              }
              "point"          -> {
                skipTrivia()
                val element = parseTerm0()
                S.Term.Point(element, until())
              }
              "union"          -> {
                skipTrivia()
                val elements = parseList(',', '{', '}') { parseTerm() }
                S.Term.Union(elements, until())
              }
              "func"           -> {
                skipTrivia()
                val params = parseList(',', '(', ')') {
                  skipTrivia()
                  val binderOrType = parseTerm()
                  skipTrivia()
                  if (canRead() && peek() == ':') {
                    skip()
                    skipTrivia()
                    val type = parseTerm()
                    binderOrType to type
                  } else {
                    S.Term.Var("_", binderOrType.range) to binderOrType
                  }
                }
                expect("->")
                skipTrivia()
                val result = parseTerm()
                S.Term.Func(params, result, until())
              }
              "code"           -> {
                skipTrivia()
                val element = parseTerm0()
                S.Term.Code(element, until())
              }
              "let"            -> {
                skipTrivia()
                val name = parseTerm()
                expect(":=")
                skipTrivia()
                val init = parseTerm()
                expect(';')
                skipTrivia()
                val body = parseTerm()
                S.Term.Let(name, init, body, until())
              }
              else             ->
                word.value.lastOrNull()?.let { suffix ->
                  when (suffix) {
                    'b'  -> word.value.dropLast(1).toByteOrNull()?.let { S.Term.ByteOf(it, until()) }
                    's'  -> word.value.dropLast(1).toShortOrNull()?.let { S.Term.ShortOf(it, until()) }
                    'l'  -> word.value.dropLast(1).toLongOrNull()?.let { S.Term.LongOf(it, until()) }
                    'f'  -> word.value.dropLast(1).toFloatOrNull()?.let { S.Term.FloatOf(it, until()) }
                    'd'  -> word.value.dropLast(1).toDoubleOrNull()?.let { S.Term.DoubleOf(it, until()) }
                    else -> word.value.toIntOrNull()?.let { S.Term.IntOf(it, until()) }
                            ?: word.value.toDoubleOrNull()?.let { S.Term.DoubleOf(it, until()) }
                  }
                } ?: S.Term.Var(word.value, until())
            }
          }
        }
      } else {
        null
      } ?: run {
        val range = until()
        diagnostics += expectedTerm(range)
        S.Term.Hole(range)
      }
    }
  }

  private fun parseTerm1(): S.Term {
    return ranging {
      var term = parseTerm0()
      skipTrivia()
      while (canRead() && peek() == '(') {
        val args = parseList(',', '(', ')') { parseTerm() }
        term = S.Term.Apply(term, args, until())
      }
      term
    }
  }

  private fun parseTerm(): S.Term {
    return ranging {
      var term = parseTerm1()
      skipTrivia()
      while (canRead()) {
        term = if (peek().isWordPart()) {
          val name = parseRanged { readWord() }
          skipTrivia()
          when (name.value) {
            "is" -> {
              skipTrivia()
              val scrutineer = parseTerm()
              S.Term.Is(term, scrutineer, until())
            }
            "as" -> {
              skipTrivia()
              val type = parseTerm()
              S.Term.As(term, type, until())
            }
            else -> {
              val func = S.Term.Var(name.value, name.range)
              skipTrivia()
              val arg = parseTerm1()
              S.Term.Apply(func, listOf(term, arg), until())
            }
          }
        } else {
          return term
        }
        skipTrivia()
      }
      term
    }
  }

  private fun <R> parseRanged(parse: () -> R): Ranged<R> {
    return ranging { Ranged(parse(), until()) }
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
          else      -> diagnostics += invalidEscape(char, Position(line, character - 1)..Position(line, character + 1))
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

  private fun readLocation(): String {
    val start = cursor
    while (canRead() && peek().isLocationPart()) {
      skip()
    }
    return text.substring(start, cursor)
  }

  private fun readWord(): String {
    val start = cursor
    while (canRead() && peek().isWordPart()) {
      skip()
    }
    return text.substring(start, cursor)
  }

  private inline fun Char.isLocationPart(): Boolean {
    return when (this) {
      ' ', '\n', '\r', ';', ',', '(', ')', '[', ']', '{', '}' -> false
      else                                                    -> true
    }
  }

  private inline fun Char.isWordPart(): Boolean {
    return when (this) {
      ':'  -> false
      else -> isLocationPart()
    }
  }

  private fun expect(expected: Char): Boolean {
    val position = here()
    skipTrivia()
    return if (canRead() && peek() == expected) {
      skip()
      true
    } else {
      diagnostics += expectedToken(expected.toString(), position)
      false
    }
  }

  private fun expect(expected: String): Boolean {
    skipTrivia()
    return if (canRead(expected.length) && text.startsWith(expected, cursor)) {
      skip(expected.length)
      true
    } else {
      diagnostics += expectedToken(expected, here())
      false
    }
  }

  private inline fun <R> ranging(action: RangingContext.() -> R): R {
    return RangingContext(here()).action()
  }

  private fun skipTrivia() {
    while (canRead()) {
      when (peek()) {
        ' '  -> skip()
        '\n' -> {
          ++cursor
          ++line
          character = 0
        }
        '\r' -> {
          ++cursor
          ++line
          character = 0
          if (canRead() && peek() == '\n') {
            ++cursor
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

  private inline fun skip() {
    ++cursor
    ++character
  }

  private inline fun skip(size: Int) {
    cursor += size
    character += size
  }

  private inline fun peek(): Char {
    return text[cursor]
  }

  private inline fun canRead(): Boolean {
    return cursor < length
  }

  private inline fun canRead(offset: Int): Boolean {
    return cursor + offset < length
  }

  private inline fun here(): Position {
    return Position(
      line,
      character,
    )
  }

  private fun invalidEscape(
    escape: Char,
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "invalid escape: \\$escape",
      DiagnosticSeverity.Error,
    )
  }

  private fun expectedToken(
    token: String,
    position: Position,
  ): Diagnostic {
    return diagnostic(
      position..Position(position.line, position.character + 1),
      "expected: '$token'",
      DiagnosticSeverity.Error,
    )
  }

  private fun expectedEndOfFile(
    position: Position,
  ): Diagnostic {
    return diagnostic(
      position..position,
      "expected: end of file",
      DiagnosticSeverity.Error,
    )
  }

  private fun expectedDefinition(
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "expected: definition",
      DiagnosticSeverity.Error,
    )
  }

  private fun expectedTerm(
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "expected: term",
      DiagnosticSeverity.Error,
    )
  }

  private fun expectedAnnotation(
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "expected: annotation",
      DiagnosticSeverity.Error,
    )
  }

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
