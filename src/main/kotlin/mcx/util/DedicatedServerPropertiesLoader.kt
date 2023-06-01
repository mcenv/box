package mcx.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.decodeFromStringMap
import mcx.data.DedicatedServerProperties
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.isRegularFile

@OptIn(ExperimentalSerializationApi::class)
fun loadDedicatedServerProperties(path: Path): DedicatedServerProperties? {
  return if (path.isRegularFile()) {
    val properties = java.util.Properties().apply { path.bufferedReader().use { load(it) } }
    Properties.decodeFromStringMap<DedicatedServerProperties>(@Suppress("UNCHECKED_CAST") (properties.toMap() as Map<String, String>))
  } else {
    null
  }
}
