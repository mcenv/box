package box.util.nbt

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun buildCompoundTag(action: CompoundTagBuilder.() -> Unit): CompoundTag {
  contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
  val builder = CompoundTagBuilder()
  builder.action()
  return builder.build()
}

@OptIn(ExperimentalContracts::class)
inline fun buildListTag(action: ListTagBuilder.() -> Unit): ListTag {
  contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
  val builder = ListTagBuilder()
  builder.action()
  return builder.build()
}

@TagDslMarker
class CompoundTagBuilder @PublishedApi internal constructor() {
  private val tags: MutableMap<String, Tag> = hashMapOf()

  fun put(name: String, tag: Tag): Tag? {
    return tags.put(name, tag)
  }

  @OptIn(ExperimentalContracts::class)
  inline fun putCompoundTag(name: String, action: CompoundTagBuilder.() -> Unit): Tag? {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return put(name, buildCompoundTag(action))
  }

  @Suppress("NOTHING_TO_INLINE")
  inline fun put(name: String, value: String): Tag? {
    return put(name, StringTag(value))
  }

  @Suppress("NOTHING_TO_INLINE")
  inline fun put(name: String, value: Byte): Tag? {
    return put(name, ByteTag(value))
  }

  @Suppress("NOTHING_TO_INLINE")
  inline fun put(name: String, value: Short): Tag? {
    return put(name, ShortTag(value))
  }

  @Suppress("NOTHING_TO_INLINE")
  inline fun put(name: String, value: Int): Tag? {
    return put(name, IntTag(value))
  }

  @Suppress("NOTHING_TO_INLINE")
  inline fun put(name: String, value: Long): Tag? {
    return put(name, LongTag(value))
  }

  @Suppress("NOTHING_TO_INLINE")
  inline fun put(name: String, value: Float): Tag? {
    return put(name, FloatTag(value))
  }

  @Suppress("NOTHING_TO_INLINE")
  inline fun put(name: String, value: Double): Tag? {
    return put(name, DoubleTag(value))
  }

  @PublishedApi
  internal fun build(): CompoundTag {
    return CompoundTag(tags)
  }
}

@TagDslMarker
class ListTagBuilder @PublishedApi internal constructor() {
  private val tags: MutableList<Tag> = arrayListOf()

  fun add(tag: Tag): Boolean {
    return tags.add(tag)
  }

  @PublishedApi
  internal fun build(): ListTag {
    return ListTag(tags)
  }
}

@DslMarker
internal annotation class TagDslMarker
