package mcx.util.collections

class UnionFind {
  private val parents: IntList = IntList()
  private val ranks: IntList = IntList()

  fun make(): Int {
    return parents.size.also {
      parents += it
      ranks += 0
    }
  }

  fun find(x: Int): Int {
    var self = x
    // Do path halving
    while (self.parent != self) {
      self.parent = self.parent.parent
      self = self.parent
    }
    return self
  }

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

  fun equals(x: Int, y: Int): Boolean {
    return find(x) == find(y)
  }

  private inline var Int.parent: Int
    get() {
      return parents[this]
    }
    set(value) {
      parents[this] = value
    }

  private inline var Int.rank: Int
    get() {
      return ranks[this]
    }
    set(value) {
      ranks[this] = value
    }
}
