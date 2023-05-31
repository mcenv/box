package mcx.util.remap

class TypeHierarchy {
  private val types: MutableMap<String, MutableSet<String>> = hashMapOf()

  operator fun set(
    thisType: String,
    superType: String,
  ) {
    types.computeIfAbsent(thisType) { hashSetOf() } += superType
  }

  operator fun get(thisType: String): List<String> {
    val superTypes = mutableListOf(thisType)
    val worklist = mutableListOf(thisType)
    while (worklist.isNotEmpty()) {
      types[worklist.removeLast()]?.let {
        superTypes += it
        worklist += it
      }
    }
    return superTypes
  }
}
