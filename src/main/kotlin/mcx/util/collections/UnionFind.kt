package mcx.util.collections

/**
 * A disjoint-set data structure.
 */
class UnionFind {
  private val parents: IntList = IntList()
  private val ranks: IntList = IntList()

  /**
   * Creates a new disjoint-set.
   */
  fun make(): Id {
    return Id(parents.size).also {
      parents += it.value
      ranks += 0
    }
  }

  /**
   * Finds the representative of the set containing [x].
   */
  fun find(x: Id): Id {
    var self = x
    // Do path halving
    while (self.parent != self) {
      self.parent = self.parent.parent
      self = self.parent
    }
    return self
  }

  /**
   * Unions the sets containing [x] and [y].
   */
  fun union(x: Id, y: Id): Id {
    val xRoot = find(x)
    val yRoot = find(y)

    return if (xRoot == yRoot) {
      xRoot
    } else {
      val xRank = xRoot.rank
      val yRank = yRoot.rank

      if (xRank < yRank) {
        xRoot.parent = yRoot
        yRoot
      } else if (yRank < xRank) {
        yRoot.parent = xRoot
        xRoot
      } else {
        yRoot.parent = xRoot
        xRoot.rank = xRank + 1
        xRoot
      }
    }
  }

  /**
   * Checks if [x] and [y] are in the same set.
   */
  fun equals(x: Id, y: Id): Boolean {
    return find(x) == find(y)
  }

  private inline var Id.parent: Id
    get() {
      return Id(parents[value])
    }
    set(id) {
      parents[value] = id.value
    }

  private inline var Id.rank: Int
    get() {
      return ranks[value]
    }
    set(rank) {
      ranks[value] = rank
    }

  @JvmInline
  value class Id(val value: Int)
}
