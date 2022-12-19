@file:UseSerializers(
  URLSerializer::class,
  DateSerializer::class,
)

package mcx.util.cache

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.net.URL
import java.time.Instant
import java.util.*

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
    val client: Download,
    @SerialName("client_mappings") val clientMappings: Download,
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
    val string = value.toString()
    encoder.encodeString(string)
  }

  override fun deserialize(
    decoder: Decoder,
  ): URL {
    val string = decoder.decodeString()
    return URL(string)
  }
}

object DateSerializer : KSerializer<Date> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Date", PrimitiveKind.STRING)

  override fun serialize(
    encoder: Encoder,
    value: Date,
  ) {
    val string =
      value
        .toInstant()
        .toString()
    encoder.encodeString(string)
  }

  override fun deserialize(
    decoder: Decoder,
  ): Date {
    val string = decoder.decodeString()
    return Date.from(Instant.parse(string))
  }
}
