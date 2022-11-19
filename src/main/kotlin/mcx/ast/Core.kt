package mcx.ast

object Core {
  data class Module(
    val name: Location,
    val definitions: List<Definition>,
  )

  sealed interface Definition {
    val annotations: List<Annotation>
    val name: Location

    data class Resource(
      override val annotations: List<Annotation>,
      val registry: Registry,
      override val name: Location,
    ) : Definition {
      lateinit var body: Term
    }

    data class Function(
      override val annotations: List<Annotation>,
      override val name: Location,
      val binder: Pattern,
      val param: Type,
      val result: Type,
    ) : Definition {
      lateinit var body: Term
    }

    object Hole : Definition {
      override val annotations: List<Annotation> get() = throw IllegalStateException()
      override val name: Location get() = throw IllegalStateException()
    }
  }

  sealed interface Annotation {
    object Tick : Annotation

    object Load : Annotation

    object NoDrop : Annotation

    object Inline : Annotation

    object Builtin : Annotation

    object Hole : Annotation
  }

  data class Kind(
    val arity: Int,
    val meta: Boolean,
  ) {
    companion object {
      val ZERO: Kind = Kind(0, false)
      val ONE: Kind = Kind(1, false)
      val META: Kind = Kind(1, true)
    }
  }

  sealed interface Type {
    val kind: Kind

    object End : Core.Type {
      override val kind: Kind get() = Kind.ONE
    }

    object Bool : Core.Type {
      override val kind: Kind get() = Kind.ONE
    }

    object Byte : Core.Type {
      override val kind: Kind get() = Kind.ONE
    }

    object Short : Core.Type {
      override val kind: Kind get() = Kind.ONE
    }

    object Int : Core.Type {
      override val kind: Kind get() = Kind.ONE
    }

    object Long : Core.Type {
      override val kind: Kind get() = Kind.ONE
    }

    object Float : Core.Type {
      override val kind: Kind get() = Kind.ONE
    }

    object Double : Core.Type {
      override val kind: Kind get() = Kind.ONE
    }

    object String : Core.Type {
      override val kind: Kind get() = Kind.ONE
    }

    object ByteArray : Core.Type {
      override val kind: Kind get() = Kind.ONE
    }

    object IntArray : Core.Type {
      override val kind: Kind get() = Kind.ONE
    }

    object LongArray : Core.Type {
      override val kind: Kind get() = Kind.ONE
    }

    data class List(
      val element: Core.Type,
    ) : Core.Type {
      override val kind: Kind get() = Kind.ONE
    }

    data class Compound(
      val elements: Map<kotlin.String, Core.Type>,
    ) : Core.Type {
      override val kind: Kind get() = Kind.ONE
    }

    data class Ref(
      val element: Core.Type,
    ) : Core.Type {
      override val kind: Kind get() = Kind.ONE
    }

    data class Tuple(
      val elements: kotlin.collections.List<Core.Type>,
      override val kind: Kind,
    ) : Core.Type

    data class Code(
      val element: Core.Type,
    ) : Core.Type {
      override val kind: Kind get() = Kind.META
    }

    object Type : Core.Type {
      override val kind: Kind get() = Kind.META
    }

    data class Var(
      val name: kotlin.String,
      val level: kotlin.Int,
    ) : Core.Type {
      override val kind: Kind get() = Kind.META
    }

    object Hole : Core.Type {
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

    data class CodeOf(
      val element: Term,
      override val type: Type,
    ) : Term

    data class Splice(
      val element: Term,
      override val type: Type,
    ) : Term

    data class TypeOf(
      val value: Type,
      override val type: Type,
    ) : Term

    data class Hole(
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
      val name: String,
      val level: Int,
      override val annotations: List<Annotation>,
      override val type: Type,
    ) : Pattern

    data class Drop(
      override val annotations: List<Annotation>,
      override val type: Type,
    ) : Pattern

    data class Hole(
      override val annotations: List<Annotation>,
      override val type: Type,
    ) : Pattern
  }
}
