package mcx.pass

import mcx.ast.ModuleLocation
import mcx.ast.Surface.Definition
import mcx.ast.Surface.Module
import mcx.ast.Surface.Term
import mcx.lsp.Ranged
import mcx.pass.frontend.Parse
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import kotlin.test.Test
import kotlin.test.assertEquals

object ParseTests {
  @Test
  fun empty() {
    val context = Context(Config("test", "", true))
    val name = ModuleLocation("empty")
    val result = Parse(context, name, "")
    assertEquals(
      Parse.Result(
        module = Module(
          name = name,
          imports = emptyList(),
          definitions = emptyList(),
        ),
        diagnostics = emptyList(),
      ),
      result,
    )
  }

  @Test
  fun simple() {
    val context = Context(Config("test", "", true))
    val name = ModuleLocation("simple")
    val result = Parse(
      context, name,
      """def a : int := 0;
      """.trimIndent()
    )
    assertEquals(
      Parse.Result(
        module = Module(
          name = name,
          imports = emptyList(),
          definitions = listOf(
            Definition.Def(
              modifiers = emptyList(),
              name = Ranged("a", Range(Position(0, 4), Position(0, 5))),
              type = Term.Int(Range(Position(0, 8), Position(0, 11))),
              body = Term.IntOf(0, Range(Position(0, 15), Position(0, 16))),
              range = Range(Position(0, 0), Position(0, 16)),
            )
          ),
        ),
        diagnostics = emptyList(),
      ),
      result,
    )
  }
}
