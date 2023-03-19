package mcx.ast

import mcx.data.NbtType
import org.eclipse.lsp4j.Range

object Core {
  data class Module(
    val name: ModuleLocation,
    val definitions: List<Definition>,
  )

  sealed interface Definition {
    val modifiers: List<Modifier>
    val name: DefinitionLocation

    data class Def(
      override val modifiers: List<Modifier>,
      override val name: DefinitionLocation,
      val type: Term,
      val body: Term?,
    ) : Definition
  }

  /**
   * A well-typed term.
   */
  sealed interface Term {
    val kind: Kind

    object Tag : Term {
      override val kind: Kind get() = Kind.END
      override fun toString(): kotlin.String = "Tag"
    }

    data class TagOf(
      val value: NbtType,
    ) : Term {
      override val kind: Kind get() = Kind.BYTE
    }

    data class Type(
      val element: Term,
      override val kind: Kind,
    ) : Term

    object Bool : Term {
      override val kind: Kind get() = Kind.TYPE_BYTE
      override fun toString(): kotlin.String = "Bool"
    }

    data class BoolOf(
      val value: Boolean,
    ) : Term {
      override val kind: Kind get() = Kind.BYTE
    }

    data class If(
      val condition: Term,
      val thenBranch: Term,
      val elseBranch: Term,
    ) : Term {
      override val kind: Kind = thenBranch.kind
    }

    data class Is(
      val scrutinee: Term,
      val scrutineer: Pattern,
    ) : Term {
      override val kind: Kind get() = Kind.BYTE
    }

    object Byte : Term {
      override val kind: Kind get() = Kind.TYPE_BYTE
      override fun toString(): kotlin.String = "Byte"
    }

    data class ByteOf(
      val value: kotlin.Byte,
    ) : Term {
      override val kind: Kind get() = Kind.BYTE
    }

    object Short : Term {
      override val kind: Kind get() = Kind.TYPE_SHORT
      override fun toString(): kotlin.String = "Short"
    }

    data class ShortOf(
      val value: kotlin.Short,
    ) : Term {
      override val kind: Kind get() = Kind.SHORT
    }

    object Int : Term {
      override val kind: Kind get() = Kind.TYPE_INT
      override fun toString(): kotlin.String = "Int"
    }

    data class IntOf(
      val value: kotlin.Int,
    ) : Term {
      override val kind: Kind get() = Kind.INT
    }

    object Long : Term {
      override val kind: Kind get() = Kind.TYPE_LONG
      override fun toString(): kotlin.String = "Long"
    }

    data class LongOf(
      val value: kotlin.Long,
    ) : Term {
      override val kind: Kind get() = Kind.LONG
    }

    object Float : Term {
      override val kind: Kind get() = Kind.TYPE_FLOAT
      override fun toString(): kotlin.String = "Float"
    }

    data class FloatOf(
      val value: kotlin.Float,
    ) : Term {
      override val kind: Kind get() = Kind.FLOAT
    }

    object Double : Term {
      override val kind: Kind get() = Kind.TYPE_DOUBLE
      override fun toString(): kotlin.String = "Double"
    }

    data class DoubleOf(
      val value: kotlin.Double,
    ) : Term {
      override val kind: Kind get() = Kind.DOUBLE
    }

    object String : Term {
      override val kind: Kind get() = Kind.TYPE_STRING
      override fun toString(): kotlin.String = "String"
    }

    data class StringOf(
      val value: kotlin.String,
    ) : Term {
      override val kind: Kind get() = Kind.STRING
    }

    object ByteArray : Term {
      override val kind: Kind get() = Kind.TYPE_BYTE_ARRAY
      override fun toString(): kotlin.String = "ByteArray"
    }

    data class ByteArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term {
      override val kind: Kind get() = Kind.BYTE_ARRAY
    }

    object IntArray : Term {
      override val kind: Kind get() = Kind.TYPE_INT_ARRAY
      override fun toString(): kotlin.String = "IntArray"
    }

    data class IntArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term {
      override val kind: Kind get() = Kind.INT_ARRAY
    }

    object LongArray : Term {
      override val kind: Kind get() = Kind.TYPE_LONG_ARRAY
      override fun toString(): kotlin.String = "LongArray"
    }

    data class LongArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term {
      override val kind: Kind get() = Kind.LONG_ARRAY
    }

    data class List(
      val element: Term,
    ) : Term {
      override val kind: Kind get() = Kind.TYPE_LIST
    }

    data class ListOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term {
      override val kind: Kind get() = Kind.LIST
    }

    data class Compound(
      val elements: Map<kotlin.String, Term>,
    ) : Term {
      override val kind: Kind get() = Kind.TYPE_COMPOUND
    }

    data class CompoundOf(
      val elements: Map<kotlin.String, Term>,
    ) : Term {
      override val kind: Kind get() = Kind.COMPOUND
    }

    data class Union(
      val elements: kotlin.collections.List<Term>,
    ) : Term {
      override val kind: Kind = elements.firstOrNull()?.kind ?: Kind.TYPE_END
    }

    data class Func(
      val params: kotlin.collections.List<Pair<Pattern, Term>>,
      val result: Term,
    ) : Term {
      override val kind: Kind get() = Kind.TYPE_COMPOUND
    }

    data class FuncOf(
      val params: kotlin.collections.List<Pattern>,
      val result: Term,
    ) : Term {
      override val kind: Kind get() = Kind.COMPOUND
    }

    data class Apply(
      val func: Term,
      val args: kotlin.collections.List<Term>,
      override val kind: Kind,
    ) : Term

    data class Let(
      val binder: Pattern,
      val init: Term,
      val body: Term,
    ) : Term {
      override val kind: Kind = body.kind
    }

    data class Var(
      val name: kotlin.String,
      val idx: Idx,
      override val kind: Kind,
    ) : Term

    data class Def(
      val name: DefinitionLocation,
      val body: Term?,
      override val kind: Kind,
    ) : Term

    data class Meta(
      val index: kotlin.Int,
      val source: Range,
      override val kind: Kind,
    ) : Term

    object Hole : Term {
      override val kind: Kind get() = Kind.Hole
      override fun toString(): kotlin.String = "Hole"
    }
  }

  /**
   * A well-typed pattern.
   */
  sealed interface Pattern {
    val kind: Kind

    data class IntOf(
      val value: Int,
    ) : Pattern {
      override val kind: Kind get() = Kind.INT
    }

    data class CompoundOf(
      val elements: List<Pair<String, Pattern>>,
    ) : Pattern {
      override val kind: Kind get() = Kind.COMPOUND
    }

    data class Var(
      val name: String,
      override val kind: Kind,
    ) : Pattern

    data class Drop(
      override val kind: Kind,
    ) : Pattern {
      override fun toString(): String = "Drop"
    }

    object Hole : Pattern {
      override val kind: Kind get() = Kind.Hole
      override fun toString(): String = "Hole"
    }
  }

  sealed interface Kind {
    data class Type(
      val element: Kind,
    ) : Kind

    data class Tag(
      val tag: NbtType,
    ) : Kind

    data class Meta(
      val index: Int,
      val source: Range,
    ) : Kind

    object Hole : Kind {
      override fun toString(): String = "Hole"
    }

    companion object {
      val END: Kind = Tag(NbtType.END)
      val BYTE: Kind = Tag(NbtType.BYTE)
      val SHORT: Kind = Tag(NbtType.SHORT)
      val INT: Kind = Tag(NbtType.INT)
      val LONG: Kind = Tag(NbtType.LONG)
      val FLOAT: Kind = Tag(NbtType.FLOAT)
      val DOUBLE: Kind = Tag(NbtType.DOUBLE)
      val STRING: Kind = Tag(NbtType.STRING)
      val BYTE_ARRAY: Kind = Tag(NbtType.BYTE_ARRAY)
      val INT_ARRAY: Kind = Tag(NbtType.INT_ARRAY)
      val LONG_ARRAY: Kind = Tag(NbtType.LONG_ARRAY)
      val LIST: Kind = Tag(NbtType.LIST)
      val COMPOUND: Kind = Tag(NbtType.COMPOUND)
      val TYPE_END: Kind = Type(END)
      val TYPE_BYTE: Kind = Type(BYTE)
      val TYPE_SHORT: Kind = Type(SHORT)
      val TYPE_INT: Kind = Type(INT)
      val TYPE_LONG: Kind = Type(LONG)
      val TYPE_FLOAT: Kind = Type(FLOAT)
      val TYPE_DOUBLE: Kind = Type(DOUBLE)
      val TYPE_STRING: Kind = Type(STRING)
      val TYPE_BYTE_ARRAY: Kind = Type(BYTE_ARRAY)
      val TYPE_INT_ARRAY: Kind = Type(INT_ARRAY)
      val TYPE_LONG_ARRAY: Kind = Type(LONG_ARRAY)
      val TYPE_LIST: Kind = Type(LIST)
      val TYPE_COMPOUND: Kind = Type(COMPOUND)
    }
  }
}
