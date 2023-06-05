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
              literal("release")
                .executes {
                  create(Version.Release)
                }
            )
            .then(
              literal("snapshot")
                .executes {
                  create(Version.Snapshot)
                }
            )
            .then(
              argument("version", string())
                .executes { c ->
                  val version: String = c["version"]
                  create(Version.Custom(version))
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

  private fun create(version: Version): Int {
    var manifest: VersionManifest? = null
    val id = when (version) {
      Version.Release   -> {
        manifest = fetchVersionManifest()
        manifest.latest.release
      }
      Version.Snapshot  -> {
        manifest = fetchVersionManifest()
        manifest.latest.snapshot
      }
      is Version.Custom -> {
        version.id
      }
    }

    if (getServerPath(id).exists()) {
      return 1
    }

    (manifest ?: fetchVersionManifest())
      .versions
      .first { it.id == id }
      .url
      .openStream()
      .use { @OptIn(ExperimentalSerializationApi::class) json.decodeFromStream<Package>(it) }
      .downloads
      .let { downloads ->
        downloads.server.url.openStream().use { getServerPath(id).saveFromStream(it) }
        downloads.serverMappings.url.openStream().use { getServerMappingsPath(id).saveFromStream(it) }
      }

    return 0
  }

  private sealed class Version {
    data object Release : Version()
    data object Snapshot : Version()
    data class Custom(val id: String) : Version()
  }
}
