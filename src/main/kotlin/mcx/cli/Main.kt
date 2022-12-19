package mcx.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ExperimentalCli

@OptIn(ExperimentalCli::class)
fun main(args: Array<String>) {
  val parser = ArgParser("mcx")
  parser.subcommands(
    Build,
    Init,
    Launch,
    Lsp,
    Version,
  )
  parser.parse(args)
}
