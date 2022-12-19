package mcx.cli

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import mcx.util.cache.BUNDLER_REPO_DIR
import mcx.util.cache.JAVA
import mcx.util.cache.getServerPath
import kotlin.io.path.notExists

@OptIn(ExperimentalCli::class)
object Launch : Subcommand(
  "launch",
  "Launch the Minecraft server",
) {
  private val version: String by argument(ArgType.String)

  override fun execute() {
    val serverPath = getServerPath(version)
    if (serverPath.notExists()) {
      error("not installed: '$version'")
    }

    ProcessBuilder(
      JAVA,
      BUNDLER_REPO_DIR,
      "-jar",
      serverPath
        .toAbsolutePath()
        .toString(),
    )
      .inheritIO()
      .start()
      .waitFor()
  }
}
