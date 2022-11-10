package mcx.ast

import org.eclipse.lsp4j.Range

object Surface {
  data class Root(
    val module: Location,
    val imports: List<Ranged<Location>>,
    val resources: List<Resource0>,
  )

  sealed interface Resource0 {
    val name: String
    val range: Range

    data class JsonResource(
      val registry: Registry,
      override val name: String,
      val body: Json,
      override val range: Range,
    ) : Resource0

    data class Function(
      override val name: String,
      val params: List<Pair<String, Type0>>,
      val result: Type0,
      val body: Term0,
      override val range: Range,
    ) : Resource0

    data class Hole(
      override val range: Range,
    ) : Resource0 {
      override val name: String get() = throw IllegalStateException()
    }
  }

  sealed interface Type0 {
    val range: Range

    data class End(
      override val range: Range,
    ) : Type0

    data class Int(
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

    data class Ref(
      val element: Type0,
      override val range: Range,
    ) : Type0

    data class Hole(
      override val range: Range,
    ) : Type0
  }

  sealed interface Term0 {
    val range: Range

    data class IntOf(
      val value: Int,
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
      val values: Map<String, Term0>,
      override val range: Range,
    ) : Term0

    data class RefOf(
      val value: Term0,
      override val range: Range,
    ) : Term0

    data class Let(
      val name: String,
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

    data class Hole(
      override val range: Range,
    ) : Term0
  }

  sealed interface Json {
    val range: Range

    data class ObjectOf(
      val members: List<Pair<String, Json>>,
      override val range: Range,
    ) : Json

    data class ArrayOf(
      val elements: List<Json>,
      override val range: Range,
    ) : Json

    data class StringOf(
      val value: String,
      override val range: Range,
    ) : Json

    data class NumberOf(
      val value: Double, // TODO
      override val range: Range,
    ) : Json

    data class True(
      override val range: Range,
    ) : Json

    data class False(
      override val range: Range,
    ) : Json

    data class Null(
      override val range: Range,
    ) : Json

    data class Hole(
      override val range: Range,
    ) : Json
  }

  data class Ranged<T>(
    val value: T,
    val range: Range,
  )
}
