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
sealed interface ListTag<out T : Tag> : Tag, List<T> {
  val tags: List<T>
}

@Serializable
@JvmInline
value class EndListTag(override val tags: List<EndTag>) : ListTag<EndTag>, List<EndTag> by tags

@Serializable
@JvmInline
value class StringListTag(override val tags: List<StringTag>) : ListTag<StringTag>, List<StringTag> by tags

@Serializable
@JvmInline
value class CompoundListTag(override val tags: List<CompoundTag>) : ListTag<CompoundTag>, List<CompoundTag> by tags

@Serializable
@JvmInline
value class ByteListTag(override val tags: List<ByteTag>) : ListTag<ByteTag>, List<ByteTag> by tags

@Serializable
@JvmInline
value class ShortListTag(override val tags: List<ShortTag>) : ListTag<ShortTag>, List<ShortTag> by tags

@Serializable
@JvmInline
value class IntListTag(override val tags: List<IntTag>) : ListTag<IntTag>, List<IntTag> by tags

@Serializable
@JvmInline
value class LongListTag(override val tags: List<LongTag>) : ListTag<LongTag>, List<LongTag> by tags

@Serializable
@JvmInline
value class FloatListTag(override val tags: List<FloatTag>) : ListTag<FloatTag>, List<FloatTag> by tags

@Serializable
@JvmInline
value class DoubleListTag(override val tags: List<DoubleTag>) : ListTag<DoubleTag>, List<DoubleTag> by tags

@Serializable
@JvmInline
value class ByteArrayListTag(override val tags: List<ByteArrayTag>) : ListTag<ByteArrayTag>, List<ByteArrayTag> by tags

@Serializable
@JvmInline
value class IntArrayListTag(override val tags: List<IntArrayTag>) : ListTag<IntArrayTag>, List<IntArrayTag> by tags

@Serializable
@JvmInline
value class LongArrayListTag(override val tags: List<LongArrayTag>) : ListTag<LongArrayTag>, List<LongArrayTag> by tags

@Serializable
@JvmInline
value class ListListTag(override val tags: List<ListTag<*>>) : ListTag<ListTag<*>>, List<ListTag<*>> by tags
