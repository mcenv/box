package mcx.ast

import mcx.data.NbtType
import mcx.phase.Env
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
    val type: Value

    object Tag : Term {
      override val type: Value get() = Value.Type.BYTE
      override fun toString(): kotlin.String = "Tag"
    }

    data class TagOf(
      val value: NbtType,
    ) : Term {
      override val type: Value get() = Value.Tag
    }

    data class Type(
      val element: Term,
    ) : Term {
      override val type: Value get() = Value.Type.BYTE
    }

    object Bool : Term {
      override val type: Value get() = Value.Type.BYTE
      override fun toString(): kotlin.String = "Bool"
    }

    data class BoolOf(
      val value: Boolean,
    ) : Term {
      override val type: Value get() = Value.Bool
    }

    data class If(
      val condition: Term,
      val thenBranch: Term,
      val elseBranch: Term,
      override val type: Value,
    ) : Term

    data class Is(
      val scrutinee: Term,
      val scrutineer: Pattern,
    ) : Term {
      override val type: Value get() = Value.Bool
    }

    object Byte : Term {
      override val type: Value get() = Value.Type.BYTE
      override fun toString(): kotlin.String = "Byte"
    }

    data class ByteOf(
      val value: kotlin.Byte,
    ) : Term {
      override val type: Value get() = Value.Byte
    }

    object Short : Term {
      override val type: Value get() = Value.Type.SHORT
      override fun toString(): kotlin.String = "Short"
    }

    data class ShortOf(
      val value: kotlin.Short,
    ) : Term {
      override val type: Value get() = Value.Short
    }

    object Int : Term {
      override val type: Value get() = Value.Type.INT
      override fun toString(): kotlin.String = "Int"
    }

    data class IntOf(
      val value: kotlin.Int,
    ) : Term {
      override val type: Value get() = Value.Int
    }

    object Long : Term {
      override val type: Value get() = Value.Type.LONG
      override fun toString(): kotlin.String = "Long"
    }

    data class LongOf(
      val value: kotlin.Long,
    ) : Term {
      override val type: Value get() = Value.Long
    }

    object Float : Term {
      override val type: Value get() = Value.Type.FLOAT
      override fun toString(): kotlin.String = "Float"
    }

    data class FloatOf(
      val value: kotlin.Float,
    ) : Term {
      override val type: Value get() = Value.Float
    }

    object Double : Term {
      override val type: Value get() = Value.Type.DOUBLE
      override fun toString(): kotlin.String = "Double"
    }

    data class DoubleOf(
      val value: kotlin.Double,
    ) : Term {
      override val type: Value get() = Value.Double
    }

    object String : Term {
      override val type: Value get() = Value.Type.STRING
      override fun toString(): kotlin.String = "String"
    }

    data class StringOf(
      val value: kotlin.String,
    ) : Term {
      override val type: Value get() = Value.String
    }

    object ByteArray : Term {
      override val type: Value get() = Value.Type.BYTE_ARRAY
      override fun toString(): kotlin.String = "ByteArray"
    }

    data class ByteArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term {
      override val type: Value get() = Value.ByteArray
    }

    object IntArray : Term {
      override val type: Value get() = Value.Type.INT_ARRAY
      override fun toString(): kotlin.String = "IntArray"
    }

    data class IntArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term {
      override val type: Value get() = Value.IntArray
    }

    object LongArray : Term {
      override val type: Value get() = Value.Type.LONG_ARRAY
      override fun toString(): kotlin.String = "LongArray"
    }

    data class LongArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term {
      override val type: Value get() = Value.LongArray
    }

    data class List(
      val element: Term,
    ) : Term {
      override val type: Value get() = Value.Type.LIST
    }

    data class ListOf(
      val elements: kotlin.collections.List<Term>,
      override val type: Value,
    ) : Term

    data class Compound(
      val elements: Map<kotlin.String, Term>,
    ) : Term {
      override val type: Value get() = Value.Type.COMPOUND
    }

    data class CompoundOf(
      val elements: Map<kotlin.String, Term>,
      override val type: Value,
    ) : Term

    data class Union(
      val elements: kotlin.collections.List<Term>,
      override val type: Value,
    ) : Term

    data class Func(
      val params: kotlin.collections.List<Pair<Pattern, Term>>,
      val result: Term,
    ) : Term {
      override val type: Value get() = Value.Type.COMPOUND
    }

    data class FuncOf(
      val params: kotlin.collections.List<Pattern>,
      val result: Term,
      override val type: Value,
    ) : Term

    data class Apply(
      val func: Term,
      val args: kotlin.collections.List<Term>,
      override val type: Value,
    ) : Term

    data class Code(
      val element: Term,
    ) : Term {
      override val type: Value get() = Value.Type.END
    }

    data class CodeOf(
      val element: Term,
      override val type: Value,
    ) : Term

    data class Splice(
      val element: Term,
      override val type: Value,
    ) : Term

    data class Let(
      val binder: Pattern,
      val init: Term,
      val body: Term,
      override val type: Value,
    ) : Term

    data class Var(
      val name: kotlin.String,
      val idx: Idx,
      override val type: Value,
    ) : Term

    data class Def(
      val name: DefinitionLocation,
      val body: Term?,
      override val type: Value,
    ) : Term

    data class Meta(
      val index: kotlin.Int,
      val source: Range,
      override val type: Value,
    ) : Term

    object Hole : Term {
      override val type: Value get() = Value.Hole
    }
  }

  /**
   * A well-typed pattern.
   */
  sealed interface Pattern {
    val type: Value

    data class IntOf(
      val value: Int,
    ) : Pattern {
      override val type: Value get() = Value.Int
    }

    data class CompoundOf(
      val elements: List<Pair<String, Pattern>>,
      override val type: Value,
    ) : Pattern

    data class Var(
      val name: String,
      override val type: Value,
    ) : Pattern

    data class Drop(
      override val type: Value,
    ) : Pattern

    object Hole : Pattern {
      override val type: Value get() = Value.Hole
    }
  }

  /**
   * A well-typed term in weak-head normal form or neutral form.
   */
  sealed interface Value {
    object Tag : Value {
      override fun toString(): kotlin.String = "Tag"
    }

    data class TagOf(
      val value: NbtType,
    ) : Value

    data class Type(
      val element: Lazy<Value>,
    ) : Value {
      companion object {
        val END: Value = Type(lazyOf(TagOf(NbtType.END)))
        val BYTE: Value = Type(lazyOf(TagOf(NbtType.BYTE)))
        val SHORT: Value = Type(lazyOf(TagOf(NbtType.SHORT)))
        val INT: Value = Type(lazyOf(TagOf(NbtType.INT)))
        val LONG: Value = Type(lazyOf(TagOf(NbtType.LONG)))
        val FLOAT: Value = Type(lazyOf(TagOf(NbtType.FLOAT)))
        val DOUBLE: Value = Type(lazyOf(TagOf(NbtType.DOUBLE)))
        val STRING: Value = Type(lazyOf(TagOf(NbtType.STRING)))
        val BYTE_ARRAY: Value = Type(lazyOf(TagOf(NbtType.BYTE_ARRAY)))
        val INT_ARRAY: Value = Type(lazyOf(TagOf(NbtType.INT_ARRAY)))
        val LONG_ARRAY: Value = Type(lazyOf(TagOf(NbtType.LONG_ARRAY)))
        val LIST: Value = Type(lazyOf(TagOf(NbtType.LIST)))
        val COMPOUND: Value = Type(lazyOf(TagOf(NbtType.COMPOUND)))
      }
    }

    object Bool : Value {
      override fun toString(): kotlin.String = "Bool"
    }

    data class BoolOf(
      val value: Boolean,
    ) : Value

    data class If(
      val condition: Value,
      val thenBranch: Lazy<Value>,
      val elseBranch: Lazy<Value>,
      val type: Value,
    ) : Value

    data class Is(
      val scrutinee: Lazy<Value>,
      val scrutineer: Pattern,
    ) : Value

    object Byte : Value {
      override fun toString(): kotlin.String = "Byte"
    }

    data class ByteOf(
      val value: kotlin.Byte,
    ) : Value

    object Short : Value {
      override fun toString(): kotlin.String = "Short"
    }

    data class ShortOf(
      val value: kotlin.Short,
    ) : Value

    object Int : Value {
      override fun toString(): kotlin.String = "Int"
    }

    data class IntOf(
      val value: kotlin.Int,
    ) : Value

    object Long : Value {
      override fun toString(): kotlin.String = "Long"
    }

    data class LongOf(
      val value: kotlin.Long,
    ) : Value

    object Float : Value {
      override fun toString(): kotlin.String = "Float"
    }

    data class FloatOf(
      val value: kotlin.Float,
    ) : Value

    object Double : Value {
      override fun toString(): kotlin.String = "Double"
    }

    data class DoubleOf(
      val value: kotlin.Double,
    ) : Value

    object String : Value {
      override fun toString(): kotlin.String = "String"
    }

    data class StringOf(
      val value: kotlin.String,
    ) : Value

    object ByteArray : Value {
      override fun toString(): kotlin.String = "ByteArray"
    }

    data class ByteArrayOf(
      val elements: kotlin.collections.List<Lazy<Value>>,
    ) : Value

    object IntArray : Value {
      override fun toString(): kotlin.String = "IntArray"
    }

    data class IntArrayOf(
      val elements: kotlin.collections.List<Lazy<Value>>,
    ) : Value

    object LongArray : Value {
      override fun toString(): kotlin.String = "LongArray"
    }

    data class LongArrayOf(
      val elements: kotlin.collections.List<Lazy<Value>>,
    ) : Value

    data class List(
      val element: Lazy<Value>,
    ) : Value

    data class ListOf(
      val elements: kotlin.collections.List<Lazy<Value>>,
      val type: Value,
    ) : Value

    data class Compound(
      val elements: Map<kotlin.String, Lazy<Value>>,
    ) : Value

    data class CompoundOf(
      val elements: Map<kotlin.String, Lazy<Value>>,
      val type: Value,
    ) : Value

    data class Union(
      val elements: kotlin.collections.List<Lazy<Value>>,
      val type: Value,
    ) : Value

    data class Func(
      val params: kotlin.collections.List<Lazy<Value>>,
      val result: Closure,
    ) : Value

    data class FuncOf(
      val result: Closure,
      val type: Value,
    ) : Value

    data class Apply(
      val func: Value,
      val args: kotlin.collections.List<Lazy<Value>>,
      val type: Value,
    ) : Value

    data class Code(
      val element: Lazy<Value>,
    ) : Value

    data class CodeOf(
      val element: Lazy<Value>,
      val type: Value,
    ) : Value

    data class Splice(
      val element: Value,
      val type: Value,
    ) : Value

    data class Var(
      val name: kotlin.String,
      val lvl: Lvl,
      val type: Value,
    ) : Value

    data class Def(
      val name: DefinitionLocation,
      val body: Term?,
      val type: Value,
    ) : Value

    data class Meta(
      val index: kotlin.Int,
      val source: Range,
      val type: Value,
    ) : Value

    object Hole : Value {
      override fun toString(): kotlin.String = "Hole"
    }
  }

  data class Closure(
    val env: Env,
    val binders: List<Pattern>,
    val body: Term,
  )
}
