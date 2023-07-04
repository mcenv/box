package mcx.pass.frontend

import mcx.ast.Token

class Tokenize private constructor(
  private val text: String,
) {
  private val length: Int = text.length
  private var cursor: Int = 0

  private fun tokenize(): List<Token> {
    val tokens = mutableListOf<Token>()

    while (cursor < length) {
      tokens += when (text[cursor++]) {
        '\n' -> Token.Literal.NEWLINE
        '\r' -> {
          when (text[cursor]) {
            '\n' -> {
              ++cursor
              Token.Literal.NEWLINE
            }
            else -> Token.Literal.NEWLINE
          }
        }
        '('  -> Token.Literal.LEFT_PARENTHESIS
        ')'  -> Token.Literal.RIGHT_PARENTHESIS
        ','  -> Token.Literal.COMMA
        ':'  -> {
          when (text[cursor]) {
            ':'  -> {
              ++cursor
              Token.Literal.COLON_COLON
            }
            '='  -> {
              ++cursor
              Token.Literal.COLON_EQUAL_SIGN
            }
            else -> Token.Literal.COLON
          }
        }
        ';'  -> Token.Literal.SEMICOLON
        '['  -> Token.Literal.LEFT_SQUARE_BRACKET
        ']'  -> Token.Literal.RIGHT_SQUARE_BRACKET
        '{'  -> Token.Literal.LEFT_CURLY_BRACKET
        '}'  -> Token.Literal.RIGHT_CURLY_BRACKET
        ' '  -> {
          val start = cursor - 1
          while (cursor < length && text[cursor] == ' ') {
            ++cursor
          }
          Token.Spaces(cursor - start)
        }
        else -> {
          val start = cursor - 1
          while (cursor < length && when (text[cursor]) {
              '\n', '\r', '(', ')', ',', ':', ';', '[', ']', '{', '}', ' ' -> false
              else                                                         -> true
            }
          ) {
            ++cursor
          }
          Token.Identifier(text.substring(start, cursor))
        }
      }
    }

    return tokens
  }

  companion object {
    operator fun invoke(
      text: String,
    ): List<Token> {
      return Tokenize(text).tokenize()
    }
  }
}
