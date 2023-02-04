package mcx.ast

import org.eclipse.lsp4j.Range

object Core {
  data class Module(
    val name: ModuleLocation,
    val definitions: List<Definition>,
  )

  sealed interface Definition {
    val modifiers: List<Modifier>
    val name: DefinitionLocation

    data class Function(
      override val modifiers: List<Modifier>,
      override val name: DefinitionLocation,
      val typeParams: List<String>,
      val binder: Pattern,
      val result: Core.Type,
      val body: Term?,
    ) : Definition

    data class Type(
      override val modifiers: List<Modifier>,
      override val name: DefinitionLocation,
      val body: Core.Type,
    ) : Definition

    data class Class(
      override val modifiers: List<Modifier>,
      override val name: DefinitionLocation,
      val signatures: List<Signature>,
    ) : Definition {
      sealed interface Signature {
        data class Function(
          val name: String,
          val typeParams: List<String>,
          val binder: Pattern,
          val result: Core.Type,
        ) : Signature
      }
    }

    data class Test(
      override val modifiers: List<Modifier>,
      override val name: DefinitionLocation,
      val body: Term,
    ) : Definition
  }

  sealed interface Kind {
    data class Type(
      val arity: Int,
    ) : Kind {
      companion object {
        val ONE: Kind = Type(1)
      }
    }

    data class Meta(
      val index: Int,
    ) : Kind

    object Hole : Kind
  }

  sealed interface Type {
    val kind: Kind

    data class Bool(
      val value: Boolean?,
    ) : Type {
      override val kind: Kind get() = Kind.Type.ONE

      companion object {
        val SET: Bool = Bool(null)
      }
    }

    data class Byte(
      val value: kotlin.Byte?,
    ) : Type {
      override val kind: Kind get() = Kind.Type.ONE

      companion object {
        val SET: Byte = Byte(null)
      }
    }

    data class Short(
      val value: kotlin.Short?,
    ) : Type {
      override val kind: Kind get() = Kind.Type.ONE

      companion object {
        val SET: Short = Short(null)
      }
    }

    data class Int(
      val value: kotlin.Int?,
    ) : Type {
      override val kind: Kind get() = Kind.Type.ONE

      companion object {
        val SET: Int = Int(null)
      }
    }

    data class Long(
      val value: kotlin.Long?,
    ) : Type {
      override val kind: Kind get() = Kind.Type.ONE

      companion object {
        val SET: Long = Long(null)
      }
    }

    data class Float(
      val value: kotlin.Float?,
    ) : Type {
      override val kind: Kind get() = Kind.Type.ONE

      companion object {
        val SET: Float = Float(null)
      }
    }

    data class Double(
      val value: kotlin.Double?,
    ) : Type {
      override val kind: Kind get() = Kind.Type.ONE

      companion object {
        val SET: Double = Double(null)
      }
    }

    data class String(
      val value: kotlin.String?,
    ) : Type {
      override val kind: Kind get() = Kind.Type.ONE

      companion object {
        val SET: String = String(null)
      }
    }

    object ByteArray : Type {
      override val kind: Kind get() = Kind.Type.ONE
    }

    object IntArray : Type {
      override val kind: Kind get() = Kind.Type.ONE
    }

    object LongArray : Type {
      override val kind: Kind get() = Kind.Type.ONE
    }

    data class List(
      val element: Type,
    ) : Type {
      override val kind: Kind get() = Kind.Type.ONE
    }

    data class Compound(
      val elements: Map<kotlin.String, Type>,
    ) : Type {
      override val kind: Kind get() = Kind.Type.ONE
    }

    data class Ref(
      val element: Type,
    ) : Type {
      override val kind: Kind get() = Kind.Type.ONE
    }

    data class Tuple(
      val elements: kotlin.collections.List<Type>,
      override val kind: Kind,
    ) : Type

    data class Func(
      val param: Type,
      val result: Type,
    ) : Type {
      override val kind: Kind get() = Kind.Type.ONE
    }

    data class Clos(
      val param: Type,
      val result: Type,
    ) : Type {
      override val kind: Kind get() = Kind.Type.ONE
    }

    data class Union(
      val elements: kotlin.collections.List<Type>,
      override val kind: Kind,
    ) : Type {
      companion object {
        val END: Union = Union(emptyList(), Kind.Type.ONE)
      }
    }

    data class Code(
      val element: Type,
    ) : Type {
      override val kind: Kind get() = Kind.Type.ONE
    }

    data class Var(
      val name: kotlin.String,
      val level: kotlin.Int,
    ) : Type {
      override val kind: Kind get() = Kind.Type.ONE
    }

    data class Run(
      val name: DefinitionLocation,
      val body: Type,
      override val kind: Kind,
    ) : Type

    data class Meta(
      val index: kotlin.Int,
      val source: Range,
      override val kind: Kind,
    ) : Type

    object Hole : Type {
      override val kind: Kind get() = Kind.Type.ONE
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

    data class FuncOf(
      val binder: Pattern,
      val body: Term,
      override val type: Type,
    ) : Term

    data class ClosOf(
      val binder: Pattern,
      val body: Term,
      override val type: Type,
    ) : Term

    data class Apply(
      val operator: Term,
      val arg: Term,
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
      val name: DefinitionLocation,
      val typeArgs: List<Type>,
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

    data class Hole(
      override val type: Type,
    ) : Term
  }

  sealed interface Pattern {
    val type: Type

    data class IntOf(
      val value: Int,
      override val type: Type,
    ) : Pattern

    data class IntRangeOf(
      val min: Int,
      val max: Int,
      override val type: Type,
    ) : Pattern

    data class ListOf(
      val elements: List<Pattern>,
      override val type: Type,
    ) : Pattern

    data class CompoundOf(
      val elements: Map<String, Pattern>,
      override val type: Type,
    ) : Pattern

    data class TupleOf(
      val elements: List<Pattern>,
      override val type: Type,
    ) : Pattern

    data class Var(
      val name: String,
      val level: Int,
      override val type: Type,
    ) : Pattern

    data class Drop(
      override val type: Type,
    ) : Pattern

    data class Hole(
      override val type: Type,
    ) : Pattern
  }
}
