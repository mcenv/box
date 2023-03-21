package mcx.phase

import kotlinx.collections.immutable.PersistentList
import mcx.ast.Core.Pattern
import mcx.ast.Core.Term
import mcx.ast.DefinitionLocation
import mcx.ast.Lvl
import mcx.data.NbtType
import org.eclipse.lsp4j.Range

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
  ) : Value

  data class Is(
    val scrutinee: Lazy<Value>,
    val scrutineer: Pattern<Value>,
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
  ) : Value

  data class Compound(
    val elements: LinkedHashMap<kotlin.String, Lazy<Value>>,
  ) : Value

  data class CompoundOf(
    val elements: LinkedHashMap<kotlin.String, Lazy<Value>>,
  ) : Value

  data class Point(
    val element: Lazy<Value>,
    val elementType: Value,
  ) : Value

  data class Union(
    val elements: kotlin.collections.List<Lazy<Value>>,
  ) : Value

  data class Func(
    val params: kotlin.collections.List<Lazy<Value>>,
    val result: Closure,
  ) : Value

  data class FuncOf(
    val result: Closure,
  ) : Value

  data class Apply(
    val func: Value,
    val args: kotlin.collections.List<Lazy<Value>>,
  ) : Value

  data class Code(
    val element: Lazy<Value>,
  ) : Value

  data class CodeOf(
    val element: Lazy<Value>,
  ) : Value

  data class Splice(
    val element: Value,
  ) : Value

  data class Var(
    val name: kotlin.String,
    val lvl: Lvl,
    val type: Value,
  ) : Value

  data class Def(
    val name: DefinitionLocation,
    val body: Term?,
  ) : Value

  data class Meta(
    val index: kotlin.Int,
    val source: Range,
  ) : Value

  object Hole : Value {
    override fun toString(): kotlin.String = "Hole"
  }
}

typealias Env = PersistentList<Lazy<Value>>

data class Closure(
  val env: Env,
  val binders: List<Pattern<Value>>,
  val body: Term,
)
