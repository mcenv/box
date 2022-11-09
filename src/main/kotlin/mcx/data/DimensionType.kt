package mcx.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DimensionType(
  @SerialName("fixed_time") val fixedTime: Long,
  @SerialName("has_skylight") val hasSkylight: Boolean,
  @SerialName("has_ceiling") val hasCeiling: Boolean,
  val ultrawarm: Boolean,
  val natural: Boolean,
  @SerialName("coordinate_scale") val coordinateScale: Double,
  @SerialName("bed_works") val bedWorks: Boolean,
  @SerialName("respawn_anchor_works") val respawnAnchorWorks: Boolean,
  @SerialName("min_y") val minY: Int,
  val height: Int,
  @SerialName("logical_height") val logicalHeight: Int,
  val infiniburn: ResourceLocation,
  @SerialName("ambient_light") val ambientLight: Double,
  @SerialName("piglin_safe") val piglinSafe: Boolean,
  @SerialName("has_raids") val hasRaids: Boolean,
  @SerialName("monster_spawn_light_level") val monsterSpawnLightLevel: Int,
  @SerialName("monster_spawn_block_light_limit") val monsterSpawnBlockLightLimit: Int,
)
