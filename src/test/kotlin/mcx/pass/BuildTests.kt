package mcx.pass

import kotlinx.coroutines.runBlocking
import mcx.cache.getOrCreateDependenciesPath
import mcx.cache.installDependencies
import mcx.lsp.diagnosticMessage
import mcx.pass.build.Build
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

object BuildTests {
  val core: Path = Path("src", "main", "resources", "core")
  val test: Path = Path("src", "test", "resources", "test")
  val std: Path = getOrCreateDependenciesPath() / "mcenv" / "std-main"

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
      installDependencies(test)
      Build(test, core)()
    }
    result.diagnosticsByPath.forEach { (path, diagnostics) ->
      diagnostics.forEach {
        println(diagnosticMessage(path, it))
      }
    }
    assert(result.success)
  }

  @Test
  fun std() {
    val result = runBlocking {
      installDependencies(test)
      Build(std, core)()
    }
    result.diagnosticsByPath.forEach { (path, diagnostics) ->
      diagnostics.forEach {
        println(diagnosticMessage(path, it))
      }
    }
    assert(result.success)
  }
}
