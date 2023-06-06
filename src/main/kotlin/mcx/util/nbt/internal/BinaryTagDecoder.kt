package mcx.util.nbt.internal

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule
import mcx.util.collections.IntList
import mcx.util.nbt.AsIntTag
import mcx.util.nbt.Nbt
import mcx.util.nbt.has
import java.io.DataInput

@OptIn(ExperimentalSerializationApi::class)
internal class BinaryTagDecoder(
  private val nbt: Nbt,
  private val input: DataInput,
) : Decoder, CompositeDecoder {
  private val elementsCounts: IntList = IntList()
  private val elementIndexes: IntList = IntList()

  override val serializersModule: SerializersModule get() = nbt.serializersModule

  override fun decodeNotNullMark(): Boolean {
    TODO()
  }

  override fun decodeNull(): Nothing? {
    TODO()
  }

  override fun decodeBoolean(): Boolean {
    return input.readByte().toInt() != 0
  }

  override fun decodeByte(): Byte {
    return input.readByte()
  }

  override fun decodeShort(): Short {
    return input.readShort()
  }

  override fun decodeChar(): Char {
    TODO()
  }

  override fun decodeInt(): Int {
    return input.readInt()
  }

  override fun decodeLong(): Long {
    return input.readLong()
  }

  override fun decodeFloat(): Float {
    return input.readFloat()
  }

  override fun decodeDouble(): Double {
    return input.readDouble()
  }

  override fun decodeString(): String {
    return input.readUTF()
  }

  override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
    return when {
      enumDescriptor.annotations.has<AsIntTag>() -> {
        decodeInt()
      }
      else                                       -> {
        val name = decodeString()
        enumDescriptor.getElementIndex(name)
      }
    }
  }

  override fun decodeInline(descriptor: SerialDescriptor): Decoder {
    return this
  }

  override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
    if (descriptor.kind == StructureKind.LIST) {
      elementIndexes.add(0)
    } else {
      elementsCounts.add(-1)
      elementIndexes.add(-1)
    }
    return this
  }

  override fun endStructure(descriptor: SerialDescriptor) {
    elementsCounts.pop()
    elementIndexes.pop()
  }

  override fun decodeSequentially(): Boolean {
    return elementIndexes.last() != -1
  }

  override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
    return when (descriptor.kind) {
      StructureKind.CLASS -> {
        val type = decodeByte()
        if (type == END) {
          DECODE_DONE
        } else {
          val name = decodeString()
          descriptor.getElementIndex(name)
        }
      }
      StructureKind.LIST  -> {
        elementIndexes.modifyLast { it + 1 }
      }
      else                -> TODO()
    }
  }

  override fun decodeCollectionSize(descriptor: SerialDescriptor): Int {
    return when (descriptor.kind) {
      StructureKind.LIST -> {
        decodeByte()
        decodeInt().also {
          elementsCounts.add(it)
        }
      }
      else               -> -1
    }
  }

  override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean {
    return decodeBoolean()
  }

  override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte {
    return decodeByte()
  }

  override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char {
    return decodeChar()
  }

  override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short {
    return decodeShort()
  }

  override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int {
    return decodeInt()
  }

  override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long {
    return decodeLong()
  }

  override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float {
    return decodeFloat()
  }

  override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double {
    return decodeDouble()
  }

  override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String {
    return decodeString()
  }

  override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder {
    return this
  }

  override fun <T> decodeSerializableElement(descriptor: SerialDescriptor, index: Int, deserializer: DeserializationStrategy<T>, previousValue: T?): T {
    return decodeSerializableValue(deserializer)
  }

  override fun <T : Any> decodeNullableSerializableElement(descriptor: SerialDescriptor, index: Int, deserializer: DeserializationStrategy<T?>, previousValue: T?): T? {
    return if (deserializer.descriptor.isNullable || decodeNotNullMark()) {
      decodeSerializableValue(deserializer)
    } else {
      decodeNull()
    }
  }
}
