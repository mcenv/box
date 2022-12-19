package mcx.cli

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import mcx.util.cache.*

@OptIn(ExperimentalCli::class)
object Install : Subcommand(
  "install",
  "Install",
) {
  private val version: String? by option(ArgType.String, "version", "v")
  private val type: String? by option(ArgType.Choice(listOf("release", "snapshot"), { it }), "type", "t")

  override fun execute() {
    if (version == null && type == null) {
      error("not specified: version")
    }

    val manifest = fetchVersionManifest()
    val id = when (type) {
      "release"  -> resolveVersion(VersionManifest.Version.Type.RELEASE, manifest)
      "snapshot" -> resolveVersion(VersionManifest.Version.Type.SNAPSHOT, manifest)
      else       -> version!!
    }
    val `package` = fetchPackage(id, manifest)
    installServer(`package`)
  }
}
