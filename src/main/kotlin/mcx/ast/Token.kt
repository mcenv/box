package mcx.ast

sealed interface Token {
  enum class Literal(val size: Int) : Token {
    NEWLINE(0),
    LEFT_PARENTHESIS(1),
    RIGHT_PARENTHESIS(1),
    COMMA(1),
    COLON(1),
    COLON_COLON(2),
    COLON_EQUAL_SIGN(2),
    SEMICOLON(1),
    LEFT_SQUARE_BRACKET(1),
    RIGHT_SQUARE_BRACKET(1),
    LEFT_CURLY_BRACKET(1),
    RIGHT_CURLY_BRACKET(1),
  }

  @JvmInline
  value class Spaces(val size: Int) : Token

  @JvmInline
  value class Identifier(val text: String) : Token
}
