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
import mcx.util.nbt.NbtConfiguration
import mcx.util.nbt.has

@OptIn(ExperimentalSerializationApi::class)
internal class StringTagDecoder(
  private val nbt: Nbt,
  string: String,
) : Decoder, CompositeDecoder {
  // TODO: optimization: stop using regex
  companion object {
    private val DOUBLE_REGEX = Regex("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?d?", RegexOption.IGNORE_CASE)
    private val FLOAT_REGEX = Regex("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?f", RegexOption.IGNORE_CASE)
    private val BYTE_REGEX = Regex("[-+]?(?:0|[1-9][0-9]*)b", RegexOption.IGNORE_CASE)
    private val LONG_REGEX = Regex("[-+]?(?:0|[1-9][0-9]*)l", RegexOption.IGNORE_CASE)
    private val SHORT_REGEX = Regex("[-+]?(?:0|[1-9][0-9]*)s", RegexOption.IGNORE_CASE)
    private val INT_REGEX = Regex("[-+]?(?:0|[1-9][0-9]*)")
    private val QUOTED_REGEX = Regex("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?[df]?|[-+]?(?:0|[1-9][0-9]*)[bls]?|true|false", RegexOption.IGNORE_CASE)
  }

  private val configuration: NbtConfiguration = nbt.configuration
  private val reader = StringReader(string)
  private val elementIndexes: IntList = IntList()

  override val serializersModule: SerializersModule get() = nbt.serializersModule

  fun canRead(): Boolean {
    return reader.canRead()
  }

  override fun decodeNotNullMark(): Boolean {
    TODO()
  }

  override fun decodeNull(): Nothing? {
    TODO()
  }

  override fun decodeBoolean(): Boolean {
    reader.skipWhitespace()
    val string = reader.readUnquotedString()
    return when {
      string.equals("true", true)  -> true
      string.equals("false", true) -> false
      else                         -> {
        require(BYTE_REGEX.matches(string))
        string.substring(0, string.length - 1).toByte() != 0.toByte()
      }
    }
  }

  override fun decodeByte(): Byte {
    reader.skipWhitespace()
    val string = reader.readUnquotedString()
    require(BYTE_REGEX.matches(string))
    return string.substring(0, string.length - 1).toByte()
  }

  override fun decodeShort(): Short {
    reader.skipWhitespace()
    val string = reader.readUnquotedString()
    require(SHORT_REGEX.matches(string))
    return string.substring(0, string.length - 1).toShort()
  }

  override fun decodeChar(): Char {
    TODO()
  }

  override fun decodeInt(): Int {
    reader.skipWhitespace()
    val string = reader.readUnquotedString()
    require(INT_REGEX.matches(string))
    return string.toInt()
  }

  override fun decodeLong(): Long {
    reader.skipWhitespace()
    val string = reader.readUnquotedString()
    require(LONG_REGEX.matches(string))
    return string.substring(0, string.length - 1).toLong()
  }

  override fun decodeFloat(): Float {
    reader.skipWhitespace()
    val string = reader.readUnquotedString()
    require(FLOAT_REGEX.matches(string))
    return string.substring(0, string.length - 1).toFloat()
  }

  override fun decodeDouble(): Double {
    reader.skipWhitespace()
    val string = reader.readUnquotedString()
    require(DOUBLE_REGEX.matches(string))
    return string.substring(0, string.length - 1).toDouble()
  }

  override fun decodeString(): String {
    reader.skipWhitespace()
    return if (reader.peek().isQuotedStringStart()) {
      reader.readQuotedString()
    } else {
      val string = reader.readUnquotedString()
      require(string.isNotEmpty())
      require(!QUOTED_REGEX.matches(string))
      string
    }
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
    when (descriptor.kind) {
      StructureKind.CLASS -> {
        reader.expect('{')
      }
      StructureKind.LIST  -> {
        reader.expect('[')
      }
      else                -> TODO()
    }
    elementIndexes.add(0)
    reader.skipWhitespace()
    return this
  }

  override fun endStructure(descriptor: SerialDescriptor) {
    elementIndexes.pop()
  }

  override fun decodeSequentially(): Boolean {
    return false
  }

  override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
    return when (descriptor.kind) {
      StructureKind.CLASS -> {
        if (elementIndexes.last() > 0 && reader.peek() != '}') {
          reader.expect(',')
          reader.skipWhitespace()
        }
        when (reader.peek()) {
          '}'  -> {
            reader.skip()
            DECODE_DONE
          }
          else -> {
            val name = reader.readString()
            require(name.isNotEmpty())
            reader.expect(':')
            elementIndexes.modifyLast { 1 }
            descriptor.getElementIndex(name)
          }
        }
      }
      StructureKind.LIST  -> {
        if (elementIndexes.last() > 0 && reader.peek() != ']') {
          reader.expect(',')
          reader.skipWhitespace()
        }
        when (reader.peek()) {
          ']'  -> {
            reader.skip()
            DECODE_DONE
          }
          else -> elementIndexes.modifyLast { it + 1 }
        }
      }
      else                -> TODO()
    }
  }

  override fun decodeCollectionSize(descriptor: SerialDescriptor): Int {
    return -1
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
