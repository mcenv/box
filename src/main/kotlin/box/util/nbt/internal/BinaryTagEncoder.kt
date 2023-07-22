package box.util.nbt.internal

import box.util.nbt.AsIntTag
import box.util.nbt.Nbt
import box.util.nbt.has
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import java.io.DataOutput
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalSerializationApi::class)
internal class BinaryTagEncoder(
  private val nbt: Nbt,
  private val output: DataOutput,
) : Encoder, CompositeEncoder {
  override val serializersModule: SerializersModule get() = nbt.serializersModule

  override fun encodeNull() {
    TODO()
  }

  override fun encodeBoolean(value: Boolean) {
    output.writeByte(if (value) 1 else 0)
  }

  override fun encodeByte(value: Byte) {
    output.writeByte(value.toInt())
  }

  override fun encodeShort(value: Short) {
    output.writeShort(value.toInt())
  }

  override fun encodeChar(value: Char) {
    TODO()
  }

  override fun encodeInt(value: Int) {
    output.writeInt(value)
  }

  override fun encodeLong(value: Long) {
    output.writeLong(value)
  }

  override fun encodeFloat(value: Float) {
    output.writeFloat(value)
  }

  override fun encodeDouble(value: Double) {
    output.writeDouble(value)
  }

  override fun encodeString(value: String) {
    output.writeUTF(value)
  }

  override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
    when {
      enumDescriptor.annotations.has<AsIntTag>() -> {
        encodeInt(index)
      }
      else                                       -> {
        val name = enumDescriptor.getElementName(index)
        encodeString(name)
      }
    }
  }

  override fun encodeInline(descriptor: SerialDescriptor): Encoder {
    return this
  }

  override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
    return this
  }

  override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
    val type = if (collectionSize == 0) {
      END
    } else {
      descriptor.getElementDescriptor(0).toTagType()
    }
    encodeByte(type)
    encodeInt(collectionSize)
    return this
  }

  override fun endStructure(descriptor: SerialDescriptor) {
    when (descriptor.kind) {
      StructureKind.CLASS -> {
        encodeByte(END)
      }
      else                -> {}
    }
  }

  @OptIn(ExperimentalContracts::class)
  private inline fun encodeElement(descriptor: SerialDescriptor, index: Int, encode: () -> Unit) {
    contract { callsInPlace(encode, InvocationKind.EXACTLY_ONCE) }
    when (descriptor.kind) {
      StructureKind.CLASS -> {
        val elementDescriptor = descriptor.getElementDescriptor(index).let {
          if (it.isInline) it.getElementDescriptor(0) else it
        }
        encodeByte(elementDescriptor.toTagType())
        encodeString(descriptor.getElementName(index))
      }
      else                -> {}
    }
    encode()
  }

  override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) {
    encodeElement(descriptor, index) { encodeBoolean(value) }
  }

  override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) {
    encodeElement(descriptor, index) { encodeByte(value) }
  }

  override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) {
    encodeElement(descriptor, index) { encodeShort(value) }
  }

  override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) {
    encodeElement(descriptor, index) { encodeChar(value) }
  }

  override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) {
    encodeElement(descriptor, index) { encodeInt(value) }
  }

  override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) {
    encodeElement(descriptor, index) { encodeLong(value) }
  }

  override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) {
    encodeElement(descriptor, index) { encodeFloat(value) }
  }

  override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) {
    encodeElement(descriptor, index) { encodeDouble(value) }
  }

  override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
    encodeElement(descriptor, index) { encodeString(value) }
  }

  override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder {
    return this
  }

  override fun <T> encodeSerializableElement(descriptor: SerialDescriptor, index: Int, serializer: SerializationStrategy<T>, value: T) {
    encodeElement(descriptor, index) { encodeSerializableValue(serializer, value) }
  }

  override fun <T : Any> encodeNullableSerializableElement(descriptor: SerialDescriptor, index: Int, serializer: SerializationStrategy<T>, value: T?) {
    encodeElement(descriptor, index) { encodeNullableSerializableValue(serializer, value) }
  }
}
