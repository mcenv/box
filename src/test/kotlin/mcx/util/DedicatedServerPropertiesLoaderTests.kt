package mcx.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.decodeFromStringMap
import mcx.data.DedicatedServerProperties
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalSerializationApi::class)
object DedicatedServerPropertiesLoaderTests {
  @Test
  fun decode() {
    val map = mapOf(
      "level-name" to "world",
      "server-port" to "25565",
      "enable-rcon" to "true",
      "function-permission-level" to "4",
      "max-tick-time" to "60000",
      "broadcast-rcon-to-ops" to "true",
      "rcon.port" to "25575",
      "rcon.password" to "password",
    )
    val properties = Properties.decodeFromStringMap<DedicatedServerProperties>(map)
    assertEquals(
      DedicatedServerProperties(
        levelName = "world",
        serverPort = 25565,
        enableRcon = true,
        functionPermissionLevel = 4,
        maxTickTime = 60000,
        broadcastRconToOps = true,
        rcon = DedicatedServerProperties.Rcon(
          port = 25575,
        ),
      ),
      properties,
    )
  }

  @Test
  fun `decode default`() {
    val properties = Properties.decodeFromStringMap<DedicatedServerProperties>(emptyMap())
    assertEquals(
      DedicatedServerProperties(
        levelName = "world",
        serverPort = 25565,
        enableRcon = false,
        functionPermissionLevel = 2,
        maxTickTime = 60000,
        broadcastRconToOps = true,
        rcon = DedicatedServerProperties.Rcon(
          port = 25575,
        ),
      ),
      properties,
    )
  }
}
