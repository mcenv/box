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
      val type: Term,
      val body: Term?,
    ) : Definition
  }

  sealed interface Term {
    object Tag : Term

    data class TagOf(
      val value: NbtType,
    ) : Term

    data class Type(
      val tag: Term,
    ) : Term

    object Bool : Term

    data class BoolOf(
      val value: Boolean,
    ) : Term

    data class If(
      val condition: Term,
      val thenBranch: Term,
      val elseBranch: Term,
    ) : Term

    data class Is(
      val scrutinee: Term,
      val scrutineer: Pattern,
    ) : Term

    object Byte : Term

    data class ByteOf(
      val value: kotlin.Byte,
    ) : Term

    object Short : Term

    data class ShortOf(
      val value: kotlin.Short,
    ) : Term

    object Int : Term

    data class IntOf(
      val value: kotlin.Int,
    ) : Term

    object Long : Term

    data class LongOf(
      val value: kotlin.Long,
    ) : Term

    object Float : Term

    data class FloatOf(
      val value: kotlin.Float,
    ) : Term

    object Double : Term

    data class DoubleOf(
      val value: kotlin.Double,
    ) : Term

    object String : Term

    data class StringOf(
      val value: kotlin.String,
    ) : Term

    object ByteArray : Term

    data class ByteArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term

    object IntArray : Term

    data class IntArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term

    object LongArray : Term

    data class LongArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term

    data class List(
      val element: Term,
    ) : Term

    data class ListOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term

    data class Compound(
      val elements: Map<kotlin.String, Term>,
    ) : Term

    data class CompoundOf(
      val elements: Map<kotlin.String, Term>,
    ) : Term

    data class Union(
      val elements: kotlin.collections.List<Term>,
    ) : Term

    data class Func(
      val params: kotlin.collections.List<Pair<Pattern, Term>>,
      val result: Term,
    ) : Term

    data class FuncOf(
      val params: kotlin.collections.List<Pattern>,
      val result: Term,
    ) : Term

    data class Apply(
      val func: Term,
      val args: kotlin.collections.List<Term>,
    ) : Term

    data class Code(
      val element: Term,
    ) : Term

    data class CodeOf(
      val element: Term,
    ) : Term

    data class Splice(
      val element: Term,
    ) : Term

    data class Let(
      val binder: Pattern,
      val init: Term,
      val body: Term,
    ) : Term

    data class Var(
      val name: kotlin.String,
      val level: kotlin.Int,
    ) : Term

    data class Def(
      val name: DefinitionLocation,
      val body: Term?,
    ) : Term

    data class Meta(
      val index: kotlin.Int,
      val source: Range,
    ) : Term

    object Hole : Term
  }

  sealed interface Pattern {
    data class IntOf(
      val value: Int,
    ) : Pattern

    data class CompoundOf(
      val elements: Map<String, Pattern>,
    ) : Pattern

    data class CodeOf(
      val element: Pattern,
    ) : Pattern

    data class Var(
      val name: String,
      val level: Int,
    ) : Pattern

    object Drop : Pattern

    object Hole : Pattern
  }

  data class Closure(
    val values: PersistentList<Lazy<Value>>,
    val binders: List<Pattern>,
    val body: Term,
  )

  sealed interface Value {
    object Tag : Value

    data class TagOf(
      val value: NbtType,
    ) : Value

    data class Type(
      val tag: Lazy<Value>,
    ) : Value

    object Bool : Value

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
      val scrutineer: Pattern,
    ) : Value

    object Byte : Value

    data class ByteOf(
      val value: kotlin.Byte,
    ) : Value

    object Short : Value

    data class ShortOf(
      val value: kotlin.Short,
    ) : Value

    object Int : Value

    data class IntOf(
      val value: kotlin.Int,
    ) : Value

    object Long : Value

    data class LongOf(
      val value: kotlin.Long,
    ) : Value

    object Float : Value

    data class FloatOf(
      val value: kotlin.Float,
    ) : Value

    object Double : Value

    data class DoubleOf(
      val value: kotlin.Double,
    ) : Value

    object String : Value

    data class StringOf(
      val value: kotlin.String,
    ) : Value

    object ByteArray : Value

    data class ByteArrayOf(
      val elements: kotlin.collections.List<Lazy<Value>>,
    ) : Value

    object IntArray : Value

    data class IntArrayOf(
      val elements: kotlin.collections.List<Lazy<Value>>,
    ) : Value

    object LongArray : Value

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
      val elements: Map<kotlin.String, Lazy<Value>>,
    ) : Value

    data class CompoundOf(
      val elements: Map<kotlin.String, Lazy<Value>>,
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

    data class Let(
      val binder: Pattern,
      val init: Value,
      val body: Value,
    ) : Value

    data class Var(
      val name: kotlin.String,
      val level: kotlin.Int,
    ) : Value

    data class Def(
      val name: DefinitionLocation,
      val body: Term?,
    ) : Value

    data class Meta(
      val index: kotlin.Int,
      val source: Range,
    ) : Value

    object Hole : Value
  }
}
