package mcx.phase

import kotlinx.serialization.Serializable

@Serializable
data class Config(
  val name: String,
  val debug: Boolean = false,
)
