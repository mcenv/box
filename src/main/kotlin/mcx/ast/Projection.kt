package mcx.ast

sealed class Projection {
  data class StructOf(
    val name: String,
  ) : Projection()
}
