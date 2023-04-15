package mcx.cache

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.*

private val VERSION_MANIFEST_URL: URL = URL("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
private val json: Json = Json { ignoreUnknownKeys = true }

// TODO: cache version_manifest_v2.json
@OptIn(ExperimentalSerializationApi::class)
private fun fetchVersionManifest(): VersionManifest {
  return VERSION_MANIFEST_URL.openStream().use { json.decodeFromStream(it) }
}

private fun getRootPath(): Path {
  return Path(System.getProperty("user.home"), ".mcx").createDirectories()
}

private fun getVersionsPath(): Path {
  return getRootPath().resolve("versions").createDirectories()
}

private fun getServerRootPath(id: String): Path {
  return getVersionsPath().resolve(id).createDirectories()
}

fun getServerPath(id: String): Path {
  return getServerRootPath(id).resolve("server.jar")
}

fun getServerMappingsPath(id: String): Path {
  return getServerRootPath(id).resolve("server.txt")
}

@OptIn(ExperimentalSerializationApi::class)
fun createServer(id: String) {
  // TODO: check sha1
  // TODO: remap server.jar
  // TODO: print messages
  if (getServerPath(id).notExists()) {
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
  }
}

@OptIn(ExperimentalPathApi::class)
fun deleteServer(id: String) {
  // TODO: print messages
  getServerRootPath(id).deleteRecursively()
}

private fun Path.saveFromStream(input: InputStream) {
  outputStream().buffered().use { input.transferTo(it) }
}
