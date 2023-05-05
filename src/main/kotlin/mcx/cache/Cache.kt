package mcx.cache

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import mcx.util.Rcon
import mcx.util.loadDedicatedServerProperties
import java.io.InputStream
import java.net.URL
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.io.path.*

private val versionManifestUrl: URL by lazy {
  URL("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
}

private val json: Json by lazy {
  Json { ignoreUnknownKeys = true }
}

private val java: String by lazy {
  ProcessHandle.current().info().command().orElseThrow()
}

private val bundlerRepoDir: String by lazy {
  "-DbundlerRepoDir=\"${getOrCreateRootPath()}\""
}

private fun getOrCreateRootPath(): Path {
  return Path(System.getProperty("user.home"), ".mcx").createDirectories()
}

private fun getOrCreateVersionsPath(): Path {
  return getOrCreateRootPath().resolve("versions").createDirectories()
}

private fun getOrCreateServerRootPath(id: String): Path {
  return getOrCreateVersionsPath().resolve(id).createDirectories()
}

private fun getServerPath(id: String): Path {
  return getOrCreateServerRootPath(id).resolve("server.jar")
}

private fun getServerMappingsPath(id: String): Path {
  return getOrCreateServerRootPath(id).resolve("server.txt")
}

// TODO: cache version_manifest_v2.json
@OptIn(ExperimentalSerializationApi::class)
private fun fetchVersionManifest(): VersionManifest {
  return versionManifestUrl.openStream().use { json.decodeFromStream(it) }
}

private fun Path.saveFromStream(input: InputStream) {
  outputStream().buffered().use { input.transferTo(it) }
}

// TODO: refactor
fun playServer(id: String): Int {
  return runBlocking {
    val serverPath = getServerPath(id)
    // TODO: check sha1
    // TODO: print messages
    if (serverPath.exists()) {
      val properties = loadDedicatedServerProperties()
      val minecraft = thread { ProcessBuilder(java, bundlerRepoDir, "-jar", serverPath.pathString).inheritIO().start().waitFor() }
      if (properties.enableRcon && properties.rcon.password.isNotEmpty()) {
        Rcon.connect(properties.rcon.password, "localhost", properties.rcon.port, properties.maxTickTime).use {
          // TODO
        }
      }
      minecraft.join()
      0
    } else {
      1
    }
  }
}

@OptIn(ExperimentalSerializationApi::class)
fun createServer(id: String): Int {
  // TODO: check sha1
  // TODO: remap server.jar
  // TODO: print messages
  return if (getServerPath(id).exists()) {
    1
  } else {
    fetchVersionManifest()
      .versions
      .first { it.id == id }
      .url
      .openStream()
      .use { json.decodeFromStream<Package>(it) }
      .downloads
      .let { downloads ->
        downloads.server.url.openStream().use { getServerPath(id).saveFromStream(it) }
        downloads.serverMappings.url.openStream().use { getServerMappingsPath(id).saveFromStream(it) }
      }
    0
  }
}

@OptIn(ExperimentalPathApi::class)
fun deleteServer(id: String): Int {
  // TODO: print messages
  getOrCreateServerRootPath(id).deleteRecursively()
  return 0
}
