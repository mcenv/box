package mcx.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import mcx.lsp.McxLanguageServer
import org.eclipse.lsp4j.launch.LSPLauncher

@OptIn(ExperimentalCli::class)
object Lsp : Subcommand(
  "lsp",
  "Launch the language server",
) {
  override fun execute() {
    val server = McxLanguageServer()
    val launcher = LSPLauncher.createServerLauncher(
      server,
      System.`in`,
      System.out,
    )
    server.connect(launcher.remoteProxy)
    launcher.startListening()
  }
}
