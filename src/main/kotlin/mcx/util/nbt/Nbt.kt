package mcx.util.nbt

import kotlinx.serialization.*
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import mcx.util.nbt.internal.*
import java.io.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

sealed class Nbt(
  val configuration: NbtConfiguration,
  override val serializersModule: SerializersModule,
) : BinaryFormat, StringFormat {
  companion object Default : Nbt(NbtConfiguration(), EmptySerializersModule())

  override fun <T> encodeToByteArray(serializer: SerializationStrategy<T>, value: T): ByteArray {
    ByteArrayOutputStream().use {
      encodeToStream(serializer, value, it)
      return it.toByteArray()
    }
  }

  override fun <T> decodeFromByteArray(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T {
    ByteArrayInputStream(bytes).use {
      return decodeFromStream(deserializer, it)
    }
  }

  fun <T> encodeToStream(serializer: SerializationStrategy<T>, value: T, stream: OutputStream) {
    DataOutputStream(if (configuration.compressed) GZIPOutputStream(stream) else stream).use {
      if (configuration.root) {
        it.writeByte(COMPOUND.toInt())
        it.writeUTF("")
      }
      val encoder = BinaryTagEncoder(this, it)
      encoder.encodeSerializableValue(serializer, value)
    }
  }

  fun <T> decodeFromStream(deserializer: DeserializationStrategy<T>, stream: InputStream): T {
    DataInputStream(if (configuration.compressed) GZIPInputStream(stream) else stream).use {
      if (configuration.root) {
        it.skipBytes(3)
      }
      val decoder = BinaryTagDecoder(this, it)
      val value = decoder.decodeSerializableValue(deserializer)
      require(it.available() == 0)
      return value
    }
  }

  override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
    StringWriter().use {
      val encoder = StringTagEncoder(this, it)
      encoder.encodeSerializableValue(serializer, value)
      return it.toString()
    }
  }

  override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
    val decoder = StringTagDecoder(this, string)
    val value = decoder.decodeSerializableValue(deserializer)
    require(!decoder.canRead())
    return value
  }
}

fun Nbt(from: Nbt = Nbt.Default, action: NbtBuilder.() -> Unit): Nbt {
  val builder = NbtBuilder(from)
  builder.action()
  return NbtImpl(builder.build(), builder.serializerModule)
}

inline fun <reified T> Nbt.encodeToByteArray(value: T): ByteArray {
  return encodeToByteArray(serializersModule.serializer(), value)
}

inline fun <reified T> Nbt.decodeFromByteArray(bytes: ByteArray): T {
  return decodeFromByteArray(serializersModule.serializer(), bytes)
}

inline fun <reified T> Nbt.encodeToStream(value: T, stream: OutputStream) {
  encodeToStream(serializersModule.serializer(), value, stream)
}

inline fun <reified T> Nbt.decodeFromStream(stream: InputStream): T {
  return decodeFromStream(serializersModule.serializer(), stream)
}

inline fun <reified T> Nbt.encodeToString(value: T): String {
  return encodeToString(serializersModule.serializer(), value)
}

inline fun <reified T> Nbt.decodeFromString(string: String): T {
  return decodeFromString(serializersModule.serializer(), string)
}

class NbtBuilder internal constructor(nbt: Nbt) {
  var root: Boolean = nbt.configuration.root
  var compressed: Boolean = nbt.configuration.compressed
  var booleanEncodingStrategy: BooleanEncodingStrategy = nbt.configuration.booleanEncodingStrategy
  var byteTagSuffix: ByteTagSuffix = nbt.configuration.byteTagSuffix
  var shortTagSuffix: ShortTagSuffix = nbt.configuration.shortTagSuffix
  var longTagSuffix: LongTagSuffix = nbt.configuration.longTagSuffix
  var floatTagSuffix: FloatTagSuffix = nbt.configuration.floatTagSuffix
  var doubleTagSuffix: DoubleTagSuffix = nbt.configuration.doubleTagSuffix
  var trailingComma: Boolean = nbt.configuration.trailingComma

  var serializerModule: SerializersModule = nbt.serializersModule

  internal fun build(): NbtConfiguration {
    return NbtConfiguration(
      root = root,
      compressed = compressed,
      booleanEncodingStrategy = booleanEncodingStrategy,
      byteTagSuffix = byteTagSuffix,
      shortTagSuffix = shortTagSuffix,
      longTagSuffix = longTagSuffix,
      floatTagSuffix = floatTagSuffix,
      doubleTagSuffix = doubleTagSuffix,
      trailingComma = trailingComma,
    )
  }
}

private class NbtImpl(configuration: NbtConfiguration, serializersModule: SerializersModule) : Nbt(configuration, serializersModule)
