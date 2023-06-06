package mcx.util.collections

import kotlinx.serialization.Serializable
import kotlin.math.max

// TODO: sort members
@Serializable
class IntList() : MutableList<Int> {
  private var elements: IntArray = IntArray(DEFAULT_CAPACITY)

  override var size: Int = 0
    private set

  val lastIndex: Int
    get() {
      return size - 1
    }

  private fun grow() {
    val oldCapacity = elements.size
    val newCapacity = oldCapacity + max(1, oldCapacity shr 1)
    elements = elements.copyOf(newCapacity)
  }

  override fun add(element: Int): Boolean {
    if (size == elements.size) {
      grow()
    }
    elements[size++] = element
    return true
  }

  fun pop() {
    --size
  }

  override fun get(index: Int): Int {
    return elements[index]
  }

  fun last(): Int {
    return elements[lastIndex]
  }

  override fun set(index: Int, element: Int): Int {
    val old = elements[index]
    elements[index] = element
    return old
  }

  inline fun modifyLast(action: (Int) -> Int): Int {
    val index = lastIndex
    return set(index, action(get(index)))
  }

  override fun contains(element: Int): Boolean {
    return elements.contains(element)
  }

  override fun containsAll(elements: Collection<Int>): Boolean {
    return elements.containsAll(elements)
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

  override fun isEmpty(): Boolean {
    return size == 0
  }

  override fun iterator(): MutableIterator<Int> {
    TODO("Not yet implemented")
  }

  override fun listIterator(): MutableListIterator<Int> {
    TODO("Not yet implemented")
  }

  override fun listIterator(index: Int): MutableListIterator<Int> {
    TODO("Not yet implemented")
  }

  override fun removeAt(index: Int): Int {
    TODO("Not yet implemented")
  }

  override fun retainAll(elements: Collection<Int>): Boolean {
    TODO("Not yet implemented")
  }

  override fun removeAll(elements: Collection<Int>): Boolean {
    TODO("Not yet implemented")
  }

  override fun remove(element: Int): Boolean {
    TODO("Not yet implemented")
  }

  override fun subList(fromIndex: Int, toIndex: Int): IntList {
    TODO("Not yet implemented")
  }

  override fun lastIndexOf(element: Int): Int {
    return elements.lastIndexOf(element)
  }

  override fun indexOf(element: Int): Int {
    return elements.indexOf(element)
  }

  override fun clear() {
    TODO("Not yet implemented")
  }

  override fun addAll(elements: Collection<Int>): Boolean {
    TODO("Not yet implemented")
  }

  override fun addAll(index: Int, elements: Collection<Int>): Boolean {
    TODO("Not yet implemented")
  }

  override fun add(index: Int, element: Int) {
    TODO("Not yet implemented")
  }

  override fun toString(): String {
    return elements.contentToString()
  }

  companion object {
    const val DEFAULT_CAPACITY = 10
  }
}
