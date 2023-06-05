package mcx.cli

import com.mojang.brigadier.CommandDispatcher
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import mcx.pass.Config
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream

@OptIn(ExperimentalSerializationApi::class)
object InitCommands {
  val json: Json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
  }

  fun register(dispatcher: CommandDispatcher<Unit>) {
    dispatcher.register(
      literal("init")
        .executes {
          val name = Path("").toAbsolutePath().last().toString()
          Path("pack.json").outputStream().buffered().use {
            json.encodeToStream(
              Config(
                name = name,
                description = "",
              ),
              it,
            )
            it.write('\n'.code)
          }
          Paths.get("src").createDirectories()
          0
        }
    )
  }
}
