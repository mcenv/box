package box.util.egraph

sealed class Pattern {
  data class Var(
    val name: String,
  ) : Pattern() {
    override fun toString(): String {
      return name
    }
  }

  data class Apply(
    val op: String,
    val args: List<Pattern>,
  ) : Pattern() {
    override fun toString(): String {
      return when (args.size) {
        0    -> op
        2    -> "(${args[0]} $op ${args[1]})"
        else -> "$op${args.joinToString(", ", "(", ")")}"
      }
    }
  }
}
