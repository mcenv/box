package mcx.pass

import kotlinx.collections.immutable.PersistentList
import mcx.ast.Core.Definition
import mcx.ast.Core.Pattern
import mcx.ast.Core.Term
import mcx.ast.Lvl
import mcx.data.NbtType
import org.eclipse.lsp4j.Range

/**
 * A well-typed term in weak-head normal form or neutral form.
 */
sealed class Value {
  data object Tag : Value()

  data class TagOf(
    val value: NbtType,
  ) : Value()

  data class Type(
    val element: Lazy<Value>,
  ) : Value() {
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

  data object Bool : Value()

  data class BoolOf(
    val value: Boolean,
  ) : Value()

  data class If(
    val condition: Value,
    val thenBranch: Lazy<Value>,
    val elseBranch: Lazy<Value>,
  ) : Value()

  data class Is(
    val scrutinee: Lazy<Value>,
    val scrutineer: Pattern<Value>,
  ) : Value()

  data object I8 : Value()

  data class I8Of(
    val value: Byte,
  ) : Value()

  data object I16 : Value()

  data class I16Of(
    val value: Short,
  ) : Value()

  data object I32 : Value()

  data class I32Of(
    val value: Int,
  ) : Value()

  data object I64 : Value()

  data class I64Of(
    val value: Long,
  ) : Value()

  data object F32 : Value()

  data class F32Of(
    val value: Float,
  ) : Value()

  data object F64 : Value()

  data class F64Of(
    val value: Double,
  ) : Value()

  data object Str : Value()

  data class StrOf(
    val value: String,
  ) : Value()

  data object I8Array : Value()

  data class I8ArrayOf(
    val elements: List<Lazy<Value>>,
  ) : Value()

  data object I32Array : Value()

  data class I32ArrayOf(
    val elements: List<Lazy<Value>>,
  ) : Value()

  data object I64Array : Value()

  data class I64ArrayOf(
    val elements: List<Lazy<Value>>,
  ) : Value()

  data class Vec(
    val element: Lazy<Value>,
  ) : Value()

  data class VecOf(
    val elements: List<Lazy<Value>>,
  ) : Value()

  data class Struct(
    val elements: LinkedHashMap<String, Lazy<Value>>,
  ) : Value()

  data class StructOf(
    val elements: LinkedHashMap<String, Lazy<Value>>,
  ) : Value()

  data class Point(
    val element: Lazy<Value>,
    val elementType: Value,
  ) : Value()

  data class Union(
    val elements: List<Lazy<Value>>,
  ) : Value()

  data class Func(
    val open: Boolean,
    val params: List<Lazy<Value>>,
    val result: Closure,
  ) : Value()

  data class FuncOf(
    val open: Boolean,
    val result: Closure,
  ) : Value()

  data class Apply(
    val open: Boolean,
    val func: Value,
    val args: List<Lazy<Value>>,
    val type: Value,
  ) : Value()

  data class Code(
    val element: Lazy<Value>,
  ) : Value()

  data class CodeOf(
    val element: Lazy<Value>,
  ) : Value()

  data class Splice(
    val element: Value,
  ) : Value()

  data class Command(
    val element: Lazy<Value>,
    val type: Value,
  ) : Value()

  data class Let(
    val binder: Pattern<Value>,
    val init: Lazy<Value>,
    val body: Lazy<Value>,
  ) : Value()

  data class Var(
    val name: String,
    val lvl: Lvl,
    val type: Value,
  ) : Value()

  data class Def(
    val def: Definition.Def,
  ) : Value()

  data class Meta(
    val index: Int,
    val source: Range,
  ) : Value()

  data object Hole : Value()
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
