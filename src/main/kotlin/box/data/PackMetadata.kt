package box.data

import kotlinx.serialization.Serializable

@Serializable
data class PackMetadata(
  val pack: PackMetadataSection,
)
