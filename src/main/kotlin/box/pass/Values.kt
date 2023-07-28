package box.pass

import box.ast.Core.Definition
import box.ast.Core.Pattern
import box.ast.common.Lvl
import box.ast.common.Proj
import box.ast.common.Repr
import kotlinx.collections.immutable.PersistentList
import org.eclipse.lsp4j.Range

/**
 * A well-typed term in weak-head normal form or neutral form.
 */
sealed class Value {
  data object Tag : Value() {
    val LAZY: Lazy<Tag> = lazyOf(Tag)
  }

  data class TagOf(
    val repr: Repr,
  ) : Value()

  data class Type(
    val element: Lazy<Value>,
  ) : Value() {
    companion object {
      val END: Value = Type(lazyOf(TagOf(Repr.END)))
      val BYTE: Value = Type(lazyOf(TagOf(Repr.BYTE)))
      val SHORT: Value = Type(lazyOf(TagOf(Repr.SHORT)))
      val INT: Value = Type(lazyOf(TagOf(Repr.INT)))
      val LONG: Value = Type(lazyOf(TagOf(Repr.LONG)))
      val FLOAT: Value = Type(lazyOf(TagOf(Repr.FLOAT)))
      val DOUBLE: Value = Type(lazyOf(TagOf(Repr.DOUBLE)))
      val STRING: Value = Type(lazyOf(TagOf(Repr.STRING)))
      val BYTE_ARRAY: Value = Type(lazyOf(TagOf(Repr.BYTE_ARRAY)))
      val INT_ARRAY: Value = Type(lazyOf(TagOf(Repr.INT_ARRAY)))
      val LONG_ARRAY: Value = Type(lazyOf(TagOf(Repr.LONG_ARRAY)))
      val LIST: Value = Type(lazyOf(TagOf(Repr.LIST)))
      val COMPOUND: Value = Type(lazyOf(TagOf(Repr.COMPOUND)))
    }
  }

  data object Unit : Value()

  data object Bool : Value()

  data object I8 : Value()

  data object I16 : Value()

  data object I32 : Value()

  data object I64 : Value()

  data object F32 : Value()

  data object F64 : Value()

  data object Wtf16 : Value()

  data class ConstOf<out T>(
    val value: T & Any,
  ) : Value()

  data object I8Array : Value()

  data class I8ArrayOf(
    val elements: kotlin.collections.List<Lazy<Value>>,
  ) : Value()

  data object I32Array : Value()

  data class I32ArrayOf(
    val elements: kotlin.collections.List<Lazy<Value>>,
  ) : Value()

  data object I64Array : Value()

  data class I64ArrayOf(
    val elements: kotlin.collections.List<Lazy<Value>>,
  ) : Value()

  data class List(
    val element: Lazy<Value>,
  ) : Value()

  data class ListOf(
    val elements: kotlin.collections.List<Lazy<Value>>,
  ) : Value()

  data class Compound(
    val elements: LinkedHashMap<String, Lazy<Value>>,
  ) : Value()

  data class CompoundOf(
    val elements: LinkedHashMap<String, Lazy<Value>>,
  ) : Value()

  data class Point(
    val elementType: Lazy<Value>,
    val element: Lazy<Value>,
  ) : Value()

  data class Union(
    val elements: kotlin.collections.List<Lazy<Value>>,
  ) : Value()

  data class Func(
    val open: Boolean,
    val params: kotlin.collections.List<Pair<Pattern, Lazy<Value>>>,
    val result: Closure,
  ) : Value()

  data class FuncOf(
    val open: Boolean,
    val params: kotlin.collections.List<Pair<Pattern, Lazy<Value>>>,
    val result: Closure,
  ) : Value()

  data class Apply(
    val open: Boolean,
    val func: Value,
    val args: kotlin.collections.List<Lazy<Value>>,
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

  data class Path(
    val element: Lazy<Value>,
  ) : Value()

  data class PathOf(
    val element: Lazy<Value>,
  ) : Value()

  data class Get(
    val element: Value,
  ) : Value()

  data class Command(
    val element: Lazy<Value>,
    val type: Lazy<Value>,
  ) : Value()

  data class Let(
    val binder: Pattern,
    val init: Lazy<Value>,
    val body: Lazy<Value>,
  ) : Value()

  data class If(
    val scrutinee: Lazy<Value>,
    val branches: kotlin.collections.List<Pair<Pattern, Lazy<Value>>>,
  ) : Value()

  data class Project(
    val target: Value,
    val projs: kotlin.collections.List<Proj>,
  ) : Value()

  data class Var(
    val name: String,
    val lvl: Lvl,
  ) : Value()

  data class Def(
    val def: Definition.Def,
  ) : Value()

  data class Meta(
    val index: Int,
    val source: Range,
  ) : Value()

  data class Builtin(
    val builtin: box.pass.Builtin,
  ) : Value()

  data object Hole : Value()
}

typealias Closure = ((List<Lazy<Value>>) -> Value)

typealias Env = PersistentList<Lazy<Value>>
