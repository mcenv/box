package box.util

import box.cache.VersionManifest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.encodeToStringMap
import box.pass.Config
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread
import kotlin.io.path.*
import java.util.Properties as JProperties

val versionManifestUrl: URL by lazy {
  URI("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").toURL()
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
  return Path(System.getProperty("user.home"), ".box").createDirectories()
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
  args: Array<String>,
  rconAction: (suspend (Rcon) -> Unit)? = null,
) {
  return runBlocking {
    // TODO: check sha1
    // TODO: print messages

    val workspace = Path(".box").createDirectories()
    val config = Path("pack.json").decodeFromJson<Config>()
    val properties = config.properties

    (workspace / "eula.txt").writeText("eula=${properties.eula}\n")

    val password = secureRandomString()
    (workspace / "server.properties").outputStream().buffered().use { output ->
      JProperties().apply {
        putAll(Properties.encodeToStringMap(properties))
        put("enable-rcon", "true")
        put("rcon.password", password)
      }.store(output, null)
    }

    val minecraft = thread {
      val command = mutableListOf(java, bundlerRepoDir, "-jar", getServerPath(id).pathString).also {
        it += args
      }
      ProcessBuilder(command)
        .directory(workspace.toFile())
        .inheritIO()
        .start()
        .waitFor()
    }
    if (rconAction != null) {
      Rcon.connect(password, "localhost", properties.rcon.port, properties.maxTickTime).use {
        rconAction(it)
      }
    }
    minecraft.join()
  }
}

fun installDependencies(root: Path) {
  val config = (root / "pack.json").decodeFromJson<Config>()
  config.dependencies.forEach { dependency ->
    val (owner, repository, tag) = dependency.value.toDependencyTripleOrNull() ?: run {
      error("Invalid dependency triple: ${dependency.value}")
    }

    try {
      info("Installing", "$owner/$repository@$tag")
      ZipInputStream(URI("https://github.com/$owner/$repository/archive/$tag.zip").toURL().openStream().buffered()).use { input ->
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
      error("Not found: $owner/$repository@$tag")
    }
  }
}
