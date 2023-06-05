package mcx.cli

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType.greedyString
import com.mojang.brigadier.arguments.StringArgumentType.string
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import mcx.cache.*
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

object InstallationsCommands {
  fun register(dispatcher: CommandDispatcher<Unit>) {
    // Create an alias: play -> installations play
    literal("play").build().let { play ->
      dispatcher.register(
        literal("installations")
      ).addChild(play)
      dispatcher.register(
        literal("play")
          .redirect(play)
      )
    }

    dispatcher.register(
      literal("installations")
        .then(
          literal("play")
            .then(
              argument("version", string())
                .executes { c ->
                  val version: String = c["version"]
                  playServer(version)
                }
                .then(
                  argument("args", greedyString())
                    .executes { c ->
                      val version: String = c["version"]
                      val args: String = c["args"]
                      playServer(version, args)
                    }
                )
            )
        )
        .then(
          literal("create")
            .then(
              argument("version", string())
                .executes { c ->
                  val version: String = c["version"]
                  if (getServerPath(version).exists()) {
                    1
                  } else {
                    fetchVersionManifest()
                      .versions
                      .first { it.id == version }
                      .url
                      .openStream()
                      .use { @OptIn(ExperimentalSerializationApi::class) json.decodeFromStream<Package>(it) }
                      .downloads
                      .let { downloads ->
                        downloads.server.url.openStream().use { getServerPath(version).saveFromStream(it) }
                        downloads.serverMappings.url.openStream().use { getServerMappingsPath(version).saveFromStream(it) }
                      }
                    0
                  }
                }
            )
        )
        .then(
          literal("delete")
            .then(
              argument("version", string())
                .executes { c ->
                  val version: String = c["version"]
                  @OptIn(ExperimentalPathApi::class)
                  getOrCreateServerRootPath(version).deleteRecursively()
                  0
                }
            )
        )
    )
  }
}
