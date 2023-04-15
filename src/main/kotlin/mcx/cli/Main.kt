package mcx.cli

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import kotlin.system.exitProcess

fun main(args: Array<String>) {
  val dispatcher = CommandDispatcher<Unit>()
  registerCommands(dispatcher)
  try {
    val result = dispatcher.execute(args.joinToString(" "), Unit)
    exitProcess(result)
  } catch (e: CommandSyntaxException) {
    System.err.println(e.message)
    exitProcess(1)
  }
}

private fun registerCommands(dispatcher: CommandDispatcher<Unit>) {
  Build.register(dispatcher)
  Init.register(dispatcher)
  Lsp.register(dispatcher)
  Version.register(dispatcher)
}
