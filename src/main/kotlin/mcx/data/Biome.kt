package mcx.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Biome(
  val precipitation: String,
  val temperature: Double,
  val downfall: Double,
  val effects: Effects,
  val carvers: Map<String, Unit>, // TODO
  val features: List<Unit>, // TODO
  @SerialName("creature_spawn_probability") val creatureSpawnProbability: Double,
  val spawners: Map<String, Unit>, // TODO
  val spawnCosts: Map<String, Unit>, // TODO
) {
  @Serializable
  data class Effects(
    @SerialName("fog_color") val fogColor: Int,
    @SerialName("water_color") val waterColor: Int,
    @SerialName("water_fog_color") val waterFogColor: Int,
    @SerialName("sky_color") val skyColor: Int,
    @SerialName("foliage_color") val foliageColor: Int,
    @SerialName("grass_color") val grassColor: Int,
  )
}
