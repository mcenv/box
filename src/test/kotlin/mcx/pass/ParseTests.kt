package mcx.pass

import mcx.ast.Surface.Definition
import mcx.ast.Surface.Module
import mcx.ast.Surface.Term
import mcx.ast.common.ModuleLocation
import mcx.lsp.Ranged
import mcx.pass.frontend.parse.Parse
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import kotlin.test.Test
import kotlin.test.assertEquals

object ParseTests {
  @Test
  fun empty() {
    val name = ModuleLocation("test", "empty")
    val result = Parse(name, "")
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
    val name = ModuleLocation("test", "simple")
    val result = Parse(
      name,
      """def a : i32 := 0;
      """.trimIndent()
    )
    assertEquals(
      Parse.Result(
        module = Module(
          name = name,
          imports = emptyList(),
          definitions = listOf(
            Definition.Def(
              doc = "",
              annotations = emptyList(),
              modifiers = emptyList(),
              name = Ranged("a", Range(Position(0, 4), Position(0, 5))),
              type = Term.I32(Range(Position(0, 8), Position(0, 11))),
              body = Term.I32Of(0, Range(Position(0, 15), Position(0, 16))),
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
