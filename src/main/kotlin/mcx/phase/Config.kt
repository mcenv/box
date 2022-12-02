package mcx.phase

import kotlinx.serialization.Serializable

@Serializable
data class Config(
  val name: String,
  val description: String, // TODO: use [Component]
  val debug: Boolean = false,
)
