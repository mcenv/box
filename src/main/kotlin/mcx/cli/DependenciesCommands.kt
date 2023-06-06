package mcx.cli

import com.mojang.brigadier.CommandDispatcher
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import mcx.cache.getOrCreateDependenciesPath
import mcx.pass.Config
import mcx.util.green
import mcx.util.toDependencyTripleOrNull
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.io.path.*

object DependenciesCommands {
  fun register(dispatcher: CommandDispatcher<Unit>) {
    dispatcher.register(
      literal("dependencies")
        .then(
          literal("install")
            .executes {
              val config = Path("pack.json").inputStream().buffered().use {
                @OptIn(ExperimentalSerializationApi::class)
                Json.decodeFromStream<Config>(it)
              }
              config.dependencies.forEach { dependency ->
                val (owner, repository, tag) = dependency.value.toDependencyTripleOrNull() ?: run {
                  println("invalid dependency name: ${dependency.value}")
                  return@executes 1
                }

                try {
                  println("${green("installing")} $owner/$repository@$tag")
                  ZipInputStream(getArchiveUrl(owner, repository, tag).openStream().buffered()).use { input ->
                    val root = (getOrCreateDependenciesPath() / owner).createDirectories()
                    var entry = input.nextEntry
                    while (entry != null) {
                      val path = root / entry.name
                      if (entry.isDirectory) {
                        path.createDirectories()
                      } else {
                        path.createParentDirectories()
                        FileOutputStream(path.toFile()).use {
                          input.transferTo(it)
                        }
                        input.closeEntry()
                      }
                      entry = input.nextEntry
                    }
                  }
                } catch (_: FileNotFoundException) {
                  println("not found: $owner/$repository@$tag")
                }
              }

              0
            }
        )
    )
  }

  private fun getArchiveUrl(owner: String, repository: String, tag: String): URL {
    return URL("https://github.com/$owner/$repository/archive/$tag.zip")
  }
}
