package mcx.cli

import com.mojang.brigadier.CommandDispatcher

object Version {
  fun register(dispatcher: CommandDispatcher<Unit>) {
    dispatcher.register(
      literal("version")
        .executes {
          val version = Version::class.java.getResource("/version")!!.readText()
          println(version)
          0
        }
    )
  }
}
