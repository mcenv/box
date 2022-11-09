package mcx.data

import kotlinx.serialization.Serializable

@Serializable
data class Tag(
  val replace: Boolean = false,
  val values: List<ResourceLocation>,
)
