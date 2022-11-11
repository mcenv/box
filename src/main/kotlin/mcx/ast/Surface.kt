package mcx.ast

import org.eclipse.lsp4j.Range

object Surface {
  data class Root(
    val module: Location,
    val imports: List<Ranged<Location>>,
    val resources: List<Resource0>,
  )

  sealed interface Resource0 {
    val annotations: List<Annotation>
    val name: String
    val range: Range

    data class JsonResource(
      override val annotations: List<Annotation>,
      val registry: Registry,
      override val name: String,
      val body: Term0,
      override val range: Range,
    ) : Resource0

    data class Functions(
      override val annotations: List<Annotation>,
      override val name: String,
      val params: List<Pair<String, Type0>>,
      val result: Type0,
      val body: Term0,
      override val range: Range,
    ) : Resource0

    data class Hole(
      override val range: Range,
    ) : Resource0 {
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

  sealed interface Type0 {
    val range: Range

    data class End(
      override val range: Range,
    ) : Type0

    data class Bool(
      override val range: Range,
    ) : Type0

    data class Byte(
      override val range: Range,
    ) : Type0

    data class Short(
      override val range: Range,
    ) : Type0

    data class Int(
      override val range: Range,
    ) : Type0

    data class Long(
      override val range: Range,
    ) : Type0

    data class Float(
      override val range: Range,
    ) : Type0

    data class Double(
      override val range: Range,
    ) : Type0

    data class String(
      override val range: Range,
    ) : Type0

    data class List(
      val element: Type0,
      override val range: Range,
    ) : Type0

    data class Compound(
      val elements: Map<kotlin.String, Type0>,
      override val range: Range,
    ) : Type0

    data class Box(
      val element: Type0,
      override val range: Range,
    ) : Type0

    data class Hole(
      override val range: Range,
    ) : Type0
  }

  sealed interface Term0 {
    val range: Range

    data class BoolOf(
      val value: Boolean,
      override val range: Range,
    ) : Term0

    data class ByteOf(
      val value: Byte,
      override val range: Range,
    ) : Term0

    data class ShortOf(
      val value: Short,
      override val range: Range,
    ) : Term0

    data class IntOf(
      val value: Int,
      override val range: Range,
    ) : Term0

    data class LongOf(
      val value: Long,
      override val range: Range,
    ) : Term0

    data class FloatOf(
      val value: Float,
      override val range: Range,
    ) : Term0

    data class DoubleOf(
      val value: Double,
      override val range: Range,
    ) : Term0

    data class StringOf(
      val value: String,
      override val range: Range,
    ) : Term0

    data class ListOf(
      val values: List<Term0>,
      override val range: Range,
    ) : Term0

    data class CompoundOf(
      val values: List<Pair<Ranged<String>, Term0>>,
      override val range: Range,
    ) : Term0

    data class BoxOf(
      val value: Term0,
      override val range: Range,
    ) : Term0

    data class Let(
      val name: Ranged<String>,
      val init: Term0,
      val body: Term0,
      override val range: Range,
    ) : Term0

    data class Var(
      val name: String,
      override val range: Range,
    ) : Term0

    data class Run(
      val name: String,
      val args: List<Term0>,
      override val range: Range,
    ) : Term0

    data class Command(
      val value: String,
      override val range: Range,
    ) : Term0

    data class Hole(
      override val range: Range,
    ) : Term0
  }

  data class Ranged<T>(
    val value: T,
    val range: Range,
  )
}
