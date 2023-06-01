@file:UseSerializers(
  URLSerializer::class,
  DateSerializer::class,
)

package mcx.util

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.decodeFromStringMap
import mcx.data.DedicatedServerProperties
import java.io.InputStream
import java.net.URL
import java.nio.file.Path
import java.time.Instant
import java.util.*
import kotlin.concurrent.thread
import kotlin.io.path.*
import java.util.Properties as JProperties

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

fun playServer(id: String, rconAction: (suspend (Rcon) -> Unit)? = null): Int {
  return runBlocking {
    val serverPath = getServerPath(id)
    // TODO: check sha1
    // TODO: print messages
    if (serverPath.isRegularFile()) {
      useDedicatedServerProperties { properties ->
        val minecraft = thread { ProcessBuilder(java, bundlerRepoDir, "-jar", serverPath.pathString, "nogui").inheritIO().start().waitFor() }
        if (rconAction != null && properties != null && properties.enableRcon && properties.rcon.password.isNotEmpty()) {
          Rcon.connect(properties.rcon.password, "localhost", properties.rcon.port, properties.maxTickTime).use {
            rconAction(it)
          }
        }
        minecraft.join()
        0
      }
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

@OptIn(ExperimentalSerializationApi::class)
inline fun <R> useDedicatedServerProperties(block: (DedicatedServerProperties?) -> R): R {
  val serverPropertiesPath = Path("server.properties")
  return if (serverPropertiesPath.isRegularFile()) {
    val text = serverPropertiesPath.readText()
    val properties = JProperties().apply { load(text.reader()) }
    val map = @Suppress("UNCHECKED_CAST") (properties.toMap() as Map<String, String>)
    val dedicatedServerProperties = Properties.decodeFromStringMap<DedicatedServerProperties>(map)
    val result = block(dedicatedServerProperties)
    serverPropertiesPath.writeText(text)
    result
  } else {
    block(null)
  }
}

@Serializable
data class VersionManifest(
  val latest: Latest,
  val versions: List<Version>,
) {
  @Serializable
  data class Latest(
    val release: String,
    val snapshot: String,
  )

  @Serializable
  data class Version(
    val id: String,
    val type: Type,
    val url: URL,
    val time: Date,
    val releaseTime: Date,
    val sha1: String,
  ) {
    @Serializable
    enum class Type {
      @SerialName("release")
      RELEASE,

      @SerialName("snapshot")
      SNAPSHOT,

      @SerialName("old_beta")
      OLD_BETA,

      @SerialName("old_alpha")
      OLD_ALPHA,
    }
  }
}

@Serializable
data class Package(
  val downloads: Downloads,
  val id: String,
) {
  @Serializable
  data class Downloads(
    val server: Download,
    @SerialName("server_mappings") val serverMappings: Download,
  ) {
    @Serializable
    data class Download(
      val sha1: String,
      val size: Long,
      val url: URL,
    )
  }
}

object URLSerializer : KSerializer<URL> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("URL", PrimitiveKind.STRING)

  override fun serialize(
    encoder: Encoder,
    value: URL,
  ) {
    encoder.encodeString(value.toString())
  }

  override fun deserialize(
    decoder: Decoder,
  ): URL {
    return URL(decoder.decodeString())
  }
}

object DateSerializer : KSerializer<Date> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Date", PrimitiveKind.STRING)

  override fun serialize(
    encoder: Encoder,
    value: Date,
  ) {
    encoder.encodeString(value.toInstant().toString())
  }

  override fun deserialize(
    decoder: Decoder,
  ): Date {
    return Date.from(Instant.parse(decoder.decodeString()))
  }
}
