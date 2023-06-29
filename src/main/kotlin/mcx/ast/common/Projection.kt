package mcx.ast.common

sealed class Projection {
  data class StructOf(
    val name: String,
  ) : Projection()

  data object RefOf : Projection()
}
