package mcx.ast

import kotlinx.collections.immutable.PersistentList
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
      val type: Value,
      val body: Term?,
    ) : Definition
  }

  sealed interface Term {
    val type: Value

    object Tag : Term {
      override val type: Value get() = Value.Type.END
    }

    data class TagOf(
      val value: NbtType,
    ) : Term {
      override val type: Value get() = Value.Tag
    }

    data class Type(
      val tag: Term,
    ) : Term {
      override val type: Value get() = Value.Type.BYTE
    }

    object Bool : Term {
      override val type: Value get() = Value.Type.BYTE
    }

    data class BoolOf(
      val value: Boolean,
    ) : Term {
      override val type: Value get() = Value.Byte
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
    }

    data class ByteOf(
      val value: kotlin.Byte,
    ) : Term {
      override val type: Value get() = Value.Byte
    }

    object Short : Term {
      override val type: Value get() = Value.Type.SHORT
    }

    data class ShortOf(
      val value: kotlin.Short,
    ) : Term {
      override val type: Value get() = Value.Short
    }

    object Int : Term {
      override val type: Value get() = Value.Type.INT
    }

    data class IntOf(
      val value: kotlin.Int,
    ) : Term {
      override val type: Value get() = Value.Int
    }

    object Long : Term {
      override val type: Value get() = Value.Type.LONG
    }

    data class LongOf(
      val value: kotlin.Long,
    ) : Term {
      override val type: Value get() = Value.Long
    }

    object Float : Term {
      override val type: Value get() = Value.Type.FLOAT
    }

    data class FloatOf(
      val value: kotlin.Float,
    ) : Term {
      override val type: Value get() = Value.Float
    }

    object Double : Term {
      override val type: Value get() = Value.Type.DOUBLE
    }

    data class DoubleOf(
      val value: kotlin.Double,
    ) : Term {
      override val type: Value get() = Value.Double
    }

    object String : Term {
      override val type: Value get() = Value.Type.STRING
    }

    data class StringOf(
      val value: kotlin.String,
    ) : Term {
      override val type: Value get() = Value.String
    }

    object ByteArray : Term {
      override val type: Value get() = Value.Type.BYTE_ARRAY
    }

    data class ByteArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term {
      override val type: Value get() = Value.ByteArray
    }

    object IntArray : Term {
      override val type: Value get() = Value.Type.INT_ARRAY
    }

    data class IntArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term {
      override val type: Value get() = Value.IntArray
    }

    object LongArray : Term {
      override val type: Value get() = Value.Type.LONG_ARRAY
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
      val level: kotlin.Int,
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

    data class Hole(
      override val type: Value,
    ) : Term
  }

  sealed interface Pattern {
    val type: Value

    data class IntOf(
      val value: Int,
    ) : Pattern {
      override val type: Value get() = Value.Int
    }

    data class CompoundOf(
      val elements: Map<String, Pattern>,
      override val type: Value,
    ) : Pattern

    data class CodeOf(
      val element: Pattern,
      override val type: Value,
    ) : Pattern

    data class Var(
      val name: String,
      val level: Int,
      override val type: Value,
    ) : Pattern

    data class Drop(
      override val type: Value,
    ) : Pattern

    data class Hole(
      override val type: Value,
    ) : Pattern
  }

  class Closure(
    val values: PersistentList<Lazy<Value>>,
    val binders: List<Pattern>,
    val body: Term,
  )

  sealed interface Value {
    object Tag : Value

    @JvmInline
    value class TagOf(
      val value: NbtType,
    ) : Value

    @JvmInline
    value class Type(
      val tag: Lazy<Value>,
    ) : Value {
      companion object {
        val END = Type(lazyOf(TagOf(NbtType.END)))
        val BYTE = Type(lazyOf(TagOf(NbtType.BYTE)))
        val SHORT = Type(lazyOf(TagOf(NbtType.SHORT)))
        val INT = Type(lazyOf(TagOf(NbtType.INT)))
        val LONG = Type(lazyOf(TagOf(NbtType.LONG)))
        val FLOAT = Type(lazyOf(TagOf(NbtType.FLOAT)))
        val DOUBLE = Type(lazyOf(TagOf(NbtType.DOUBLE)))
        val BYTE_ARRAY = Type(lazyOf(TagOf(NbtType.BYTE_ARRAY)))
        val INT_ARRAY = Type(lazyOf(TagOf(NbtType.INT_ARRAY)))
        val LONG_ARRAY = Type(lazyOf(TagOf(NbtType.LONG_ARRAY)))
        val STRING = Type(lazyOf(TagOf(NbtType.STRING)))
        val LIST = Type(lazyOf(TagOf(NbtType.LIST)))
        val COMPOUND = Type(lazyOf(TagOf(NbtType.COMPOUND)))
      }
    }

    object Bool : Value

    @JvmInline
    value class BoolOf(
      val value: Boolean,
    ) : Value

    class If(
      val condition: Value,
      val thenBranch: Lazy<Value>,
      val elseBranch: Lazy<Value>,
      val type: Value,
    ) : Value

    class Is(
      val scrutinee: Lazy<Value>,
      val scrutineer: Pattern,
    ) : Value

    object Byte : Value

    @JvmInline
    value class ByteOf(
      val value: kotlin.Byte,
    ) : Value

    object Short : Value

    @JvmInline
    value class ShortOf(
      val value: kotlin.Short,
    ) : Value

    object Int : Value

    @JvmInline
    value class IntOf(
      val value: kotlin.Int,
    ) : Value

    object Long : Value

    @JvmInline
    value class LongOf(
      val value: kotlin.Long,
    ) : Value

    object Float : Value

    @JvmInline
    value class FloatOf(
      val value: kotlin.Float,
    ) : Value

    object Double : Value

    @JvmInline
    value class DoubleOf(
      val value: kotlin.Double,
    ) : Value

    object String : Value

    @JvmInline
    value class StringOf(
      val value: kotlin.String,
    ) : Value

    object ByteArray : Value

    @JvmInline
    value class ByteArrayOf(
      val elements: kotlin.collections.List<Lazy<Value>>,
    ) : Value

    object IntArray : Value

    @JvmInline
    value class IntArrayOf(
      val elements: kotlin.collections.List<Lazy<Value>>,
    ) : Value

    object LongArray : Value

    @JvmInline
    value class LongArrayOf(
      val elements: kotlin.collections.List<Lazy<Value>>,
    ) : Value

    @JvmInline
    value class List(
      val element: Lazy<Value>,
    ) : Value

    class ListOf(
      val elements: kotlin.collections.List<Lazy<Value>>,
      val type: Value,
    ) : Value

    @JvmInline
    value class Compound(
      val elements: Map<kotlin.String, Lazy<Value>>,
    ) : Value

    @JvmInline
    value class CompoundOf(
      val elements: Map<kotlin.String, Lazy<Value>>,
    ) : Value

    @JvmInline
    value class Union(
      val elements: kotlin.collections.List<Lazy<Value>>,
    ) : Value

    class Func(
      val params: kotlin.collections.List<Lazy<Value>>,
      val result: Closure,
    ) : Value

    class FuncOf(
      val result: Closure,
      val type: Value,
    ) : Value

    class Apply(
      val func: Value,
      val args: kotlin.collections.List<Lazy<Value>>,
      val type: Value,
    ) : Value

    @JvmInline
    value class Code(
      val element: Lazy<Value>,
    ) : Value

    @JvmInline
    value class CodeOf(
      val element: Lazy<Value>,
    ) : Value

    class Splice(
      val element: Value,
      val type: Value,
    ) : Value

    class Let(
      val binder: Pattern,
      val init: Value,
      val body: Value,
      val type: Value,
    ) : Value

    class Var(
      val name: kotlin.String,
      val level: kotlin.Int,
      val type: Value,
    ) : Value

    class Def(
      val name: DefinitionLocation,
      val body: Term?,
      val type: Value,
    ) : Value

    class Meta(
      val index: kotlin.Int,
      val source: Range,
      val type: Value,
    ) : Value

    @JvmInline
    value class Hole(
      val type: Value,
    ) : Value
  }
}
