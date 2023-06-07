package mcx.util.egraph

sealed class Pattern {
  data class Var(
    val name: String,
  ) : Pattern()

  data class Apply(
    val op: String,
    val args: List<Pattern>,
  ) : Pattern()
}
