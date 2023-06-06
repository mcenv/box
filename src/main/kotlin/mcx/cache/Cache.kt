package mcx.cache

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.encodeToStringMap
import mcx.pass.Config
import java.io.InputStream
import java.net.URL
import java.nio.file.Path
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