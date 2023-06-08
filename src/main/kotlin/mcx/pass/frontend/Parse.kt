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
      val doc = parseDoc()
      val annotations = parseAnnotations()
      val (modifiers, keyword) = parseModifiers()
      when (keyword) {
        "def" -> {
          skipTrivia()
          val name = parseRanged { readWord() }

          if (modifiers.find { it.value == Modifier.TEST } != null) {
            val type = S.Term.Bool(until())
            val body = run {
              expect(":=")
              skipTrivia()
              parseTerm()
            }
            return S.Definition.Def(doc, annotations, modifiers, name, type, body, until())
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
                S.Definition.Def(doc, annotations, modifiers, name, type, body, until())
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
                    S.Term.Var("_", binderOrType.range) to binderOrType
                  }
                }
                val type = run {
                  expect(':')
                  skipTrivia()
                  val result = parseTerm()
                  S.Term.Func(false, params, result, until())
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
                  S.Term.FuncOf(false, params.map { it.first }, body, until())
                }
                S.Definition.Def(doc, annotations, modifiers, name, type, body, until())
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
        S.Definition.Hole(range)
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
          '"'  -> S.Term.StrOf(readQuotedString(), until())
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
                  ';'  -> {
                    when (first) {
                      is S.Term.I8  -> {
                        val elements = parseList(',', ';', ']') { parseTerm() }
                        S.Term.I8ArrayOf(elements, until())
                      }
                      is S.Term.I32 -> {
                        val elements = parseList(',', ';', ']') { parseTerm() }
                        S.Term.I32ArrayOf(elements, until())
                      }
                      is S.Term.I64 -> {
                        val elements = parseList(',', ';', ']') { parseTerm() }
                        S.Term.I64ArrayOf(elements, until())
                      }
                      else          -> null // TODO: improve error message
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
            S.Term.StructOf(elements, until())
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
            S.Term.FuncOf(open, params, result, until())
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
              ""             -> null
              "tag"          -> S.Term.Tag(until())
              "end"          -> S.Term.TagOf(NbtType.END, until())
              "byte"         -> S.Term.TagOf(NbtType.BYTE, until())
              "short"        -> S.Term.TagOf(NbtType.SHORT, until())
              "int"          -> S.Term.TagOf(NbtType.INT, until())
              "long"         -> S.Term.TagOf(NbtType.LONG, until())
              "float"        -> S.Term.TagOf(NbtType.FLOAT, until())
              "double"       -> S.Term.TagOf(NbtType.DOUBLE, until())
              "string"       -> S.Term.TagOf(NbtType.STRING, until())
              "byte_array"   -> S.Term.TagOf(NbtType.BYTE_ARRAY, until())
              "int_array"    -> S.Term.TagOf(NbtType.INT_ARRAY, until())
              "long_array"   -> S.Term.TagOf(NbtType.LONG_ARRAY, until())
              "list"         -> S.Term.TagOf(NbtType.LIST, until())
              "compound"     -> S.Term.TagOf(NbtType.COMPOUND, until())
              "type"         -> {
                skipTrivia()
                val tag = parseTerm0()
                S.Term.Type(tag, until())
              }
              "bool"         -> S.Term.Bool(until())
              "false"        -> S.Term.BoolOf(false, until())
              "true"         -> S.Term.BoolOf(true, until())
              "if"           -> {
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
              "i8"           -> S.Term.I8(until())
              "i16"          -> S.Term.I16(until())
              "i32"          -> S.Term.I32(until())
              "i64"          -> S.Term.I64(until())
              "f32"          -> S.Term.F32(until())
              "f64"          -> S.Term.F64(until())
              "str"          -> S.Term.Str(until())
              "i8_array"     -> S.Term.I8Array(until())
              "i32_array"    -> S.Term.I32Array(until())
              "i64_array"    -> S.Term.I64Array(until())
              "vec"          -> {
                skipTrivia()
                val element = parseTerm0()
                S.Term.Vec(element, until())
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
                S.Term.Struct(elements, until())
              }
              "point"        -> {
                skipTrivia()
                val element = parseTerm0()
                S.Term.Point(element, until())
              }
              "union"        -> {
                skipTrivia()
                val elements = parseList(',', '{', '}') { parseTerm() }
                S.Term.Union(elements, until())
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
                    S.Term.Var("_", binderOrType.range) to binderOrType
                  }
                }
                expect("->")
                skipTrivia()
                val result = parseTerm()
                S.Term.Func(open, params, result, until())
              }
              "code"         -> {
                skipTrivia()
                val element = parseTerm0()
                S.Term.Code(element, until())
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
                S.Term.Let(name, init, body, until())
              }
              else           -> {
                val name = word.value
                when {
                  name.endsWith("i8")  -> name.dropLast("i8".length).toByteOrNull()?.let { S.Term.I8Of(it, until()) }
                  name.endsWith("i16") -> name.dropLast("i16".length).toShortOrNull()?.let { S.Term.I16Of(it, until()) }
                  name.endsWith("i32") -> name.dropLast("i32".length).toIntOrNull()?.let { S.Term.I32Of(it, until()) }
                  name.endsWith("i64") -> name.dropLast("i64".length).toLongOrNull()?.let { S.Term.I64Of(it, until()) }
                  name.endsWith("f32") -> name.dropLast("f32".length).toFloatOrNull()?.takeUnless { it.isNaN() || it compareTo -0.0f == 0 }?.let { S.Term.F32Of(it, until()) }
                  name.endsWith("f64") -> name.dropLast("f64".length).toDoubleOrNull()?.takeUnless { it.isNaN() || it compareTo -0.0 == 0 }?.let { S.Term.F64Of(it, until()) }
                  else                 -> name.toIntOrNull()?.let { S.Term.I32Of(it, until()) }
                                          ?: name.toDoubleOrNull()?.let { S.Term.F64Of(it, until()) }
                                          ?: S.Term.Var(name, until())
                }
              }
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
    val module: Surface.Module,
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
