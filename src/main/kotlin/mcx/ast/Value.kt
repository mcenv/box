package mcx.ast

sealed interface Value {
  data class BoolOf(
    val value: Boolean,
  ) : Value

  data class ByteOf(
    val value: Byte,
  ) : Value

  data class ShortOf(
    val value: Short,
  ) : Value

  data class IntOf(
    val value: Int,
  ) : Value

  data class LongOf(
    val value: Long,
  ) : Value

  data class FloatOf(
    val value: Float,
  ) : Value

  data class DoubleOf(
    val value: Double,
  ) : Value

  data class StringOf(
    val value: String,
  ) : Value

  data class ByteArrayOf(
    val elements: List<Lazy<Value>>,
  ) : Value

  data class IntArrayOf(
    val elements: List<Lazy<Value>>,
  ) : Value

  data class LongArrayOf(
    val elements: List<Lazy<Value>>,
  ) : Value

  data class ListOf(
    val elements: List<Lazy<Value>>,
  ) : Value

  data class CompoundOf(
    val elements: Map<String, Lazy<Value>>,
  ) : Value

  data class RefOf(
    val element: Lazy<Value>,
  ) : Value

  data class TupleOf(
    val elements: List<Lazy<Value>>,
  ) : Value

  data class If(
    val condition: Value,
    val thenClause: Lazy<Value>,
    val elseClause: Lazy<Value>,
  ) : Value

  data class Var(
    val name: String,
    val level: Int,
  ) : Value

  data class Is(
    val scrutinee: Value,
    val scrutineer: Core.Pattern,
    val scrutineeType: Core.Type,
  ) : Value

  data class CodeOf(
    val element: Lazy<Value>,
  ) : Value

  data class Splice(
    val element: Value,
    val elementType: Core.Type,
  ) : Value
}
