package mcx.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand

@OptIn(ExperimentalCli::class)
object Version : Subcommand(
  "version",
  "Display the mcx version",
) {
  override fun execute() {
    val version = Version::class.java
      .getResource("/version")!!
      .readText()
    println("mcx $version")
  }
}
