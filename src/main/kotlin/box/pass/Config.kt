package box.pass

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import box.codec.DedicatedServerProperties

@Serializable
data class Config(
  val name: String,
  val description: String, // TODO: use [Component]
  val output: Output = Output.FILE,
  val debug: Debug = Debug(),
  val dependencies: Map<String, String> = emptyMap(),
  val properties: DedicatedServerProperties = DedicatedServerProperties(),
) {
  @Serializable
  data class Debug(
    val verbose: Boolean = false,
  )

  @Serializable
  enum class Output {
    @SerialName("path")
    PATH,

    @SerialName("file")
    FILE,

    @SerialName("none")
    NONE,
  }
}
