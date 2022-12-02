package mcx.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking
import mcx.phase.Build
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.*

@OptIn(ExperimentalCli::class)
object Build : Subcommand(
  "build",
  "Build the current pack",
) {
  override fun execute(): Unit =
    runBlocking {
      Build(Paths.get(""))()
    }
}
