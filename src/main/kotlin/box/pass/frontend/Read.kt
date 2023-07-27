package box.pass.frontend

import box.ast.common.ModuleLocation
import box.util.getOrCreateDependenciesPath
import box.pass.Context
import box.util.toDependencyTripleOrNull
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

class Read private constructor(
  private val context: Context,
  private val core: Path?,
  private val src: Path,
) {
  private fun read(location: ModuleLocation): String {
    val path = when (val pack = location.parts.firstOrNull()) {
      context.config.name -> src.resolve(location)
      "core"              -> core?.resolve(location)
      null                -> null
      else                -> resolveDependency(pack, location)
    }
    return when (path?.isRegularFile()) {
      true -> path.readText()
      else -> ""
    }
  }

  private fun resolveDependency(pack: String, location: ModuleLocation): Path? {
    val (owner, repository, tag) = context.config.dependencies[pack]?.toDependencyTripleOrNull() ?: return null
    val root = getOrCreateDependenciesPath() / owner / "$repository-$tag" / "src"
    return root.resolve(location)
  }

  private fun Path.resolve(location: ModuleLocation): Path? {
    return try {
      this / location
        .parts
        .drop(1) // Drop pack name
        .joinToString("/", postfix = ".box")
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
