package mcx.cli

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType.greedyString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import mcx.pass.Config
import mcx.util.getOrCreateDependenciesPath
import java.io.FileNotFoundException
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.io.path.*

object DependenciesCommands {
  fun register(dispatcher: CommandDispatcher<Unit>) {
    dispatcher.register(
      literal("dependencies")
        .then(
          literal("add")
            .then(
              argument("name", greedyString())
                .executes { c ->
                  val name: String = c["name"]
                  val result = Regex("""^([^/]+)/([^@]+)@(.+)$""").matchEntire(name) ?: run {
                    println("invalid dependency name: $name")
                    return@executes 1
                  }
                  val (_, owner, repository, tag) = result.groupValues

                  try {
                    // TODO: message
                    ZipInputStream(getArchiveUrl(owner, repository, tag).openStream().buffered()).use { input ->
                      val root = (getOrCreateDependenciesPath() / owner).createDirectories()
                      var entry = input.nextEntry
                      while (entry != null) {
                        val path = root / entry.name
                        if (entry.isDirectory) {
                          path.createDirectories()
                        } else {
                          path.createParentDirectories()
                          input.copyTo(path.outputStream().buffered())
                        }
                        entry = input.nextEntry
                      }
                    }

                    Path("pack.json").let { packPath ->
                      @OptIn(ExperimentalSerializationApi::class)
                      packPath.inputStream().buffered().use { input ->
                        val oldConfig = InitCommands.json.decodeFromStream<Config>(input)
                        val newConfig = oldConfig.copy(dependencies = oldConfig.dependencies + name)
                        packPath.outputStream().buffered().use { output ->
                          InitCommands.json.encodeToStream(newConfig, output)
                        }
                      }
                    }

                    0
                  } catch (_: FileNotFoundException) {
                    println("not found: $owner/$repository@$tag")
                    1
                  }
                }
            )
        )
    )
  }

  private fun getArchiveUrl(owner: String, repository: String, tag: String): URL {
    return URL("https://github.com/$owner/$repository/archive/$tag.zip")
  }
}
