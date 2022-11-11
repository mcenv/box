package mcx.ast

object Core {
  data class Root(
    val module: Location,
    val resources: List<Resource0>,
  )

  sealed interface Resource0 {
    val annotations: List<Annotation>
    val module: Location
    val name: String

    data class JsonResource(
      override val annotations: List<Annotation>,
      val registry: Registry,
      override val module: Location,
      override val name: String,
    ) : Resource0 {
      lateinit var body: Term0
    }

    data class Functions(
      override val annotations: List<Annotation>,
      override val module: Location,
      override val name: String,
      val params: List<Pair<String, Type0>>,
      val result: Type0,
    ) : Resource0 {
      lateinit var body: Term0
    }

    object Hole : Resource0 {
      override val annotations: List<Annotation> get() = throw IllegalStateException()
      override val module: Location get() = throw IllegalStateException()
      override val name: String get() = throw IllegalStateException()
    }
  }

  sealed interface Annotation {
    object Tick : Annotation

    object Load : Annotation

    object Hole : Annotation
  }

  sealed interface Type0 {
    object End : Type0

    object Bool : Type0

    object Byte : Type0

    object Short : Type0

    object Int : Type0

    object Long : Type0

    object Float : Type0

    object Double : Type0

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

    data class BoolOf(
      val value: Boolean,
    ) : Term0 {
      override val type: Type0 get() = Type0.Bool
    }

    data class ByteOf(
      val value: Byte,
    ) : Term0 {
      override val type: Type0 get() = Type0.Byte
    }

    data class ShortOf(
      val value: Short,
    ) : Term0 {
      override val type: Type0 get() = Type0.Short
    }

    data class IntOf(
      val value: Int,
    ) : Term0 {
      override val type: Type0 get() = Type0.Int
    }

    data class LongOf(
      val value: Long,
    ) : Term0 {
      override val type: Type0 get() = Type0.Long
    }

    data class FloatOf(
      val value: Float,
    ) : Term0 {
      override val type: Type0 get() = Type0.Float
    }

    data class DoubleOf(
      val value: Double,
    ) : Term0 {
      override val type: Type0 get() = Type0.Double
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

    data class If(
      val condition: Term0,
      val thenClause: Term0,
      val elseClause: Term0,
    ) : Term0 {
      override val type: Type0 get() = thenClause.type
    }

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

    data class Command(
      val value: String,
    ) : Term0 {
      override val type: Type0 get() = Type0.End
    }

    data class Hole(
      override val type: Type0,
    ) : Term0
  }
}
