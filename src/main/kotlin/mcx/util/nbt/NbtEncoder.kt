package mcx.util.nbt

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import java.io.DataOutput

@OptIn(ExperimentalSerializationApi::class)
class NbtEncoder(
  private val output: DataOutput,
) : AbstractEncoder() {
  override val serializersModule: SerializersModule = EmptySerializersModule()

  override fun encodeBoolean(
    value: Boolean,
  ): Unit =
    encodeByte(if (value) 1 else 0)

  override fun encodeByte(
    value: Byte,
  ): Unit =
    output.writeByte(value.toInt())

  override fun encodeShort(
    value: Short,
  ): Unit =
    output.writeShort(value.toInt())

  override fun encodeChar(
    value: Char,
  ): Unit =
    output.writeChar(value.code)

  override fun encodeInt(
    value: Int,
  ): Unit =
    output.writeInt(value)

  override fun encodeLong(
    value: Long,
  ): Unit =
    output.writeLong(value)

  override fun encodeFloat(
    value: Float,
  ): Unit =
    output.writeFloat(value)

  override fun encodeDouble(
    value: Double,
  ): Unit =
    output.writeDouble(value)

  override fun encodeString(
    value: String,
  ): Unit =
    output.writeUTF(value)

  override fun encodeEnum(
    enumDescriptor: SerialDescriptor,
    index: Int,
  ): Unit =
    output.writeInt(index)

  override fun encodeInline(
    descriptor: SerialDescriptor,
  ): Encoder =
    this

  override fun beginStructure(
    descriptor: SerialDescriptor,
  ): CompositeEncoder =
    this

  override fun encodeElement(
    descriptor: SerialDescriptor,
    index: Int,
  ): Boolean {
    when (descriptor.kind) {
      StructureKind.CLASS -> {
        encodeNbtType(descriptor.getElementDescriptor(index).kind.toNbtType())
        encodeString(descriptor.getElementName(index))
      }
      else                -> Unit
    }
    return true
  }

  override fun endStructure(
    descriptor: SerialDescriptor,
  ): Unit =
    when (descriptor.kind) {
      StructureKind.CLASS -> encodeNbtType(NbtType.END)
      else                -> Unit
    }

  override fun beginCollection(
    descriptor: SerialDescriptor,
    collectionSize: Int,
  ): CompositeEncoder =
    apply {
      val type = when (collectionSize) {
        0    -> NbtType.END
        else -> descriptor.getElementDescriptor(0).kind.toNbtType()
      }
      encodeNbtType(type)
      encodeInt(collectionSize)
    }

  fun encodeNbtType(
    type: NbtType,
  ): Unit =
    encodeByte(type.ordinal.toByte())
}
