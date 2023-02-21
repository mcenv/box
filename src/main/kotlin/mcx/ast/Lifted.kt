package mcx.ast

/**
 * A lifted AST, where all code blocks are lifted to top-level.
 */
object Lifted {
  sealed interface Definition {
    val modifiers: List<Modifier>
    val name: DefinitionLocation

    data class Function(
      override val modifiers: List<Modifier>,
      override val name: DefinitionLocation,
      val binder: Pattern,
      val body: Term,
      val restore: Int?,
    ) : Definition

    data class Test(
      override val modifiers: List<Modifier>,
      override val name: DefinitionLocation,
      val body: Term,
    ) : Definition
  }

  enum class Modifier {
    TICK,
    LOAD,
    NO_DROP,
    BUILTIN,
  }

  sealed interface Type {
    data class Bool(
      val value: Boolean?,
    ) : Type

    data class Byte(
      val value: kotlin.Byte?,
    ) : Type

    data class Short(
      val value: kotlin.Short?,
    ) : Type

    data class Int(
      val value: kotlin.Int?,
    ) : Type

    data class Long(
      val value: kotlin.Long?,
    ) : Type

    data class Float(
      val value: kotlin.Float?,
    ) : Type

    data class Double(
      val value: kotlin.Double?,
    ) : Type

    data class String(
      val value: kotlin.String?,
    ) : Type

    object ByteArray : Type

    object IntArray : Type

    object LongArray : Type

    data class List(
      val element: Type,
    ) : Type

    data class Compound(
      val elements: Map<kotlin.String, Type>,
    ) : Type

    data class Ref(
      val element: Type,
    ) : Type

    data class Tuple(
      val elements: kotlin.collections.List<Type>,
    ) : Type

    data class Func(
      val param: Type,
      val result: Type,
    ) : Type

    data class Clos(
      val param: Type,
      val result: Type,
    ) : Type

    data class Union(
      val elements: kotlin.collections.List<Type>,
    ) : Type

    data class Run(
      val name: DefinitionLocation,
      val body: Lazy<Type>,
    ) : Type
  }

  sealed interface Term {
    val type: Type

    data class BoolOf(
      val value: Boolean,
      override val type: Type,
    ) : Term

    data class ByteOf(
      val value: Byte,
      override val type: Type,
    ) : Term

    data class ShortOf(
      val value: Short,
      override val type: Type,
    ) : Term

    data class IntOf(
      val value: Int,
      override val type: Type,
    ) : Term

    data class LongOf(
      val value: Long,
      override val type: Type,
    ) : Term

    data class FloatOf(
      val value: Float,
      override val type: Type,
    ) : Term

    data class DoubleOf(
      val value: Double,
      override val type: Type,
    ) : Term

    data class StringOf(
      val value: String,
      override val type: Type,
    ) : Term

    data class ByteArrayOf(
      val elements: List<Term>,
      override val type: Type,
    ) : Term

    data class IntArrayOf(
      val elements: List<Term>,
      override val type: Type,
    ) : Term

    data class LongArrayOf(
      val elements: List<Term>,
      override val type: Type,
    ) : Term

    data class ListOf(
      val elements: List<Term>,
      override val type: Type,
    ) : Term

    data class CompoundOf(
      val elements: Map<String, Term>,
      override val type: Type,
    ) : Term

    data class RefOf(
      val element: Term,
      override val type: Type,
    ) : Term

    data class TupleOf(
      val elements: List<Term>,
      override val type: Type,
    ) : Term

    data class FuncOf(
      val tag: Int,
      override val type: Type,
    ) : Term

    data class ClosOf(
      val tag: Int,
      val vars: List<Triple<String, Int, Type>>,
      override val type: Type,
    ) : Term

    data class If(
      val condition: Term,
      val thenName: DefinitionLocation,
      val elseName: DefinitionLocation,
      override val type: Type,
    ) : Term

    data class Let(
      val binder: Pattern,
      val init: Term,
      val body: Term,
      override val type: Type,
    ) : Term

    data class Var(
      val level: Int,
      override val type: Type,
    ) : Term

    data class Run(
      val name: DefinitionLocation,
      val arg: Term,
      override val type: Type,
    ) : Term

    data class Is(
      val scrutinee: Term,
      val scrutineer: Pattern,
      override val type: Type,
    ) : Term

    data class Command(
      val value: String,
      override val type: Type,
    ) : Term
  }

  sealed interface Pattern {
    val type: Type

    data class IntOf(
      val value: Int,
      override val type: Type,
    ) : Pattern

    data class IntRangeOf(
      val min: Int,
      val max: Int,
      override val type: Type,
    ) : Pattern

    data class ListOf(
      val elements: List<Pattern>,
      override val type: Type,
    ) : Pattern

    data class CompoundOf(
      val elements: List<Pair<String, Pattern>>,
      override val type: Type,
    ) : Pattern

    data class TupleOf(
      val elements: List<Pattern>,
      override val type: Type,
    ) : Pattern

    data class Var(
      val level: Int,
      override val type: Type,
    ) : Pattern

    data class Drop(
      override val type: Type,
    ) : Pattern
  }
}
