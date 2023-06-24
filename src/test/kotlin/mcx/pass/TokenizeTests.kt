package mcx.pass

import mcx.ast.Token
import mcx.pass.frontend.Tokenize
import kotlin.test.Test
import kotlin.test.assertEquals

object TokenizeTests {
  @Test
  fun test() {
    val context = Context(Config(name = "test", description = "", debug = Config.Debug(true)))
    assertEquals(
      listOf(
        Token.Literal.NEWLINE,
        Token.Literal.NEWLINE,
        Token.Literal.LEFT_PARENTHESIS,
        Token.Literal.RIGHT_PARENTHESIS,
        Token.Literal.COMMA,
        Token.Literal.COLON,
        Token.Literal.SEMICOLON,
        Token.Literal.LEFT_SQUARE_BRACKET,
        Token.Literal.RIGHT_SQUARE_BRACKET,
        Token.Literal.LEFT_CURLY_BRACKET,
        Token.Literal.RIGHT_CURLY_BRACKET,
        Token.Spaces(1),
        Token.Literal.NEWLINE,
        Token.Spaces(2),
        Token.Identifier("a"),
        Token.Literal.COLON_COLON,
        Token.Literal.COLON_EQUAL_SIGN,
        Token.Identifier("bc"),
      ),
      Tokenize(
        context,
        "\n\r(),:;[]{} \r\n  a:::=bc",
      ),
    )
  }
}
