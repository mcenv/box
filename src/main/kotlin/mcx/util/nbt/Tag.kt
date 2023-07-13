package mcx.util.nbt

import kotlinx.serialization.Serializable

@Serializable
sealed interface Tag

@Serializable
class EndTag(val value: Nothing) : Tag

// TODO: https://bugs.mojang.com/browse/MC-259282
@Serializable
@JvmInline
value class StringTag(val value: String) : Tag

@Serializable
@JvmInline
value class CompoundTag(private val tags: Map<String, Tag>) : Tag, Map<String, Tag> by tags

@Serializable
@JvmInline
value class ByteTag(val value: Byte) : Tag

@Serializable
@JvmInline
value class ShortTag(val value: Short) : Tag

@Serializable
@JvmInline
value class IntTag(val value: Int) : Tag

@Serializable
@JvmInline
value class LongTag(val value: Long) : Tag

// TODO: https://bugs.mojang.com/browse/MC-171881
@Serializable
@JvmInline
value class FloatTag(val value: Float) : Tag

// TODO: https://bugs.mojang.com/browse/MC-171881
@Serializable
@JvmInline
value class DoubleTag(val value: Double) : Tag

@Serializable
@JvmInline
value class ByteArrayTag internal constructor(private val values: List<Byte>) : Tag, List<Byte> by values

@Serializable
@JvmInline
value class IntArrayTag internal constructor(private val values: List<Int>) : Tag, List<Int> by values

@Serializable
@JvmInline
value class LongArrayTag internal constructor(private val values: List<Long>) : Tag, List<Long> by values

@Serializable
@JvmInline
value class ListTag(private val tags: List<Tag>) : Tag, List<Tag> by tags
