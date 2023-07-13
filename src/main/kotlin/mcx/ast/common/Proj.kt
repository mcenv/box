package mcx.ast.common

sealed class Proj {
  data class I8ArrayOf(
    val index: Int,
  ) : Proj()

  data class I32ArrayOf(
    val index: Int,
  ) : Proj()

  data class I64ArrayOf(
    val index: Int,
  ) : Proj()

  data class VecOf(
    val index: Int,
  ) : Proj()

  data class StructOf(
    val name: String,
  ) : Proj()
}