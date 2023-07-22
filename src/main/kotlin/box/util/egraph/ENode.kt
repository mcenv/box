package box.util.egraph

data class ENode(
  val op: String,
  val args: List<EClassId> = emptyList(),
) {
  override fun toString(): String {
    return when (args.size) {
      0    -> op
      2    -> "(${args[0]} $op ${args[1]})"
      else -> "$op${args.joinToString(", ", "(", ")")}"
    }
  }
}
