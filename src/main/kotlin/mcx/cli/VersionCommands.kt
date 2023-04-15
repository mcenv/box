package mcx.cli

import com.mojang.brigadier.CommandDispatcher

object VersionCommands {
  fun register(dispatcher: CommandDispatcher<Unit>) {
    dispatcher.register(
      literal("version")
        .executes {
          val version = VersionCommands::class.java.getResource("/version")!!.readText()
          print(version)
          0
        }
    )
  }
}
