package mcx.pass

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
  data object Tag : Value

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

  data object Bool : Value

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

  data object Byte : Value

  data class ByteOf(
    val value: kotlin.Byte,
  ) : Value

  data object Short : Value

  data class ShortOf(
    val value: kotlin.Short,
  ) : Value

  data object Int : Value

  data class IntOf(
    val value: kotlin.Int,
  ) : Value

  data object Long : Value

  data class LongOf(
    val value: kotlin.Long,
  ) : Value

  data object Float : Value

  data class FloatOf(
    val value: kotlin.Float,
  ) : Value

  data object Double : Value

  data class DoubleOf(
    val value: kotlin.Double,
  ) : Value

  data object String : Value

  data class StringOf(
    val value: kotlin.String,
  ) : Value

  data object ByteArray : Value

  data class ByteArrayOf(
    val elements: kotlin.collections.List<Lazy<Value>>,
  ) : Value

  data object IntArray : Value

  data class IntArrayOf(
    val elements: kotlin.collections.List<Lazy<Value>>,
  ) : Value

  data object LongArray : Value

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
    val type: Value,
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

  data class Command(
    val element: Lazy<Value>,
    val type: Value,
  ) : Value

  data class Let(
    val binder: Pattern<Value>,
    val init: Lazy<Value>,
    val body: Lazy<Value>,
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
  ) : Value

  data object Hole : Value
}

typealias Env = PersistentList<Lazy<Value>>

fun Env.next(): Lvl {
  return Lvl(size)
}

data class Closure(
  val env: Env,
  val binders: List<Pattern<Value>>,
  val body: Term,
)
