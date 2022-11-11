package mcx.ast

object Core {
  data class Root(
    val module: Location,
    val resources: List<Resource>,
  )

  sealed interface Resource {
    val annotations: List<Annotation>
    val module: Location
    val name: String

    data class JsonResource(
      override val annotations: List<Annotation>,
      val registry: Registry,
      override val module: Location,
      override val name: String,
    ) : Resource {
      lateinit var body: Term
    }

    data class Functions(
      override val annotations: List<Annotation>,
      override val module: Location,
      override val name: String,
      val params: List<Pair<String, Type>>,
      val result: Type,
    ) : Resource {
      lateinit var body: Term
    }

    object Hole : Resource {
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

    data class List(
      val element: Type,
    ) : Type

    data class Compound(
      val elements: Map<kotlin.String, Type>,
    ) : Type

    data class Box(
      val element: Type,
    ) : Type

    object Hole : Type
  }

  sealed interface Term {
    val type: Type

    data class BoolOf(
      val value: Boolean,
    ) : Term {
      override val type: Type get() = Type.Bool
    }

    data class ByteOf(
      val value: Byte,
    ) : Term {
      override val type: Type get() = Type.Byte
    }

    data class ShortOf(
      val value: Short,
    ) : Term {
      override val type: Type get() = Type.Short
    }

    data class IntOf(
      val value: Int,
    ) : Term {
      override val type: Type get() = Type.Int
    }

    data class LongOf(
      val value: Long,
    ) : Term {
      override val type: Type get() = Type.Long
    }

    data class FloatOf(
      val value: Float,
    ) : Term {
      override val type: Type get() = Type.Float
    }

    data class DoubleOf(
      val value: Double,
    ) : Term {
      override val type: Type get() = Type.Double
    }

    data class StringOf(
      val value: String,
    ) : Term {
      override val type: Type get() = Type.String
    }

    data class ListOf(
      val values: List<Term>,
      override val type: Type,
    ) : Term

    data class CompoundOf(
      val values: Map<String, Term>,
      override val type: Type,
    ) : Term

    data class BoxOf(
      val value: Term,
      override val type: Type,
    ) : Term

    data class If(
      val condition: Term,
      val thenClause: Term,
      val elseClause: Term,
    ) : Term {
      override val type: Type get() = thenClause.type
    }

    data class Let(
      val name: String,
      val init: Term,
      val body: Term,
    ) : Term {
      override val type: Type get() = body.type
    }

    data class Var(
      val name: String,
      override val type: Type,
    ) : Term

    data class Run(
      val module: Location,
      val name: String,
      val args: List<Term>,
      override val type: Type,
    ) : Term

    data class Command(
      val value: String,
    ) : Term {
      override val type: Type get() = Type.End
    }

    data class Hole(
      override val type: Type,
    ) : Term
  }
}
