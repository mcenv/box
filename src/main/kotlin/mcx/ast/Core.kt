package mcx.ast

object Core {
  data class Module(
    val name: Location,
    val resources: List<Resource>,
  )

  sealed interface Resource {
    val annotations: List<Annotation>
    val name: Location

    data class JsonResource(
      override val annotations: List<Annotation>,
      val registry: Registry,
      override val name: Location,
    ) : Resource {
      lateinit var body: Term
    }

    data class Functions(
      override val annotations: List<Annotation>,
      override val name: Location,
      val params: List<Pair<String, Type>>,
      val result: Type,
    ) : Resource {
      lateinit var body: Term
    }

    object Hole : Resource {
      override val annotations: List<Annotation> get() = throw IllegalStateException()
      override val name: Location get() = throw IllegalStateException()
    }
  }

  sealed interface Annotation {
    object Tick : Annotation

    object Load : Annotation

    object Hole : Annotation
  }

  data class Kind(val arity: Int) {
    companion object {
      val ZERO: Kind = Kind(0)
      val ONE: Kind = Kind(1)
    }
  }

  sealed interface Type {
    val kind: Kind

    object End : Type {
      override val kind: Kind get() = Kind.ZERO
    }

    object Bool : Type {
      override val kind: Kind get() = Kind.ONE
    }

    object Byte : Type {
      override val kind: Kind get() = Kind.ONE
    }

    object Short : Type {
      override val kind: Kind get() = Kind.ONE
    }

    object Int : Type {
      override val kind: Kind get() = Kind.ONE
    }

    object Long : Type {
      override val kind: Kind get() = Kind.ONE
    }

    object Float : Type {
      override val kind: Kind get() = Kind.ONE
    }

    object Double : Type {
      override val kind: Kind get() = Kind.ONE
    }

    object String : Type {
      override val kind: Kind get() = Kind.ONE
    }

    data class List(
      val element: Type,
    ) : Type {
      override val kind: Kind get() = Kind.ONE
    }

    data class Compound(
      val elements: Map<kotlin.String, Type>,
    ) : Type {
      override val kind: Kind get() = Kind.ONE
    }

    data class Box(
      val element: Type,
    ) : Type {
      override val kind: Kind get() = Kind.ONE
    }

    data class Tuple(
      val elements: kotlin.collections.List<Type>,
    ) : Type {
      override val kind: Kind get() = Kind(elements.size)
    }

    object Hole : Type {
      override val kind: Kind get() = Kind.ZERO
    }
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

    data class TupleOf(
      val values: List<Term>,
      override val type: Type,
    ) : Term

    data class If(
      val condition: Term,
      val thenClause: Term,
      val elseClause: Term,
      override val type: Type,
    ) : Term

    data class Let(
      val binder: Pattern,
      val init: Term,
      val body: Term,
      override val type: Type,
    ) : Term

    data class Var(
      val name: String,
      override val type: Type,
    ) : Term

    data class Run(
      val name: Location,
      val args: List<Term>,
      override val type: Type,
    ) : Term

    data class Command(
      val value: String,
      override val type: Type,
    ) : Term

    data class Hole(
      override val type: Type,
    ) : Term
  }

  sealed interface Pattern {
    val type: Type

    data class TupleOf(
      val elements: List<Pattern>,
      override val type: Type,
    ) : Pattern

    data class Var(
      val name: String,
      override val type: Type,
    ) : Pattern

    data class Hole(
      override val type: Type,
    ) : Pattern
  }
}
