package box.data

import kotlinx.serialization.Serializable

@Serializable
data class ResourceLocation(
  val namespace: String,
  val path: String,
)
