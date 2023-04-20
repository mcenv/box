package mcx.cli

import com.mojang.brigadier.CommandDispatcher
import mcx.lsp.McxLanguageServer
import org.eclipse.lsp4j.launch.LSPLauncher

object LspCommands {
  fun register(dispatcher: CommandDispatcher<Unit>) {
    dispatcher.register(
      literal("lsp")
        .executes {
          val server = McxLanguageServer()
          val launcher = LSPLauncher.createServerLauncher(server, System.`in`, System.out)
          server.connect(launcher.remoteProxy)
          launcher.startListening().get()
          0
        }
    )
  }
}