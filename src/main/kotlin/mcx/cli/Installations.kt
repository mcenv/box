package mcx.cli

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType.string
import mcx.cache.createServer
import mcx.cache.deleteServer

object Installations {
  fun register(dispatcher: CommandDispatcher<Unit>) {
    dispatcher.register(
      literal("installations")
        .then(
          literal("create")
            .then(
              argument("version", string())
                .executes {
                  val version: String = it["version"]
                  createServer(version)
                  0
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
                  0
                }
            )
        )
    )
  }
}
