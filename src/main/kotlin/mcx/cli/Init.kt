package mcx.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import mcx.phase.Config
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream

@OptIn(
  ExperimentalCli::class,
  ExperimentalSerializationApi::class
)
object Init : Subcommand(
  "init",
  "Initialize a new pack",
) {
  private val json: Json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
  }

  override fun execute() {
    val name = Paths
      .get("")
      .toAbsolutePath()
      .last()
      .toString()

    Paths
      .get("pack.json")
      .outputStream()
      .buffered()
      .use {
        json.encodeToStream(
          Config(
            name = name,
            description = "",
          ),
          it,
        )
        it.write('\n'.code)
      }

    Paths
      .get("src")
      .createDirectories()
  }
}
