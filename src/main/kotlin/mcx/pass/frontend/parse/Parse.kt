package mcx.pass.frontend.parse

import mcx.ast.Annotation
import mcx.ast.Modifier
import mcx.ast.ModuleLocation
import mcx.ast.Repr
import mcx.lsp.Ranged
import mcx.lsp.diagnostic
import mcx.lsp.rangeTo
import mcx.pass.Context
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import mcx.ast.Parsed as P

// TODO: refactor
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
  ): P.Module {
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

    val definitions = mutableListOf<P.Definition>().also {
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

    return P.Module(module, imports, definitions)
  }

  private fun parseDefinition(): P.Definition {
    return ranging {
      val doc = parseDoc()
      val annotations = parseAnnotations()
      val (modifiers, keyword) = parseModifiers()
      when (keyword) {
        "def" -> {
          skipTrivia()
          val name = parseRanged { readWord() }

          if (modifiers.find { it.value == Modifier.TEST } != null) {
            val type = P.Term.Bool(until())
            val body = run {
              expect(":=")
              skipTrivia()
              parseTerm()
            }
            return P.Definition.Def(doc, annotations, modifiers, name, type, body, until())
          }

          skipTrivia()
          if (canRead()) {
            when (peek()) {
              ':'  -> {
                skip()
                skipTrivia()
                val type = parseTerm()
                val body = if (
                  modifiers.find { it.value == Modifier.BUILTIN } != null &&
                  modifiers.find { it.value == Modifier.CONST } != null
                ) {
                  null
                } else {
                  expect(":=")
                  skipTrivia()
                  parseTerm()
                }
                P.Definition.Def(doc, annotations, modifiers, name, type, body, until())
              }
              '('  -> {
                val params = parseList(',', '(', ')') {
                  val binderOrType = parseTerm()
                  skipTrivia()
                  if (canRead() && peek() == ':') {
                    skip()
                    skipTrivia()
                    val type = parseTerm()
                    binderOrType to type
                  } else {
                    P.Term.Var("_", binderOrType.range) to binderOrType
                  }
                }
                val type = run {
                  expect(':')
                  skipTrivia()
                  val result = parseTerm()
                  P.Term.Func(false, params, result, until())
                }
                val body = if (
                  modifiers.find { it.value == Modifier.BUILTIN } != null &&
                  modifiers.find { it.value == Modifier.CONST } != null
                ) {
                  null
                } else {
                  expect(":=")
                  skipTrivia()
                  val body = parseTerm()
                  P.Term.FuncOf(false, params.map { it.first }, body, until())
                }
                P.Definition.Def(doc, annotations, modifiers, name, type, body, until())
              }
              else -> null
            }
          } else {
            null
          }
        }
        else  -> null
      } ?: run {
        val range = until()
        diagnostics += expectedDefinition(range)
        P.Definition.Hole(range)
      }
    }
  }

  private fun parseDoc(): String {
    val lines = mutableListOf<String>()
    while (text.startsWith("#|", cursor)) {
      skip(2)
      val start = cursor
      while (
        canRead() && when (peek()) {
          '\n', '\r' -> false; else -> true
        }
      ) {
        skip()
      }
      lines += text.substring(start, cursor)
      skipTrivia()
    }
    return lines.joinToString("\n")
  }

  private fun parseAnnotations(): List<Ranged<Annotation>> {
    val annotations = mutableListOf<Ranged<Annotation>>()
    while (canRead() && peek() == '@') {
      skip()
      annotations += ranging {
        when (readWord()) {
          "deprecated" -> Ranged(Annotation.Deprecated, until())
          "unstable"   -> Ranged(Annotation.Unstable, until())
          "delicate"   -> Ranged(Annotation.Delicate, until())
          else         -> Ranged(Annotation.Hole, until())
        }
      }
      skipTrivia()
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
          "test"    -> Ranged(Modifier.TEST, until())
          "error"   -> Ranged(Modifier.ERROR, until())
          else      -> return modifiers to word
        }
      }
      if (start == cursor) {
        break
      }
    }
    return modifiers to null
  }

  private fun parseTerm0(): P.Term {
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
          '%'  -> {
            skip()
            when (readWord()) {
              "end"        -> P.Term.TagOf(Repr.End, until())
              "byte"       -> P.Term.TagOf(Repr.Byte, until())
              "short"      -> P.Term.TagOf(Repr.Short, until())
              "int"        -> P.Term.TagOf(Repr.Int, until())
              "long"       -> P.Term.TagOf(Repr.Long, until())
              "float"      -> P.Term.TagOf(Repr.Float, until())
              "double"     -> P.Term.TagOf(Repr.Double, until())
              "string"     -> P.Term.TagOf(Repr.String, until())
              "byte_array" -> P.Term.TagOf(Repr.ByteArray, until())
              "int_array"  -> P.Term.TagOf(Repr.IntArray, until())
              "long_array" -> P.Term.TagOf(Repr.LongArray, until())
              "list"       -> P.Term.TagOf(Repr.List, until())
              "compound"   -> P.Term.TagOf(Repr.Compound, until())
              else         -> null
            }
          }
          '"'  -> parseInterpolatedString()
          '['  -> {
            skip()
            skipTrivia()
            if (canRead() && peek() == ']') {
              skip()
              P.Term.ListOf(emptyList(), until())
            } else {
              val first = parseTerm()
              skipTrivia()
              if (canRead()) {
                when (peek()) {
                  ']'  -> {
                    skip()
                    P.Term.ListOf(listOf(first), until())
                  }
                  ';'  -> {
                    when (first) {
                      is P.Term.I8  -> {
                        val elements = parseList(',', ';', ']') { parseTerm() }
                        P.Term.I8ArrayOf(elements, until())
                      }
                      is P.Term.I32 -> {
                        val elements = parseList(',', ';', ']') { parseTerm() }
                        P.Term.I32ArrayOf(elements, until())
                      }
                      is P.Term.I64 -> {
                        val elements = parseList(',', ';', ']') { parseTerm() }
                        P.Term.I64ArrayOf(elements, until())
                      }
                      else          -> null // TODO: improve error message
                    }
                  }
                  ','  -> {
                    val tail = parseList(',', ',', ']') { parseTerm() }
                    P.Term.ListOf(listOf(first) + tail, until())
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
            P.Term.StructOf(elements, until())
          }
          '&'  -> {
            skip()
            skipTrivia()
            val element = parseTerm0()
            P.Term.RefOf(element, until())
          }
          '\\' -> {
            skip()
            val open = if (canRead() && peek() == '\\') {
              skip()
              true
            } else {
              false
            }
            skipTrivia()
            val params = parseList(',', '(', ')') { parseTerm() }
            expect("->")
            skipTrivia()
            val result = parseTerm()
            P.Term.FuncOf(open, params, result, until())
          }
          '`'  -> {
            skip()
            skipTrivia()
            val element = parseTerm0()
            P.Term.CodeOf(element, until())
          }
          '$'  -> {
            skip()
            skipTrivia()
            val element = parseTerm0()
            P.Term.Splice(element, until())
          }
          '/'  -> {
            skip()
            skipTrivia()
            val element = parseTerm0()
            P.Term.Command(element, until())
          }
          else -> {
            val word = parseRanged { readLocation() }
            when (word.value) {
              ""             -> null
              "tag"          -> P.Term.Tag(until())
              "type"         -> {
                skipTrivia()
                val tag = parseTerm0()
                P.Term.Type(tag, until())
              }
              "bool"         -> P.Term.Bool(until())
              "false"        -> P.Term.BoolOf(false, until())
              "true"         -> P.Term.BoolOf(true, until())
              "if"           -> {
                skipTrivia()
                val condition = parseTerm1()
                expect("then")
                skipTrivia()
                val thenBranch = parseTerm1()
                expect("else")
                skipTrivia()
                val elseBranch = parseTerm1()
                P.Term.If(condition, thenBranch, elseBranch, until())
              }
              "i8"           -> P.Term.I8(until())
              "i16"          -> P.Term.I16(until())
              "i32"          -> P.Term.I32(until())
              "i64"          -> P.Term.I64(until())
              "f32"          -> P.Term.F32(until())
              "f64"          -> P.Term.F64(until())
              "str"          -> P.Term.Str(until())
              "i8_array"     -> P.Term.I8Array(until())
              "i32_array"    -> P.Term.I32Array(until())
              "i64_array"    -> P.Term.I64Array(until())
              "vec"          -> {
                skipTrivia()
                val element = parseTerm0()
                P.Term.Vec(element, until())
              }
              "struct"       -> {
                skipTrivia()
                val elements = parseList(',', '{', '}') {
                  val key = parseRanged { readString() }
                  expect(':')
                  skipTrivia()
                  val value = parseTerm()
                  key to value
                }
                P.Term.Struct(elements, until())
              }
              "ref"          -> {
                skipTrivia()
                val element = parseTerm0()
                P.Term.Ref(element, until())
              }
              "point"        -> {
                skipTrivia()
                val element = parseTerm0()
                P.Term.Point(element, until())
              }
              "union"        -> {
                skipTrivia()
                val elements = parseList(',', '{', '}') { parseTerm() }
                P.Term.Union(elements, until())
              }
              "proc", "func" -> {
                skipTrivia()
                val open = word.value == "func"
                val params = parseList(',', '(', ')') {
                  val binderOrType = parseTerm()
                  skipTrivia()
                  if (canRead() && peek() == ':') {
                    skip()
                    skipTrivia()
                    val type = parseTerm()
                    binderOrType to type
                  } else {
                    P.Term.Var("_", binderOrType.range) to binderOrType
                  }
                }
                expect("->")
                skipTrivia()
                val result = parseTerm()
                P.Term.Func(open, params, result, until())
              }
              "code"         -> {
                skipTrivia()
                val element = parseTerm0()
                P.Term.Code(element, until())
              }
              "let"          -> {
                skipTrivia()
                val name = parseTerm()
                expect(":=")
                skipTrivia()
                val init = parseTerm()
                expect(';')
                skipTrivia()
                val body = parseTerm()
                P.Term.Let(name, init, body, until())
              }
              "match"        -> {
                skipTrivia()
                val scrutinee = parseTerm()
                val branches = parseList(',', '[', ']') {
                  val pattern = parseTerm()
                  expect(":->")
                  skipTrivia()
                  val body = parseTerm()
                  pattern to body
                }
                P.Term.Match(scrutinee, branches, until())
              }
              else           -> {
                val name = word.value
                when {
                  name.endsWith("i8")  -> name.dropLast("i8".length).toByteOrNull()?.let { P.Term.NumOf(false, it, until()) }
                  name.endsWith("i16") -> name.dropLast("i16".length).toShortOrNull()?.let { P.Term.NumOf(false, it, until()) }
                  name.endsWith("i32") -> name.dropLast("i32".length).toIntOrNull()?.let { P.Term.NumOf(false, it, until()) }
                  name.endsWith("i64") -> name.dropLast("i64".length).toLongOrNull()?.let { P.Term.NumOf(false, it, until()) }
                  name.endsWith("f32") -> name.dropLast("f32".length).toFloatOrNull()?.takeUnless { it.isNaN() || it compareTo -0.0f == 0 }?.let { P.Term.NumOf(false, it, until()) }
                  name.endsWith("f64") -> name.dropLast("f64".length).toDoubleOrNull()?.takeUnless { it.isNaN() || it compareTo -0.0 == 0 }?.let { P.Term.NumOf(false, it, until()) }
                  else                 -> name.toLongOrNull()?.let { P.Term.NumOf(true, it, until()) }
                                          ?: name.toDoubleOrNull()?.let { P.Term.NumOf(true, it, until()) }
                } ?: P.Term.Var(name, until())
              }
            }
          }
        }
      } else {
        null
      } ?: run {
        val range = until()
        diagnostics += expectedTerm(range)
        P.Term.Hole(range)
      }
    }
  }

  private fun parseTerm1(): P.Term {
    return ranging {
      var term = parseTerm0()
      skipTrivia()
      while (canRead() && peek() == '(') {
        val args = parseList(',', '(', ')') { parseTerm() }
        term = P.Term.Apply(term, args, until())
      }
      term
    }
  }

  private fun parseTerm(): P.Term {
    return ranging {
      var term = parseTerm1()
      skipTrivia()
      while (canRead()) {
        term = if (peek().isWordPart()) {
          val name = parseRanged { readLocation() }
          skipTrivia()
          when (name.value) {
            "as" -> {
              skipTrivia()
              val type = parseTerm()
              P.Term.As(term, type, until())
            }
            else -> {
              val func = P.Term.Var(name.value, name.range)
              skipTrivia()
              val arg = parseTerm1()
              P.Term.Apply(func, listOf(term, arg), until())
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
          'u'       -> {
            skip()
            if (canRead(4)) {
              text.substring(cursor, cursor + 4).toIntOrNull(16)?.takeUnless { it.toChar() in Char.MIN_HIGH_SURROGATE..Char.MAX_LOW_SURROGATE }?.let {
                skip(3)
                builder.append(it.toChar())
              }
            } else {
              null
            } ?: run {
              diagnostics += invalidEscape(char, Position(line, character - 1)..Position(line, character + 1))
            }
          }
          else      -> {
            diagnostics += invalidEscape(char, Position(line, character - 1)..Position(line, character + 1))
          }
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

  private fun parseInterpolatedString(): P.Term {
    var start = here()
    val parts = mutableListOf<P.Term>()
    var builder = StringBuilder()

    expect('"')

    var escaped = false
    while (canRead()) {
      if (escaped) {
        when (val char = peek()) {
          '"', '\\', '#' -> {
            builder.append(char)
          }
          'u'            -> {
            skip()
            if (canRead(4)) {
              text.substring(cursor, cursor + 4).toIntOrNull(16)?.takeUnless { it.toChar() in Char.MIN_HIGH_SURROGATE..Char.MAX_LOW_SURROGATE }?.let {
                skip(3)
                builder.append(it.toChar())
              }
            } else {
              null
            } ?: run {
              diagnostics += invalidEscape(char, Position(line, character - 1)..Position(line, character + 1))
            }
          }
          else           -> {
            diagnostics += invalidEscape(char, Position(line, character - 1)..Position(line, character + 1))
          }
        }
        escaped = false
      } else {
        when (val char = peek()) {
          '\\' -> escaped = true
          '"'  -> break
          '#'  -> {
            if (builder.isNotEmpty()) {
              parts += P.Term.StrOf(builder.toString(), start..here())
            }

            skip()
            expect('{')
            skipTrivia()
            parts += parseTerm()
            expect('}')

            start = here()
            builder = StringBuilder()
            continue
          }
          else -> builder.append(char)
        }
      }
      skip()
    }

    expect('"')

    if (builder.isNotEmpty()) {
      parts += P.Term.StrOf(builder.toString(), start..here())
    }
    return parts.reduceOrNull { acc, term ->
      // TODO: keep string interpolation until resolve phase
      P.Term.Apply(P.Term.Var("++", acc.range.end..term.range.start), listOf(acc, term), acc.range.start..term.range.end)
    } ?: P.Term.StrOf("", start..here())
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
          // ignore doc
          if (canRead(1) && peek(1) == '|') {
            break
          }

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

  private inline fun peek(offset: Int): Char {
    return text[cursor + offset]
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
    fun until(): Range {
      return start..here()
    }
  }

  data class Result(
    val module: P.Module,
    val diagnostics: List<Diagnostic>,
  )

  companion object {
    operator fun invoke(
      context: Context,
      module: ModuleLocation,
      text: String,
    ): Result {
      return Parse(text).run {
        Result(
          parseModule(module),
          diagnostics,
        )
      }
    }
  }
}
