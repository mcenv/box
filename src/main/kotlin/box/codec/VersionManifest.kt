package box.codec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URL
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
    val url: @Serializable(URLSerializer::class) URL,
    val time: @Serializable(DateSerializer::class) Date,
    val releaseTime: @Serializable(DateSerializer::class) Date,
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
