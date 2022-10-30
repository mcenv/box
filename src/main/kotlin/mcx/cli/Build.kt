package mcx.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

@OptIn(ExperimentalCli::class)
object Build : Subcommand(
  "build",
  "Build",
) {
  override fun execute() {
    val serverProperties = Properties().apply {
      load(
        Paths
          .get("server.properties")
          .inputStream()
          .buffered()
      )
    }
    val levelName = serverProperties.getProperty("level-name")

    Build::class.java
      .getResourceAsStream("/mcx.zip")!!
      .buffered()
      .use { input ->
        Paths
          .get(
            levelName,
            "datapacks",
            "mcx.zip",
          )
          .outputStream()
          .buffered()
          .use { output ->
            input.transferTo(output)
          }
      }
  }
}
