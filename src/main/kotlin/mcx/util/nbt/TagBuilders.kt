@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(ExperimentalContracts::class)

package mcx.util.nbt

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

inline fun buildCompoundTag(action: CompoundTagBuilder.() -> Unit): CompoundTag {
  contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
  val builder = CompoundTagBuilder()
  builder.action()
  return builder.build()
}

inline fun buildEndListTag(): EndListTag {
  return EndListTag(emptyList())
}

inline fun buildStringListTag(action: StringListTagBuilder.() -> Unit): StringListTag {
  contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
  val builder = StringListTagBuilder()
  builder.action()
  return builder.build()
}

inline fun buildCompoundListTag(action: CompoundListTagBuilder.() -> Unit): CompoundListTag {
  contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
  val builder = CompoundListTagBuilder()
  builder.action()
  return builder.build()
}

inline fun buildByteListTag(action: ByteListTagBuilder.() -> Unit): ByteListTag {
  contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
  val builder = ByteListTagBuilder()
  builder.action()
  return builder.build()
}

inline fun buildShortListTag(action: ShortListTagBuilder.() -> Unit): ShortListTag {
  contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
  val builder = ShortListTagBuilder()
  builder.action()
  return builder.build()
}

inline fun buildIntListTag(action: IntListTagBuilder.() -> Unit): IntListTag {
  contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
  val builder = IntListTagBuilder()
  builder.action()
  return builder.build()
}

inline fun buildLongListTag(action: LongListTagBuilder.() -> Unit): LongListTag {
  contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
  val builder = LongListTagBuilder()
  builder.action()
  return builder.build()
}

inline fun buildFloatListTag(action: FloatListTagBuilder.() -> Unit): FloatListTag {
  contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
  val builder = FloatListTagBuilder()
  builder.action()
  return builder.build()
}

inline fun buildDoubleListTag(action: DoubleListTagBuilder.() -> Unit): DoubleListTag {
  contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
  val builder = DoubleListTagBuilder()
  builder.action()
  return builder.build()
}

inline fun buildByteArrayListTag(action: ByteArrayListTagBuilder.() -> Unit): ByteArrayListTag {
  contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
  val builder = ByteArrayListTagBuilder()
  builder.action()
  return builder.build()
}

inline fun buildIntArrayListTag(action: IntArrayListTagBuilder.() -> Unit): IntArrayListTag {
  contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
  val builder = IntArrayListTagBuilder()
  builder.action()
  return builder.build()
}

inline fun buildLongArrayListTag(action: LongArrayListTagBuilder.() -> Unit): LongArrayListTag {
  contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
  val builder = LongArrayListTagBuilder()
  builder.action()
  return builder.build()
}

inline fun buildListListTag(action: ListListTagBuilder.() -> Unit): ListListTag {
  contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
  val builder = ListListTagBuilder()
  builder.action()
  return builder.build()
}

@TagDslMarker
class CompoundTagBuilder @PublishedApi internal constructor() {
  private val tags: MutableMap<String, Tag> = hashMapOf()

  fun put(name: String, tag: Tag): Tag? {
    return tags.put(name, tag)
  }

  inline fun putCompoundTag(name: String, action: CompoundTagBuilder.() -> Unit): Tag? {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return put(name, buildCompoundTag(action))
  }

  inline fun put(name: String, value: String): Tag? {
    return put(name, StringTag(value))
  }

  inline fun put(name: String, value: Byte): Tag? {
    return put(name, ByteTag(value))
  }

  inline fun put(name: String, value: Short): Tag? {
    return put(name, ShortTag(value))
  }

  inline fun put(name: String, value: Int): Tag? {
    return put(name, IntTag(value))
  }

  inline fun put(name: String, value: Long): Tag? {
    return put(name, LongTag(value))
  }

  inline fun put(name: String, value: Float): Tag? {
    return put(name, FloatTag(value))
  }

  inline fun put(name: String, value: Double): Tag? {
    return put(name, DoubleTag(value))
  }

  @PublishedApi
  internal fun build(): CompoundTag {
    return CompoundTag(tags)
  }
}

@TagDslMarker
class StringListTagBuilder @PublishedApi internal constructor() {
  private val tags: MutableList<StringTag> = arrayListOf()

  fun add(tag: String): Boolean {
    return tags.add(StringTag(tag))
  }

  @PublishedApi
  internal fun build(): StringListTag {
    return StringListTag(tags)
  }
}

@TagDslMarker
class CompoundListTagBuilder @PublishedApi internal constructor() {
  private val tags: MutableList<CompoundTag> = arrayListOf()

  fun add(tag: CompoundTag): Boolean {
    return tags.add(tag)
  }

  inline fun addCompoundTag(action: CompoundTagBuilder.() -> Unit): Boolean {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return add(buildCompoundTag(action))
  }

  @PublishedApi
  internal fun build(): CompoundListTag {
    return CompoundListTag(tags)
  }
}

@TagDslMarker
class ByteListTagBuilder @PublishedApi internal constructor() {
  private val tags: MutableList<ByteTag> = arrayListOf()

  fun add(tag: Byte): Boolean {
    return tags.add(ByteTag(tag))
  }

  @PublishedApi
  internal fun build(): ByteListTag {
    return ByteListTag(tags)
  }
}

@TagDslMarker
class ShortListTagBuilder @PublishedApi internal constructor() {
  private val tags: MutableList<ShortTag> = arrayListOf()

  fun add(tag: Short): Boolean {
    return tags.add(ShortTag(tag))
  }

  @PublishedApi
  internal fun build(): ShortListTag {
    return ShortListTag(tags)
  }
}

@TagDslMarker
class IntListTagBuilder @PublishedApi internal constructor() {
  private val tags: MutableList<IntTag> = arrayListOf()

  fun add(tag: Int): Boolean {
    return tags.add(IntTag(tag))
  }

  @PublishedApi
  internal fun build(): IntListTag {
    return IntListTag(tags)
  }
}

@TagDslMarker
class LongListTagBuilder @PublishedApi internal constructor() {
  private val tags: MutableList<LongTag> = arrayListOf()

  fun add(tag: Long): Boolean {
    return tags.add(LongTag(tag))
  }

  @PublishedApi
  internal fun build(): LongListTag {
    return LongListTag(tags)
  }
}

@TagDslMarker
class FloatListTagBuilder @PublishedApi internal constructor() {
  private val tags: MutableList<FloatTag> = arrayListOf()

  fun add(tag: Float): Boolean {
    return tags.add(FloatTag(tag))
  }

  @PublishedApi
  internal fun build(): FloatListTag {
    return FloatListTag(tags)
  }
}

@TagDslMarker
class DoubleListTagBuilder @PublishedApi internal constructor() {
  private val tags: MutableList<DoubleTag> = arrayListOf()

  fun add(tag: Double): Boolean {
    return tags.add(DoubleTag(tag))
  }

  @PublishedApi
  internal fun build(): DoubleListTag {
    return DoubleListTag(tags)
  }
}

@TagDslMarker
class ByteArrayListTagBuilder @PublishedApi internal constructor() {
  private val tags: MutableList<ByteArrayTag> = arrayListOf()

  fun add(vararg values: Byte): Boolean {
    return tags.add(ByteArrayTag(values.toList()))
  }

  @PublishedApi
  internal fun build(): ByteArrayListTag {
    return ByteArrayListTag(tags)
  }
}

@TagDslMarker
class IntArrayListTagBuilder @PublishedApi internal constructor() {
  private val tags: MutableList<IntArrayTag> = arrayListOf()

  fun add(vararg values: Int): Boolean {
    return tags.add(IntArrayTag(values.toList()))
  }

  @PublishedApi
  internal fun build(): IntArrayListTag {
    return IntArrayListTag(tags)
  }
}

@TagDslMarker
class LongArrayListTagBuilder @PublishedApi internal constructor() {
  private val tags: MutableList<LongArrayTag> = arrayListOf()

  fun add(vararg values: Long): Boolean {
    return tags.add(LongArrayTag(values.toList()))
  }

  @PublishedApi
  internal fun build(): LongArrayListTag {
    return LongArrayListTag(tags)
  }
}

@TagDslMarker
class ListListTagBuilder @PublishedApi internal constructor() {
  private val tags: MutableList<ListTag<*>> = arrayListOf()

  fun add(tag: ListTag<*>): Boolean {
    return tags.add(tag)
  }

  inline fun addEndListTag(): Boolean {
    return add(buildEndListTag())
  }

  inline fun addStringListTag(action: StringListTagBuilder.() -> Unit): Boolean {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return add(buildStringListTag(action))
  }

  inline fun addCompoundListTag(action: CompoundListTagBuilder.() -> Unit): Boolean {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return add(buildCompoundListTag(action))
  }

  inline fun addByteListTag(action: ByteListTagBuilder.() -> Unit): Boolean {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return add(buildByteListTag(action))
  }

  inline fun addShortListTag(action: ShortListTagBuilder.() -> Unit): Boolean {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return add(buildShortListTag(action))
  }

  inline fun addIntListTag(action: IntListTagBuilder.() -> Unit): Boolean {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return add(buildIntListTag(action))
  }

  inline fun addLongListTag(action: LongListTagBuilder.() -> Unit): Boolean {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return add(buildLongListTag(action))
  }

  inline fun addFloatListTag(action: FloatListTagBuilder.() -> Unit): Boolean {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return add(buildFloatListTag(action))
  }

  inline fun addDoubleListTag(action: DoubleListTagBuilder.() -> Unit): Boolean {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return add(buildDoubleListTag(action))
  }

  inline fun addByteArrayListTag(action: ByteArrayListTagBuilder.() -> Unit): Boolean {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return add(buildByteArrayListTag(action))
  }

  inline fun addIntArrayListTag(action: IntArrayListTagBuilder.() -> Unit): Boolean {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return add(buildIntArrayListTag(action))
  }

  inline fun addLongArrayListTag(action: LongArrayListTagBuilder.() -> Unit): Boolean {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return add(buildLongArrayListTag(action))
  }

  inline fun addListListTag(action: ListListTagBuilder.() -> Unit): Boolean {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return add(buildListListTag(action))
  }

  @PublishedApi
  internal fun build(): ListListTag {
    return ListListTag(tags)
  }
}

@DslMarker
internal annotation class TagDslMarker
