package mcx.pass

import kotlinx.coroutines.runBlocking
import mcx.lsp.diagnosticMessage
import mcx.pass.build.Build
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test

object BuildTests {
  val std: Path = Path("src", "main", "resources", "std")
  val test: Path = Path("src", "test", "resources", "test")

  @Test
  fun std() {
    val diagnosticsByPath = runBlocking {
      Build(std, std)()
    }.onEach { (path, diagnostics) ->
      diagnostics.forEach { println(diagnosticMessage(path, it)) }
    }
    assert(diagnosticsByPath.isEmpty())
  }

  @Test
  fun test() {
    val diagnosticsByPath = runBlocking {
      Build(test, std)()
    }.onEach { (path, diagnostics) ->
      diagnostics.forEach { println(diagnosticMessage(path, it)) }
    }
    assert(diagnosticsByPath.isEmpty())
  }
}
