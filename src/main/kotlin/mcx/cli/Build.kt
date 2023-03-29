package mcx.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking
import mcx.lsp.diagnosticMessage
import mcx.pass.build.Build
import kotlin.io.path.Path
import kotlin.system.exitProcess

@OptIn(ExperimentalCli::class)
object Build : Subcommand(
  "build",
  "Build the current pack",
) {
  override fun execute() {
    runBlocking {
      Build(Path(""))().forEach { (path, diagnostics) ->
        diagnostics.forEach { println(diagnosticMessage(path, it)) }
        exitProcess(1)
      }
    }
  }
}
