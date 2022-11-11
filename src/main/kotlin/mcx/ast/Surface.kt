package mcx.ast

import org.eclipse.lsp4j.Range

object Surface {
  data class Root(
    val module: Location,
    val imports: List<Ranged<Location>>,
    val resources: List<Resource>,
  )

  sealed interface Resource {
    val annotations: List<Annotation>
    val name: String
    val range: Range

    data class JsonResource(
      override val annotations: List<Annotation>,
      val registry: Registry,
      override val name: String,
      val body: Term,
      override val range: Range,
    ) : Resource

    data class Functions(
      override val annotations: List<Annotation>,
      override val name: String,
      val params: List<Pair<String, Type>>,
      val result: Type,
      val body: Term,
      override val range: Range,
    ) : Resource

    data class Hole(
      override val range: Range,
    ) : Resource {
      override val annotations: List<Annotation> get() = throw IllegalStateException()
      override val name: String get() = throw IllegalStateException()
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

    data class Hole(
      override val range: Range,
    ) : Annotation
  }

  sealed interface Type {
    val range: Range

    data class End(
      override val range: Range,
    ) : Type

    data class Bool(
      override val range: Range,
    ) : Type

    data class Byte(
      override val range: Range,
    ) : Type

    data class Short(
      override val range: Range,
    ) : Type

    data class Int(
      override val range: Range,
    ) : Type

    data class Long(
      override val range: Range,
    ) : Type

    data class Float(
      override val range: Range,
    ) : Type

    data class Double(
      override val range: Range,
    ) : Type

    data class String(
      override val range: Range,
    ) : Type

    data class List(
      val element: Type,
      override val range: Range,
    ) : Type

    data class Compound(
      val elements: Map<kotlin.String, Type>,
      override val range: Range,
    ) : Type

    data class Box(
      val element: Type,
      override val range: Range,
    ) : Type

    data class Hole(
      override val range: Range,
    ) : Type
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

    data class ListOf(
      val values: List<Term>,
      override val range: Range,
    ) : Term

    data class CompoundOf(
      val values: List<Pair<Ranged<String>, Term>>,
      override val range: Range,
    ) : Term

    data class BoxOf(
      val value: Term,
      override val range: Range,
    ) : Term

    data class If(
      val condition: Term,
      val thenClause: Term,
      val elseClause: Term,
      override val range: Range,
    ) : Term

    data class Let(
      val name: Ranged<String>,
      val init: Term,
      val body: Term,
      override val range: Range,
    ) : Term

    data class Var(
      val name: String,
      override val range: Range,
    ) : Term

    data class Run(
      val name: String,
      val args: List<Term>,
      override val range: Range,
    ) : Term

    data class Command(
      val value: String,
      override val range: Range,
    ) : Term

    data class Hole(
      override val range: Range,
    ) : Term
  }

  data class Ranged<T>(
    val value: T,
    val range: Range,
  )
}
