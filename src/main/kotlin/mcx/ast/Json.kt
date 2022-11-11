package mcx.ast

sealed interface Json {
  data class ObjectOf(
    val members: Map<String, Json>,
  ) : Json

  data class ArrayOf(
    val elements: List<Json>,
  ) : Json

  data class StringOf(
    val value: String,
  ) : Json

  data class IntOf(
    val value: Int,
  ) : Json

  data class BoolOf(
    val value: Boolean,
  ) : Json
}
