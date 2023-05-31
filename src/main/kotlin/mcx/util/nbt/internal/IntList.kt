package mcx.util.nbt.internal

import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
class IntList private constructor(
  private var elements: IntArray,
  private var size: Int,
) {
  val lastIndex: Int
    get() {
      return size - 1
    }

  internal constructor() : this(IntArray(DEFAULT_CAPACITY), 0)

  internal constructor(vararg elements: Int) : this(elements, elements.size)

  private fun grow() {
    val oldCapacity = elements.size
    val newCapacity = oldCapacity + max(1, oldCapacity shr 1)
    elements = elements.copyOf(newCapacity)
  }

  fun add(element: Int): Boolean {
    if (size == elements.size) {
      grow()
    }
    elements[size++] = element
    return true
  }

  fun pop() {
    --size
  }

  fun get(index: Int): Int {
    return elements[index]
  }

  fun last(): Int {
    return elements[lastIndex]
  }

  fun set(index: Int, element: Int): Int {
    val old = elements[index]
    elements[index] = element
    return old
  }

  inline fun modifyLast(action: (Int) -> Int): Int {
    val index = lastIndex
    return set(index, action(get(index)))
  }

  override fun equals(other: Any?): Boolean {
    return when {
      this === other     -> true
      other !is IntList  -> false
      size != other.size -> false
      else               -> (0 until size).all { elements[it] == other.elements[it] }
    }
  }

  override fun hashCode(): Int {
    return elements.contentHashCode()
  }

  override fun toString(): String {
    return elements.contentToString()
  }

  companion object {
    const val DEFAULT_CAPACITY = 10
  }
}
