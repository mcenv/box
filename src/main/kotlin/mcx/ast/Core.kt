package mcx.ast

object Core {
  data class Root(
    val module: Location,
    val resources: List<Resource0>,
  )

  sealed interface Resource0 {
    val module: Location
    val name: String

    data class JsonResource(
      val registry: Registry,
      override val module: Location,
      override val name: String,
      val body: Json,
    ) : Resource0

    data class Function(
      override val module: Location,
      override val name: String,
      val params: List<Pair<String, Type0>>,
      val result: Type0,
      val body: Term0,
    ) : Resource0

    object Hole : Resource0 {
      override val module: Location get() = throw IllegalStateException()
      override val name: String get() = throw IllegalStateException()
    }
  }

  sealed interface Type0 {
    object End : Type0

    object Int : Type0

    object String : Type0

    data class List(
      val element: Type0,
    ) : Type0

    data class Compound(
      val elements: Map<kotlin.String, Type0>,
    ) : Type0

    data class Box(
      val element: Type0,
    ) : Type0

    object Hole : Type0
  }

  sealed interface Term0 {
    val type: Type0

    data class IntOf(
      val value: Int,
    ) : Term0 {
      override val type: Type0 get() = Type0.Int
    }

    data class StringOf(
      val value: String,
    ) : Term0 {
      override val type: Type0 get() = Type0.String
    }

    data class ListOf(
      val values: List<Term0>,
      override val type: Type0,
    ) : Term0

    data class CompoundOf(
      val values: Map<String, Term0>,
      override val type: Type0,
    ) : Term0

    data class BoxOf(
      val value: Term0,
      override val type: Type0,
    ) : Term0

    data class Let(
      val name: String,
      val init: Term0,
      val body: Term0,
    ) : Term0 {
      override val type: Type0 get() = body.type
    }

    data class Var(
      val name: String,
      override val type: Type0,
    ) : Term0

    data class Run(
      val module: Location,
      val name: String,
      val args: List<Term0>,
      override val type: Type0,
    ) : Term0

    data class Hole(
      override val type: Type0,
    ) : Term0
  }
}
