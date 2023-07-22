package box.util.collections

/**
 * A disjoint-set data structure.
 */
class UnionFind {
  private val parents: IntList = IntList()
  private val ranks: IntList = IntList()

  /**
   * Creates a new disjoint-set.
   */
  fun make(): Int {
    return parents.size.also {
      parents += it
      ranks += 0
    }
  }

  /**
   * Finds the representative of the set containing [x].
   */
  fun find(x: Int): Int {
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
  fun union(x: Int, y: Int): Int {
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
  fun equals(x: Int, y: Int): Boolean {
    return find(x) == find(y)
  }

  private inline var Int.parent: Int
    get() {
      return parents[this]
    }
    set(id) {
      parents[this] = id
    }

  private inline var Int.rank: Int
    get() {
      return ranks[this]
    }
    set(rank) {
      ranks[this] = rank
    }
}
