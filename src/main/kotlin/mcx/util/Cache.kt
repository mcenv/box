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

fun getServerMappingsPath(id: String): Path {
  return getOrCreateServerRootPath(id) / "server.txt"
}

// TODO: cache version_manifest_v2.json
@OptIn(ExperimentalSerializationApi::class)
fun fetchVersionManifest(): VersionManifest {
  return versionManifestUrl.openStream().use { json.decodeFromStream(it) }
}

fun Path.saveFromStream(input: InputStream) {
  outputStream().buffered().use { input.transferTo(it) }
}

fun playServer(
  id: String,
  args: String? = null,
  rconAction: (suspend (Rcon) -> Unit)? = null,
): Int {
  return runBlocking {
    val serverPath = getServerPath(id)
    // TODO: check sha1
    // TODO: print messages
    if (serverPath.isRegularFile()) {
      useDedicatedServerProperties { properties ->
        val minecraft = thread {
          val command = mutableListOf(java, bundlerRepoDir, "-jar", serverPath.pathString).also {
            if (args != null) {
              it.addAll(args.split(' '))
            }
          }
          ProcessBuilder(command)
            .directory(Path(".mcx").createDirectories().toFile())
            .inheritIO()
            .start()
            .waitFor()
        }
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
inline fun <R> useDedicatedServerProperties(block: (DedicatedServerProperties?) -> R): R {
  val serverPropertiesPath = Path(".mcx") / "server.properties"
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
