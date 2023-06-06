package mcx.pass.frontend

import mcx.ast.ModuleLocation
import mcx.pass.Context
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.readText

class Read private constructor(
  private val context: Context,
  private val core: Path?,
  private val src: Path,
) {
  private fun read(location: ModuleLocation): String {
    return when (location.parts.first()) {
             context.config.name -> src.resolve(location)
             "core"              -> core?.resolve(location)
             else                -> null // TODO: read from dependencies
           }?.readText() ?: ""
  }

  private fun Path.resolve(location: ModuleLocation): Path? {
    return try {
      this / location
        .parts
        .drop(1) // Drop pack name
        .joinToString("/", postfix = ".mcx")
    } catch (_: InvalidPathException) {
      null
    }
  }

  companion object {
    operator fun invoke(
      context: Context,
      core: Path?,
      src: Path,
      location: ModuleLocation,
    ): String {
      return Read(context, core, src).read(location)
    }
  }
}
