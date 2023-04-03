package mcx.data

import kotlinx.serialization.Serializable

@Serializable
sealed interface Nbt {
  @Serializable
  data object End : Nbt

  @Serializable
  data class Byte(val value: kotlin.Byte) : Nbt

  @Serializable
  data class Short(val value: kotlin.Short) : Nbt

  @Serializable
  data class Int(val value: kotlin.Int) : Nbt

  @Serializable
  data class Long(val value: kotlin.Long) : Nbt

  @Serializable
  data class Float(val value: kotlin.Float) : Nbt

  @Serializable
  data class Double(val value: kotlin.Double) : Nbt

  @Serializable
  data class ByteArray(val elements: kotlin.collections.List<kotlin.Byte>) : Nbt

  @Serializable
  data class IntArray(val elements: kotlin.collections.List<kotlin.Int>) : Nbt

  @Serializable
  data class LongArray(val elements: kotlin.collections.List<kotlin.Long>) : Nbt

  @Serializable
  data class String(val value: kotlin.String) : Nbt

  @Serializable
  sealed interface List<T : Nbt> : Nbt {
    val elements: kotlin.collections.List<T>

    @Serializable
    object End : Nbt.List<Nbt.End> {
      override val elements: kotlin.collections.List<Nbt.End> get() = emptyList()
    }

    @Serializable
    data class Byte(override val elements: kotlin.collections.List<Nbt.Byte>) : Nbt.List<Nbt.Byte>

    @Serializable
    data class Short(override val elements: kotlin.collections.List<Nbt.Short>) : Nbt.List<Nbt.Short>

    @Serializable
    data class Int(override val elements: kotlin.collections.List<Nbt.Int>) : Nbt.List<Nbt.Int>

    @Serializable
    data class Long(override val elements: kotlin.collections.List<Nbt.Long>) : Nbt.List<Nbt.Long>

    @Serializable
    data class Float(override val elements: kotlin.collections.List<Nbt.Float>) : Nbt.List<Nbt.Float>

    @Serializable
    data class Double(override val elements: kotlin.collections.List<Nbt.Double>) : Nbt.List<Nbt.Double>

    @Serializable
    data class ByteArray(override val elements: kotlin.collections.List<Nbt.ByteArray>) : Nbt.List<Nbt.ByteArray>

    @Serializable
    data class IntArray(override val elements: kotlin.collections.List<Nbt.IntArray>) : Nbt.List<Nbt.IntArray>

    @Serializable
    data class LongArray(override val elements: kotlin.collections.List<Nbt.LongArray>) : Nbt.List<Nbt.LongArray>

    @Serializable
    data class String(override val elements: kotlin.collections.List<Nbt.String>) : Nbt.List<Nbt.String>

    @Serializable
    data class List(override val elements: kotlin.collections.List<Nbt.List<*>>) : Nbt.List<Nbt.List<*>>

    @Serializable
    data class Compound(override val elements: kotlin.collections.List<Nbt.Compound>) : Nbt.List<Nbt.Compound>
  }

  @Serializable
  data class Compound(val elements: Map<kotlin.String, Nbt>) : Nbt
}
