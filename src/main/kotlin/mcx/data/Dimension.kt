package mcx.data

import kotlinx.serialization.Serializable

@Serializable
data class Dimension(
  val type: ResourceLocation,
  val generator: Generator,
) {
  @Serializable
  data class Generator(
    val type: String,
    val settings: Settings,
  ) {
    @Serializable
    data class Settings(
      val layers: List<Layer>,
      val biome: ResourceLocation,
    ) {
      @Serializable
      data class Layer(
        val height: Int,
        val block: ResourceLocation,
      )
    }
  }
}
