package mcx.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.decodeFromStringMap
import mcx.data.DedicatedServerProperties
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader

@OptIn(ExperimentalSerializationApi::class)
fun loadDedicatedServerProperties(path: Path = Path("server.properties")): DedicatedServerProperties {
  val properties = java.util.Properties().apply { path.bufferedReader().use { load(it) } }
  return Properties.decodeFromStringMap(@Suppress("UNCHECKED_CAST") (properties.toMap() as Map<String, String>))
}