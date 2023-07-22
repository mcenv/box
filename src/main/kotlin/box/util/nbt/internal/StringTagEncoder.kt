package box.util.nbt.internal

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import box.util.collections.IntList
import box.util.nbt.*
import java.io.Writer
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

// TODO: formatting
@OptIn(ExperimentalSerializationApi::class)
internal class StringTagEncoder(
  private val nbt: Nbt,
  private val writer: Writer,
) : Encoder, CompositeEncoder {
  private val configuration: NbtConfiguration = nbt.configuration
  private lateinit var elementsCounts: IntList

  init {
    if (!configuration.trailingComma) {
      elementsCounts = IntList()
    }
  }

  override val serializersModule: SerializersModule get() = nbt.serializersModule

  override fun encodeNull() {
    TODO()
  }

  override fun encodeBoolean(value: Boolean) {
    when (configuration.booleanEncodingStrategy) {
      BooleanEncodingStrategy.AS_BOOLEAN -> writer.write(value.toString())
      BooleanEncodingStrategy.AS_BYTE    -> encodeByte(if (value) 1 else 0)
    }
  }

  override fun encodeByte(value: Byte) {
    writer.write(value.toString() + configuration.byteTagSuffix.value)
  }

  override fun encodeShort(value: Short) {
    writer.write(value.toString() + configuration.shortTagSuffix.value)
  }

  override fun encodeChar(value: Char) {
    TODO()
  }

  override fun encodeInt(value: Int) {
    writer.write(value.toString())
  }

  override fun encodeLong(value: Long) {
    writer.write(value.toString() + configuration.longTagSuffix.value)
  }

  override fun encodeFloat(value: Float) {
    // TODO: optimization: fast parsing or small size
    writer.write(value.toString() + configuration.floatTagSuffix.value)
  }

  override fun encodeDouble(value: Double) {
    // TODO: optimization: fast parsing or small size
    writer.write(value.toString() + configuration.doubleTagSuffix.value)
  }

  override fun encodeString(value: String) {
    // TODO: configuration: '"' or '\'' or nothing when possible or fast parsing or small size
    // TODO: configuration: disallow some characters (e.g. cannot use '\n' in a command) https://github.com/Mojang/brigadier/pull/90
    writer.write('"'.code)
    value.forEach {
      when (it) {
        '"', '\\' -> writer.write('\\'.code)
      }
      writer.write(it.code)
    }
    writer.write('"'.code)
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
    when (descriptor.kind) {
      StructureKind.CLASS -> {
        if (!configuration.trailingComma) {
          elementsCounts.add(descriptor.elementsCount)
        }
        writer.write('{'.code)
      }
      else                -> TODO()
    }
    return this
  }

  override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
    if (!configuration.trailingComma) {
      elementsCounts.add(collectionSize)
    }
    writer.write('['.code)
    return this
  }

  override fun endStructure(descriptor: SerialDescriptor) {
    when (descriptor.kind) {
      StructureKind.CLASS -> {
        writer.write('}'.code)
      }
      StructureKind.LIST  -> {
        writer.write(']'.code)
      }
      else                -> TODO()
    }
    if (!configuration.trailingComma) {
      elementsCounts.pop()
    }
  }

  @OptIn(ExperimentalContracts::class)
  private inline fun encodeElement(descriptor: SerialDescriptor, index: Int, encode: () -> Unit) {
    contract { callsInPlace(encode, InvocationKind.EXACTLY_ONCE) }
    when (descriptor.kind) {
      StructureKind.CLASS -> {
        val name = descriptor.getElementName(index)
        encodeString(name)
        writer.write(':'.code)
      }
      StructureKind.LIST  -> {}
      else                -> TODO()
    }
    encode()
    if (configuration.trailingComma || index < elementsCounts.last() - 1) {
      writer.write(','.code)
    }
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
