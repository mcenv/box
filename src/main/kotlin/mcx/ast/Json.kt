package mcx.ast

sealed interface Json {
  data class ObjectOf(
    val members: List<Pair<String, Json>>,
  ) : Json

  data class ArrayOf(
    val elements: List<Json>,
  ) : Json

  data class StringOf(
    val value: String,
  ) : Json

  data class NumberOf(
    val value: Double, // TODO
  ) : Json

  object True : Json

  object False : Json

  object Null : Json

  object Hole : Json
}
