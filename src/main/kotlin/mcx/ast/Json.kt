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

  data class ByteOf(
    val value: Byte,
  ) : Json

  data class ShortOf(
    val value: Short,
  ) : Json

  data class IntOf(
    val value: Int,
  ) : Json

  data class LongOf(
    val value: Long,
  ) : Json

  data class FloatOf(
    val value: Float,
  ) : Json

  data class DoubleOf(
    val value: Double,
  ) : Json

  data class BoolOf(
    val value: Boolean,
  ) : Json
}
