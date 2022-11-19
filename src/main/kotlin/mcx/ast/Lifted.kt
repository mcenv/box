package mcx.ast

/**
 * A lifted AST, where all code blocks are lifted to top-level.
 */
object Lifted {
  sealed interface Definition {
    val annotations: List<Annotation>
    val name: Location

    data class Resource(
      override val annotations: List<Annotation>,
      val registry: Registry,
      override val name: Location,
      val body: Term,
    ) : Definition

    data class Function(
      override val annotations: List<Annotation>,
      override val name: Location,
      val binder: Pattern,
      val param: Type,
      val result: Type,
      val body: Term,
    ) : Definition

    data class Builtin(
      override val annotations: List<Annotation>,
      override val name: Location,
    ) : Definition
  }

  sealed interface Annotation {
    object Tick : Annotation

    object Load : Annotation

    object NoDrop : Annotation

    object Inline : Annotation

    object Builtin : Annotation
  }

  sealed interface Type {
    object End : Type

    object Bool : Type

    object Byte : Type

    object Short : Type

    object Int : Type

    object Long : Type

    object Float : Type

    object Double : Type

    object String : Type

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

    data class If(
      val condition: Term,
      val thenName: Location,
      val elseName: Location,
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
      val name: Location,
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
    val annotations: List<Annotation>
    val type: Type

    data class IntOf(
      val value: Int,
      override val annotations: List<Annotation>,
      override val type: Type,
    ) : Pattern

    data class IntRangeOf(
      val min: Int,
      val max: Int,
      override val annotations: List<Annotation>,
      override val type: Type,
    ) : Pattern

    data class TupleOf(
      val elements: List<Pattern>,
      override val annotations: List<Annotation>,
      override val type: Type,
    ) : Pattern

    data class Var(
      val level: Int,
      override val annotations: List<Annotation>,
      override val type: Type,
    ) : Pattern

    data class Drop(
      override val annotations: List<Annotation>,
      override val type: Type,
    ) : Pattern
  }
}
