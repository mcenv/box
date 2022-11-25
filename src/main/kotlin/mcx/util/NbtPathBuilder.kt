package mcx.util

import mcx.ast.Packed.NbtNode
import mcx.ast.Packed.NbtPath
import mcx.util.nbt.Nbt

inline fun nbtPath(
  root: NbtNode.MatchRootObject? = null,
  block: (NbtPathBuilder) -> Unit,
): NbtPath {
  val builder = NbtPathBuilder(root)
  block(builder)
  return builder.build()
}

@Suppress("NOTHING_TO_INLINE")
class NbtPathBuilder(root: NbtNode.MatchRootObject? = null) {
  val nodes: MutableList<NbtNode> = mutableListOf()

  init {
    root?.let { nodes += it }
  }

  inline operator fun invoke(pattern: Nbt.Compound): NbtPathBuilder =
    apply {
      nodes += NbtNode.MatchElement(pattern)
    }

  inline operator fun invoke(): NbtPathBuilder =
    apply {
      nodes += NbtNode.AllElements
    }

  inline operator fun invoke(index: Int): NbtPathBuilder =
    apply {
      nodes += NbtNode.IndexedElement(index)
    }

  inline operator fun invoke(
    name: String,
    pattern: Nbt.Compound,
  ): NbtPathBuilder =
    apply {
      nodes += NbtNode.MatchObject(name, pattern)
    }

  inline operator fun invoke(name: String): NbtPathBuilder =
    apply {
      nodes += NbtNode.CompoundChild(name)
    }

  fun build(): NbtPath =
    NbtPath(nodes)
}
