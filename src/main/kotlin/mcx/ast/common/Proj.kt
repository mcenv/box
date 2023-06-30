package mcx.ast.common

sealed class Proj {
  data class VecOf(
    val index: Int,
  ) : Proj()

  data class StructOf(
    val name: String,
  ) : Proj()
}
