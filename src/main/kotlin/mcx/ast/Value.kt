package mcx.ast

sealed interface Value {
  @JvmInline
  value class BoolOf(
    val value: Boolean,
  ) : Value

  @JvmInline
  value class ByteOf(
    val value: Byte,
  ) : Value

  @JvmInline
  value class ShortOf(
    val value: Short,
  ) : Value

  @JvmInline
  value class IntOf(
    val value: Int,
  ) : Value

  @JvmInline
  value class LongOf(
    val value: Long,
  ) : Value

  @JvmInline
  value class FloatOf(
    val value: Float,
  ) : Value

  @JvmInline
  value class DoubleOf(
    val value: Double,
  ) : Value

  @JvmInline
  value class StringOf(
    val value: String,
  ) : Value

  @JvmInline
  value class ByteArrayOf(
    val elements: List<Lazy<Value>>,
  ) : Value

  @JvmInline
  value class IntArrayOf(
    val elements: List<Lazy<Value>>,
  ) : Value

  @JvmInline
  value class LongArrayOf(
    val elements: List<Lazy<Value>>,
  ) : Value

  @JvmInline
  value class ListOf(
    val elements: List<Lazy<Value>>,
  ) : Value

  @JvmInline
  value class CompoundOf(
    val elements: Map<String, Lazy<Value>>,
  ) : Value

  @JvmInline
  value class RefOf(
    val element: Lazy<Value>,
  ) : Value

  @JvmInline
  value class TupleOf(
    val elements: List<Lazy<Value>>,
  ) : Value

  class FunOf(
    val binder: Core.Pattern,
    val body: Core.Term,
  ) : Value

  class Apply(
    val operator: Value,
    val arg: Lazy<Value>,
    val operatorType: Core.Type,
  ) : Value

  class If(
    val condition: Value,
    val thenClause: Lazy<Value>,
    val elseClause: Lazy<Value>,
  ) : Value

  class Let(
    val binder: Core.Pattern,
    val init: Lazy<Value>,
    val body: Lazy<Value>,
    val type: Core.Type,
  ) : Value

  class Var(
    val name: String,
    val level: Int,
  ) : Value

  class Run(
    val name: DefinitionLocation,
    val typeArgs: List<Core.Type>,
    val arg: Value,
  ) : Value

  class Is(
    val scrutinee: Value,
    val scrutineer: Core.Pattern,
    val scrutineeType: Core.Type,
  ) : Value

  class Command(
    val value: String,
  ) : Value

  @JvmInline
  value class CodeOf(
    val element: Lazy<Value>,
  ) : Value

  class Splice(
    val element: Value,
    val elementType: Core.Type,
  ) : Value

  @JvmInline
  value class Hole(
    val type: Core.Type,
  ) : Value
}
