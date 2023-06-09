package mcx.pass

import kotlinx.serialization.Serializable
import mcx.data.DedicatedServerProperties

@Serializable
data class Config(
  val name: String,
  val description: String, // TODO: use [Component]
  val debug: Debug = Debug(),
  val dependencies: Map<String, String> = emptyMap(),
  val properties: DedicatedServerProperties = DedicatedServerProperties(),
) {
  @Serializable
  data class Debug(
    val verbose: Boolean = false,
  )
}
