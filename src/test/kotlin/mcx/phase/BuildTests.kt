package mcx.phase

import kotlinx.coroutines.runBlocking
import mcx.lsp.diagnosticMessage
import mcx.phase.build.Build
import kotlin.io.path.Path
import kotlin.test.Test

object BuildTests {
  @Test
  fun std() {
    val diagnosticsByPath = runBlocking {
      Build(Path("src", "main", "resources", "std"), true)()
    }.onEach { (path, diagnostics) ->
      diagnostics.forEach { println(diagnosticMessage(path, it)) }
    }
    assert(diagnosticsByPath.isEmpty())
  }
}
