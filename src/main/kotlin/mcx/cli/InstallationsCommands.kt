package mcx.cli

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType.string
import mcx.util.createServer
import mcx.util.deleteServer
import mcx.util.playServer

object InstallationsCommands {
  fun register(dispatcher: CommandDispatcher<Unit>) {
    dispatcher.register(
      literal("installations")
        .then(
          literal("play")
            .then(
              argument("version", string())
                .executes {
                  val version: String = it["version"]
                  playServer(version)
                }
            )
        )
        .then(
          literal("create")
            .then(
              argument("version", string())
                .executes {
                  val version: String = it["version"]
                  createServer(version)
                }
            )
        )
        .then(
          literal("delete")
            .then(
              argument("version", string())
                .executes {
                  val version: String = it["version"]
                  deleteServer(version)
                }
            )
        )
    )
  }
}
