package mcx.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking
import mcx.phase.Build
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.*
import kotlin.system.exitProcess

@OptIn(ExperimentalCli::class)
object Build : Subcommand(
  "build",
  "Build the current pack",
) {
  override fun execute(): Unit =
    runBlocking {
      val build = Build(Paths.get(""))
      val diagnosticsByPath = build()
      if (diagnosticsByPath.isNotEmpty()) {
        diagnosticsByPath.forEach { (path, diagnostics) ->
          diagnostics.forEach {
            println("[${it.severity.name.lowercase()}] ${path.invariantSeparatorsPathString}:${it.range.start.line + 1}:${it.range.start.character + 1} ${it.message}")
          }
        }
        exitProcess(1)
      }
    }
}
