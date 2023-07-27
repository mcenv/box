package box.pass

import kotlinx.coroutines.runBlocking
import box.util.getOrCreateDependenciesPath
import box.util.installDependencies
import box.lsp.diagnosticMessage
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

object BuildTests {
  val packs: Path = Path("packs")
  val core: Path = packs / "core"
  val test: Path = packs / "test"
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
