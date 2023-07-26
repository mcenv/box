package box.pass

import box.ast.Surface.Definition
import box.ast.Surface.Module
import box.ast.Surface.Term
import box.ast.common.ModuleLocation
import box.lsp.Ranged
import box.pass.frontend.parse.Parse
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
              body = Term.ConstOf(0, Range(Position(0, 15), Position(0, 16))),
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
