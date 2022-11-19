package mcx.ast

import org.eclipse.lsp4j.Range

object Surface {
  data class Module(
    val name: Location,
    val imports: List<Ranged<Location>>,
    val definitions: List<Definition>,
  )

  sealed interface Definition {
    val annotations: List<Annotation>
    val name: Ranged<String>
    val range: Range

    data class Resource(
      override val annotations: List<Annotation>,
      val registry: Registry,
      override val name: Ranged<String>,
      val body: Term,
      override val range: Range,
    ) : Definition

    data class Function(
      override val annotations: List<Annotation>,
      override val name: Ranged<String>,
      val binder: Pattern,
      val result: Type,
      val body: Term,
      override val range: Range,
    ) : Definition

    data class Hole(
      override val range: Range,
    ) : Definition {
      override val annotations: List<Annotation> get() = throw IllegalStateException()
      override val name: Ranged<String> get() = throw IllegalStateException()
    }
  }

  sealed interface Annotation {
    val range: Range

    data class Tick(
      override val range: Range,
    ) : Annotation

    data class Load(
      override val range: Range,
    ) : Annotation

    data class NoDrop(
      override val range: Range,
    ) : Annotation

    data class Inline(
      override val range: Range,
    ) : Annotation

    data class Builtin(
      override val range: Range,
    ) : Annotation

    data class Hole(
      override val range: Range,
    ) : Annotation
  }

  sealed interface Type {
    val range: Range

    data class End(
      override val range: Range,
    ) : Surface.Type

    data class Bool(
      override val range: Range,
    ) : Surface.Type

    data class Byte(
      override val range: Range,
    ) : Surface.Type

    data class Short(
      override val range: Range,
    ) : Surface.Type

    data class Int(
      override val range: Range,
    ) : Surface.Type

    data class Long(
      override val range: Range,
    ) : Surface.Type

    data class Float(
      override val range: Range,
    ) : Surface.Type

    data class Double(
      override val range: Range,
    ) : Surface.Type

    data class String(
      override val range: Range,
    ) : Surface.Type

    data class ByteArray(
      override val range: Range,
    ) : Surface.Type

    data class IntArray(
      override val range: Range,
    ) : Surface.Type

    data class LongArray(
      override val range: Range,
    ) : Surface.Type

    data class List(
      val element: Surface.Type,
      override val range: Range,
    ) : Surface.Type

    data class Compound(
      val elements: Map<kotlin.String, Surface.Type>,
      override val range: Range,
    ) : Surface.Type

    data class Ref(
      val element: Surface.Type,
      override val range: Range,
    ) : Surface.Type

    data class Tuple(
      val elements: kotlin.collections.List<Surface.Type>,
      override val range: Range,
    ) : Surface.Type

    data class Code(
      val element: Surface.Type,
      override val range: Range,
    ) : Surface.Type

    data class Type(
      override val range: Range,
    ) : Surface.Type

    data class Var(
      val name: kotlin.String,
      override val range: Range,
    ) : Surface.Type

    data class Hole(
      override val range: Range,
    ) : Surface.Type
  }

  sealed interface Term {
    val range: Range

    data class BoolOf(
      val value: Boolean,
      override val range: Range,
    ) : Term

    data class ByteOf(
      val value: Byte,
      override val range: Range,
    ) : Term

    data class ShortOf(
      val value: Short,
      override val range: Range,
    ) : Term

    data class IntOf(
      val value: Int,
      override val range: Range,
    ) : Term

    data class LongOf(
      val value: Long,
      override val range: Range,
    ) : Term

    data class FloatOf(
      val value: Float,
      override val range: Range,
    ) : Term

    data class DoubleOf(
      val value: Double,
      override val range: Range,
    ) : Term

    data class StringOf(
      val value: String,
      override val range: Range,
    ) : Term

    data class ByteArrayOf(
      val elements: List<Term>,
      override val range: Range,
    ) : Term

    data class IntArrayOf(
      val elements: List<Term>,
      override val range: Range,
    ) : Term

    data class LongArrayOf(
      val elements: List<Term>,
      override val range: Range,
    ) : Term

    data class ListOf(
      val elements: List<Term>,
      override val range: Range,
    ) : Term

    data class CompoundOf(
      val elements: List<Pair<Ranged<String>, Term>>,
      override val range: Range,
    ) : Term

    data class RefOf(
      val element: Term,
      override val range: Range,
    ) : Term

    data class TupleOf(
      val elements: List<Term>,
      override val range: Range,
    ) : Term

    data class If(
      val condition: Term,
      val thenClause: Term,
      val elseClause: Term,
      override val range: Range,
    ) : Term

    data class Let(
      val binder: Pattern,
      val init: Term,
      val body: Term,
      override val range: Range,
    ) : Term

    data class Var(
      val name: String,
      override val range: Range,
    ) : Term

    data class Run(
      val name: Ranged<Location>,
      val arg: Term,
      override val range: Range,
    ) : Term

    data class Is(
      val scrutinee: Term,
      val scrutineer: Pattern,
      override val range: Range,
    ) : Term

    data class CodeOf(
      val element: Term,
      override val range: Range,
    ) : Term

    data class Splice(
      val element: Term,
      override val range: Range,
    ) : Term

    data class TypeOf(
      val value: Type,
      override val range: Range,
    ) : Term

    data class Hole(
      override val range: Range,
    ) : Term
  }

  sealed interface Pattern {
    val annotations: List<Annotation>
    val range: Range

    data class IntOf(
      val value: Int,
      override val annotations: List<Annotation>,
      override val range: Range,
    ) : Pattern

    data class IntRangeOf(
      val min: Int,
      val max: Int,
      override val annotations: List<Annotation>,
      override val range: Range,
    ) : Pattern

    data class TupleOf(
      val elements: List<Pattern>,
      override val annotations: List<Annotation>,
      override val range: Range,
    ) : Pattern

    data class Var(
      val name: String,
      override val annotations: List<Annotation>,
      override val range: Range,
    ) : Pattern

    data class Drop(
      override val annotations: List<Annotation>,
      override val range: Range,
    ) : Pattern

    data class Anno(
      val element: Pattern,
      val type: Type,
      override val annotations: List<Annotation>,
      override val range: Range,
    ) : Pattern

    data class Hole(
      override val annotations: List<Annotation>,
      override val range: Range,
    ) : Pattern
  }

  data class Ranged<T>(
    val value: T,
    val range: Range,
  )
}
