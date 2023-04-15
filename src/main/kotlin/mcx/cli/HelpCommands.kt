package mcx.cli

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType.greedyString

object HelpCommands {
  fun register(dispatcher: CommandDispatcher<Unit>) {
    dispatcher.register(
      literal("help")
        .executes {
          dispatcher.getSmartUsage(dispatcher.root, it.source).forEach { (_, usage) ->
            println(usage)
          }
          0
        }
        .then(
          argument("command", greedyString())
            .executes {
              val command: String = it["command"]
              val results = dispatcher.parse(command, it.source)
              results.context.nodes.lastOrNull()?.let { last ->
                dispatcher.getSmartUsage(last.node, it.source).forEach { (_, usage) ->
                  println("${results.reader.string} $usage")
                }
              }
              0
            }
        )
    )
  }
}
