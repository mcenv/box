package mcx.phase

import kotlinx.coroutines.runBlocking
import mcx.lsp.diagnosticMessage
import mcx.phase.build.Build
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test

object BuildTests {
  private val std: Path = Path("src", "main", "resources", "std")

  @Test
  fun std() {
    val diagnosticsByPath = runBlocking {
      Build(std, null)()
    }.onEach { (path, diagnostics) ->
      diagnostics.forEach { println(diagnosticMessage(path, it)) }
    }
    assert(diagnosticsByPath.isEmpty())
  }

  @Test
  fun test() {
    val diagnosticsByPath = runBlocking {
      Build(Path("src", "test", "resources", "test"), std)()
    }.onEach { (path, diagnostics) ->
      diagnostics.forEach { println(diagnosticMessage(path, it)) }
    }
    assert(diagnosticsByPath.isEmpty())
  }
}
