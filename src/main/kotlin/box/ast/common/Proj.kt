package box.ast.common

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

  data class ListOf(
    val index: Int,
  ) : Proj()

  data class CompoundOf(
    val name: String,
  ) : Proj()
}
