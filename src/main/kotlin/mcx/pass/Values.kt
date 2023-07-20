package mcx.pass

import kotlinx.collections.immutable.PersistentList
import mcx.ast.Core.Definition
import mcx.ast.Core.Pattern
import mcx.ast.Core.Term
import mcx.ast.common.Lvl
import mcx.ast.common.Proj
import mcx.ast.common.Repr
import org.eclipse.lsp4j.Range

/**
 * A well-typed term in weak-head normal form or neutral form.
 */
sealed class Value {
  abstract val type: Lazy<Value>

  data object Tag : Value() {
    override val type: Lazy<Value> get() = Type.END_LAZY

    val LAZY: Lazy<Tag> = lazyOf(Tag)
  }

  data class TagOf(
    val repr: Repr,
  ) : Value() {
    override val type: Lazy<Value> get() = Tag.LAZY
  }

  data class Type(
    val element: Lazy<Value>,
  ) : Value() {
    override val type: Lazy<Value> get() = BYTE_LAZY

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

      val END_LAZY: Lazy<Value> = lazyOf(END)
      val BYTE_LAZY: Lazy<Value> = lazyOf(BYTE)
      val SHORT_LAZY: Lazy<Value> = lazyOf(SHORT)
      val INT_LAZY: Lazy<Value> = lazyOf(INT)
      val LONG_LAZY: Lazy<Value> = lazyOf(LONG)
      val FLOAT_LAZY: Lazy<Value> = lazyOf(FLOAT)
      val DOUBLE_LAZY: Lazy<Value> = lazyOf(DOUBLE)
      val STRING_LAZY: Lazy<Value> = lazyOf(STRING)
      val BYTE_ARRAY_LAZY: Lazy<Value> = lazyOf(BYTE_ARRAY)
      val INT_ARRAY_LAZY: Lazy<Value> = lazyOf(INT_ARRAY)
      val LONG_ARRAY_LAZY: Lazy<Value> = lazyOf(LONG_ARRAY)
      val LIST_LAZY: Lazy<Value> = lazyOf(LIST)
      val COMPOUND_LAZY: Lazy<Value> = lazyOf(COMPOUND)
    }
  }

  data object Unit : Value() {
    override val type: Lazy<Value> get() = Type.BYTE_LAZY

    val LAZY: Lazy<Unit> = lazyOf(Unit)
  }

  data object UnitOf : Value() {
    override val type: Lazy<Value> get() = Unit.LAZY
  }

  data object Bool : Value() {
    override val type: Lazy<Value> get() = Type.BYTE_LAZY

    val LAZY: Lazy<Bool> = lazyOf(Bool)
  }

  data class BoolOf(
    val value: Boolean,
  ) : Value() {
    override val type: Lazy<Value> get() = Bool.LAZY
  }

  data object I8 : Value() {
    override val type: Lazy<Value> get() = Type.BYTE_LAZY

    val LAZY: Lazy<I8> = lazyOf(I8)
  }

  data class I8Of(
    val value: Byte,
  ) : Value() {
    override val type: Lazy<Value> get() = I8.LAZY
  }

  data object I16 : Value() {
    override val type: Lazy<Value> get() = Type.SHORT_LAZY

    val LAZY: Lazy<I16> = lazyOf(I16)
  }

  data class I16Of(
    val value: Short,
  ) : Value() {
    override val type: Lazy<Value> get() = I16.LAZY
  }

  data object I32 : Value() {
    override val type: Lazy<Value> get() = Type.INT_LAZY

    val LAZY: Lazy<I32> = lazyOf(I32)
  }

  data class I32Of(
    val value: Int,
  ) : Value() {
    override val type: Lazy<Value> get() = I32.LAZY
  }

  data object I64 : Value() {
    override val type: Lazy<Value> get() = Type.LONG_LAZY

    val LAZY: Lazy<I64> = lazyOf(I64)
  }

  data class I64Of(
    val value: Long,
  ) : Value() {
    override val type: Lazy<Value> get() = I64.LAZY
  }

  data object F32 : Value() {
    override val type: Lazy<Value> get() = Type.FLOAT_LAZY

    val LAZY: Lazy<F32> = lazyOf(F32)
  }

  data class F32Of(
    val value: Float,
  ) : Value() {
    override val type: Lazy<Value> get() = F32.LAZY
  }

  data object F64 : Value() {
    override val type: Lazy<Value> get() = Type.DOUBLE_LAZY

    val LAZY: Lazy<F64> = lazyOf(F64)
  }

  data class F64Of(
    val value: Double,
  ) : Value() {
    override val type: Lazy<Value> get() = F64.LAZY
  }

  data object Wtf16 : Value() {
    override val type: Lazy<Value> get() = Type.STRING_LAZY

    val LAZY: Lazy<Wtf16> = lazyOf(Wtf16)
  }

  data class Wtf16Of(
    val value: String,
  ) : Value() {
    override val type: Lazy<Value> get() = Wtf16.LAZY
  }

  data object I8Array : Value() {
    override val type: Lazy<Value> get() = Type.BYTE_ARRAY_LAZY

    val LAZY: Lazy<I8Array> = lazyOf(I8Array)
  }

  data class I8ArrayOf(
    val elements: kotlin.collections.List<Lazy<Value>>,
  ) : Value() {
    override val type: Lazy<Value> get() = I8Array.LAZY
  }

  data object I32Array : Value() {
    override val type: Lazy<Value> get() = Type.INT_ARRAY_LAZY

    val LAZY: Lazy<I32Array> = lazyOf(I32Array)
  }

  data class I32ArrayOf(
    val elements: kotlin.collections.List<Lazy<Value>>,
  ) : Value() {
    override val type: Lazy<Value> get() = I32Array.LAZY
  }

  data object I64Array : Value() {
    override val type: Lazy<Value> get() = Type.LONG_ARRAY_LAZY

    val LAZY: Lazy<I64Array> = lazyOf(I64Array)
  }

  data class I64ArrayOf(
    val elements: kotlin.collections.List<Lazy<Value>>,
  ) : Value() {
    override val type: Lazy<Value> get() = I64Array.LAZY
  }

  data class List(
    val element: Lazy<Value>,
  ) : Value() {
    override val type: Lazy<Value> get() = Type.LIST_LAZY
  }

  data class ListOf(
    val elements: kotlin.collections.List<Lazy<Value>>,
    override val type: Lazy<Value>,
  ) : Value()

  data class Compound(
    val elements: LinkedHashMap<String, Lazy<Value>>,
  ) : Value() {
    override val type: Lazy<Value> get() = Type.COMPOUND_LAZY
  }

  data class CompoundOf(
    val elements: LinkedHashMap<String, Lazy<Value>>,
    override val type: Lazy<Value>,
  ) : Value()

  data class Point(
    val element: Lazy<Value>,
    override val type: Lazy<Value>,
  ) : Value()

  data class Union(
    val elements: kotlin.collections.List<Lazy<Value>>,
    override val type: Lazy<Value>,
  ) : Value()

  data class Func(
    val open: Boolean,
    val params: kotlin.collections.List<Pair<Pattern, Lazy<Value>>>,
    val result: Closure,
  ) : Value() {
    override val type: Lazy<Value> get() = Type.COMPOUND_LAZY
  }

  data class FuncOf(
    val open: Boolean,
    val params: kotlin.collections.List<Pattern>,
    val result: Closure,
    override val type: Lazy<Value>,
  ) : Value()

  data class Apply(
    val open: Boolean,
    val func: Value,
    val args: kotlin.collections.List<Lazy<Value>>,
    override val type: Lazy<Value>,
  ) : Value()

  data class Code(
    val element: Lazy<Value>,
  ) : Value() {
    override val type: Lazy<Value> get() = Type.END_LAZY
  }

  data class CodeOf(
    val element: Lazy<Value>,
    override val type: Lazy<Value>,
  ) : Value()

  data class Splice(
    val element: Value,
    override val type: Lazy<Value>,
  ) : Value()

  data class Command(
    val element: Lazy<Value>,
    override val type: Lazy<Value>,
  ) : Value()

  data class Let(
    val binder: Pattern,
    val init: Lazy<Value>,
    val body: Lazy<Value>,
    override val type: Lazy<Value>,
  ) : Value()

  data class If(
    val scrutinee: Lazy<Value>,
    val branches: kotlin.collections.List<Pair<Pattern, Lazy<Value>>>,
    override val type: Lazy<Value>,
  ) : Value()

  data class Project(
    val target: Value,
    val projs: kotlin.collections.List<Proj>,
    override val type: Lazy<Value>,
  ) : Value()

  data class Var(
    val name: String,
    val lvl: Lvl,
    override val type: Lazy<Value>,
  ) : Value()

  data class Def(
    val def: Definition.Def,
    override val type: Lazy<Value>,
  ) : Value()

  data class Meta(
    val index: Int,
    val source: Range,
    override val type: Lazy<Value>,
  ) : Value()

  data class Builtin(
    val builtin: mcx.pass.Builtin,
    override val type: Lazy<Value>,
  ) : Value()

  data object Hole : Value() {
    override val type: Lazy<Value> get() = LAZY

    val LAZY: Lazy<Hole> = lazyOf(Hole)
  }
}

typealias Env = PersistentList<Lazy<Value>>

data class Closure(
  val env: Env,
  val body: Term,
)
