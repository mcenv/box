package mcx.cli

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal

object Version {
  fun register(dispatcher: CommandDispatcher<Unit>) {
    dispatcher.register(
      literal<Unit>("version")
        .executes {
          val version = Version::class.java.getResource("/version")!!.readText()
          println(version)
          0
        }
    )
  }
}
