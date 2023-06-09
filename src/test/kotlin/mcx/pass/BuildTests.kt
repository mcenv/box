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
  val pos: Path = Path("src", "test", "resources", "pos")
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
  fun pos() {
    val result = runBlocking {
      installDependencies(pos)
      Build(pos, core)()
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
      installDependencies(pos)
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
