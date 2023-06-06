package mcx.cache

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.encodeToStringMap
import mcx.pass.Config
import mcx.util.green
import mcx.util.toDependencyTripleOrNull
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread
import kotlin.io.path.*
import java.util.Properties as JProperties

val versionManifestUrl: URL by lazy {
  URL("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
}

val json: Json by lazy {
  Json { ignoreUnknownKeys = true }
}

val java: String by lazy {
  ProcessHandle.current().info().command().orElseThrow()
}

val bundlerRepoDir: String by lazy {
  "-DbundlerRepoDir=\"${getOrCreateRootPath()}\""
}

fun getOrCreateRootPath(): Path {
  return Path(System.getProperty("user.home"), ".mcx").createDirectories()
}

fun getOrCreateDependenciesPath(): Path {
  return (getOrCreateRootPath() / "dependencies").createDirectories()
}

fun getOrCreateVersionsPath(): Path {
  return (getOrCreateRootPath() / "versions").createDirectories()
}

fun getOrCreateServerRootPath(id: String): Path {
  return (getOrCreateVersionsPath() / id).createDirectories()
}

fun getServerPath(id: String): Path {
  return getOrCreateServerRootPath(id) / "server.jar"
}

// TODO: cache version_manifest_v2.json
@OptIn(ExperimentalSerializationApi::class)
fun fetchVersionManifest(): VersionManifest {
  return versionManifestUrl.openStream().use { json.decodeFromStream(it) }
}

fun Path.saveFromStream(input: InputStream) {
  outputStream().buffered().use { input.transferTo(it) }
}

@OptIn(ExperimentalSerializationApi::class)
fun playServer(
  id: String,
  args: String? = null,
  rconAction: (suspend (Rcon) -> Unit)? = null,
): Int {
  return runBlocking {
    val configPath = Path("pack.json")
    if (configPath.notExists()) {
      return@runBlocking 1
    }

    // TODO: check sha1
    // TODO: print messages
    val serverPath = getServerPath(id)
    if (!serverPath.isRegularFile()) {
      return@runBlocking 1
    }

    val workspace = Path(".mcx").createDirectories()
    val config = Json.decodeFromStream<Config>(configPath.inputStream().buffered())
    val properties = config.properties

    (workspace / "eula.txt").writeText("eula=${properties.eula}\n")
    (workspace / "server.properties").outputStream().buffered().use { output ->
      JProperties().apply { putAll(Properties.encodeToStringMap(properties)) }.store(output, null)
    }

    val minecraft = thread {
      val command = mutableListOf(java, bundlerRepoDir, "-jar", serverPath.pathString).also {
        if (args != null) {
          it.addAll(args.split(' '))
        }
      }
      ProcessBuilder(command)
        .directory(workspace.toFile())
        .inheritIO()
        .start()
        .waitFor()
    }
    if (rconAction != null && properties.enableRcon && properties.rcon.password.isNotEmpty()) {
      Rcon.connect(properties.rcon.password, "localhost", properties.rcon.port, properties.maxTickTime).use {
        rconAction(it)
      }
    }
    minecraft.join()
    0
  }
}

fun installDependencies(root: Path): Int {
  val config = (root / "pack.json").inputStream().buffered().use {
    @OptIn(ExperimentalSerializationApi::class)
    Json.decodeFromStream<Config>(it)
  }
  config.dependencies.forEach { dependency ->
    val (owner, repository, tag) = dependency.value.toDependencyTripleOrNull() ?: run {
      println("invalid dependency name: ${dependency.value}")
      return 1
    }

    try {
      println("${green("installing")} $owner/$repository@$tag")
      ZipInputStream(getArchiveUrl(owner, repository, tag).openStream().buffered()).use { input ->
        val pack = (getOrCreateDependenciesPath() / owner).createDirectories()
        var entry = input.nextEntry
        while (entry != null) {
          val path = pack / entry.name
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

  return 0
}

private fun getArchiveUrl(owner: String, repository: String, tag: String): URL {
  return URL("https://github.com/$owner/$repository/archive/$tag.zip")
}
