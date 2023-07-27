package box.codec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URL

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
      val url: @Serializable(URLSerializer::class) URL,
    )
  }
}
