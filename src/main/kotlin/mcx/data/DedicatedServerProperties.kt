package mcx.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes

@Serializable
data class DedicatedServerProperties(
  @SerialName("level-name") val levelName: String = "world",
  @SerialName("server-port") val serverPort: Int = 25565,
  @SerialName("enable-rcon") val enableRcon: Boolean = false,
  @SerialName("function-permission-level") val functionPermissionLevel: Int = 2,
  @SerialName("max-tick-time") val maxTickTime: Long = 1L.minutes.inWholeMilliseconds,
  @SerialName("broadcast-rcon-to-ops") val broadcastRconToOps: Boolean = true,
  val rcon: Rcon = Rcon(),
) {
  @Serializable
  data class Rcon(
    val port: Int = 25575,
    val password: String = "",
  )
}
