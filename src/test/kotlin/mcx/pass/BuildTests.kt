package mcx.pass

import kotlinx.coroutines.runBlocking
import mcx.lsp.diagnosticMessage
import mcx.pass.build.Build
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test

object BuildTests {
  val core: Path = Path("src", "main", "resources", "core")
  val test: Path = Path("src", "test", "resources", "test")

  @Test
  fun core() {
    val result = runBlocking {
      Build(core, core)()
    }
    result.diagnosticsByPath.forEach { (path, diagnostics) ->
      diagnostics.forEach {
        println(diagnosticMessage(path, it))
      }
    }
    assert(result.success)
  }

  @Test
  fun test() {
    val result = runBlocking {
      Build(test, core)()
    }
    result.diagnosticsByPath.forEach { (path, diagnostics) ->
      diagnostics.forEach {
        println(diagnosticMessage(path, it))
      }
    }
    assert(result.success)
  }
}
