package mcx.util.nbt

import kotlinx.serialization.Serializable
import java.io.DataOutput

@Serializable
sealed interface Nbt {
  @Serializable
  @JvmInline
  value class Byte(val value: kotlin.Byte) : Nbt

  @Serializable
  @JvmInline
  value class Short(val value: kotlin.Short) : Nbt

  @Serializable
  @JvmInline
  value class Int(val value: kotlin.Int) : Nbt

  @Serializable
  @JvmInline
  value class Long(val value: kotlin.Long) : Nbt

  @Serializable
  @JvmInline
  value class Float(val value: kotlin.Float) : Nbt

  @Serializable
  @JvmInline
  value class Double(val value: kotlin.Double) : Nbt

  @Serializable
  @JvmInline
  value class ByteArray(val value: kotlin.collections.List<kotlin.Byte>) : Nbt

  @Serializable
  @JvmInline
  value class IntArray(val elements: kotlin.collections.List<kotlin.Int>) : Nbt

  @Serializable
  @JvmInline
  value class LongArray(val elements: kotlin.collections.List<kotlin.Long>) : Nbt

  @Serializable
  @JvmInline
  value class String(val value: kotlin.String) : Nbt

  @Serializable
  sealed interface List<T : Nbt> : Nbt {
    val elements: kotlin.collections.List<T>

    @Serializable
    @JvmInline
    value class Byte(override val elements: kotlin.collections.List<Nbt.Byte>) : Nbt.List<Nbt.Byte>

    @Serializable
    @JvmInline
    value class Short(override val elements: kotlin.collections.List<Nbt.Short>) : Nbt.List<Nbt.Short>

    @Serializable
    @JvmInline
    value class Int(override val elements: kotlin.collections.List<Nbt.Int>) : Nbt.List<Nbt.Int>

    @Serializable
    @JvmInline
    value class Long(override val elements: kotlin.collections.List<Nbt.Long>) : Nbt.List<Nbt.Long>

    @Serializable
    @JvmInline
    value class Float(override val elements: kotlin.collections.List<Nbt.Float>) : Nbt.List<Nbt.Float>

    @Serializable
    @JvmInline
    value class Double(override val elements: kotlin.collections.List<Nbt.Double>) : Nbt.List<Nbt.Double>

    @Serializable
    @JvmInline
    value class ByteArray(override val elements: kotlin.collections.List<Nbt.ByteArray>) : Nbt.List<Nbt.ByteArray>

    @Serializable
    @JvmInline
    value class IntArray(override val elements: kotlin.collections.List<Nbt.IntArray>) : Nbt.List<Nbt.IntArray>

    @Serializable
    @JvmInline
    value class LongArray(override val elements: kotlin.collections.List<Nbt.LongArray>) : Nbt.List<Nbt.LongArray>

    @Serializable
    @JvmInline
    value class String(override val elements: kotlin.collections.List<Nbt.String>) : Nbt.List<Nbt.String>

    @Serializable
    @JvmInline
    value class List(override val elements: kotlin.collections.List<Nbt.List<*>>) : Nbt.List<Nbt.List<*>>

    @Serializable
    @JvmInline
    value class Compound(override val elements: kotlin.collections.List<Nbt.Compound>) : Nbt.List<Nbt.Compound>
  }

  @Serializable
  @JvmInline
  value class Compound(val elements: Map<kotlin.String, Nbt>) : Nbt

  companion object {
    inline fun <reified T> encode(
      value: T,
      output: DataOutput,
    ): Unit =
      NbtEncoder(output).run {
        encodeNbtType(NbtType.COMPOUND)
        encodeString("")
        encodeSerializableValue(kotlinx.serialization.serializer(), value)
      }
  }
}
