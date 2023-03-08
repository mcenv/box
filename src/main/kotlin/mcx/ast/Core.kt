package mcx.ast

import kotlinx.collections.immutable.PersistentList
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

    data class Tag(
      override val type: Value,
    ) : Term

    data class EndTag(
      override val type: Value,
    ) : Term

    data class ByteTag(
      override val type: Value,
    ) : Term

    data class ShortTag(
      override val type: Value,
    ) : Term

    data class IntTag(
      override val type: Value,
    ) : Term

    data class LongTag(
      override val type: Value,
    ) : Term

    data class FloatTag(
      override val type: Value,
    ) : Term

    data class DoubleTag(
      override val type: Value,
    ) : Term

    data class StringTag(
      override val type: Value,
    ) : Term

    data class ByteArrayTag(
      override val type: Value,
    ) : Term

    data class IntArrayTag(
      override val type: Value,
    ) : Term

    data class LongArrayTag(
      override val type: Value,
    ) : Term

    data class ListTag(
      override val type: Value,
    ) : Term

    data class CompoundTag(
      override val type: Value,
    ) : Term

    data class Type(
      val tag: Term,
      override val type: Value,
    ) : Term

    data class Bool(
      override val type: Value,
    ) : Term

    data class BoolOf(
      val value: Boolean,
      override val type: Value,
    ) : Term

    data class If(
      val condition: Term,
      val thenBranch: Term,
      val elseBranch: Term,
      override val type: Value,
    ) : Term

    data class Is(
      val scrutinee: Term,
      val scrutineer: Pattern,
      override val type: Value,
    ) : Term

    data class Byte(
      override val type: Value,
    ) : Term

    data class ByteOf(
      val value: kotlin.Byte,
      override val type: Value,
    ) : Term

    data class Short(
      override val type: Value,
    ) : Term

    data class ShortOf(
      val value: kotlin.Short,
      override val type: Value,
    ) : Term

    data class Int(
      override val type: Value,
    ) : Term

    data class IntOf(
      val value: kotlin.Int,
      override val type: Value,
    ) : Term

    data class Long(
      override val type: Value,
    ) : Term

    data class LongOf(
      val value: kotlin.Long,
      override val type: Value,
    ) : Term

    data class Float(
      override val type: Value,
    ) : Term

    data class FloatOf(
      val value: kotlin.Float,
      override val type: Value,
    ) : Term

    data class Double(
      override val type: Value,
    ) : Term

    data class DoubleOf(
      val value: kotlin.Double,
      override val type: Value,
    ) : Term

    data class String(
      override val type: Value,
    ) : Term

    data class StringOf(
      val value: kotlin.String,
      override val type: Value,
    ) : Term

    data class ByteArray(
      override val type: Value,
    ) : Term

    data class ByteArrayOf(
      val elements: kotlin.collections.List<Term>,
      override val type: Value,
    ) : Term

    data class IntArray(
      override val type: Value,
    ) : Term

    data class IntArrayOf(
      val elements: kotlin.collections.List<Term>,
      override val type: Value,
    ) : Term

    data class LongArray(
      override val type: Value,
    ) : Term

    data class LongArrayOf(
      val elements: kotlin.collections.List<Term>,
      override val type: Value,
    ) : Term

    data class List(
      val element: Term,
      override val type: Value,
    ) : Term

    data class ListOf(
      val elements: kotlin.collections.List<Term>,
      override val type: Value,
    ) : Term

    data class Compound(
      val elements: Map<kotlin.String, Term>,
      override val type: Value,
    ) : Term

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
      override val type: Value,
    ) : Term

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
      override val type: Value,
    ) : Term

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
      val body: Term,
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
      override val type: Value,
    ) : Pattern

    data class IntRangeOf(
      val value: IntRange,
      override val type: Value,
    ) : Pattern

    data class CompoundOf(
      val elements: Map<String, Pattern>,
      override val type: Value,
    ) : Pattern

    data class Splice(
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

    object EndTag : Value

    object ByteTag : Value

    object ShortTag : Value

    object IntTag : Value

    object LongTag : Value

    object FloatTag : Value

    object DoubleTag : Value

    object StringTag : Value

    object ByteArrayTag : Value

    object IntArrayTag : Value

    object LongArrayTag : Value

    object ListTag : Value

    object CompoundTag : Value

    @JvmInline
    value class Type(
      val tag: Lazy<Value>,
    ) : Value

    object Bool : Value

    @JvmInline
    value class BoolOf(
      val value: Boolean,
    ) : Value

    class If(
      val condition: Value,
      val thenBranch: Lazy<Value>,
      val elseBranch: Lazy<Value>,
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

    @JvmInline
    value class ListOf(
      val elements: kotlin.collections.List<Lazy<Value>>,
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

    @JvmInline
    value class FuncOf(
      val result: Closure,
    ) : Value

    class Apply(
      val func: Value,
      val args: kotlin.collections.List<Lazy<Value>>,
    ) : Value

    @JvmInline
    value class Code(
      val element: Lazy<Value>,
    ) : Value

    @JvmInline
    value class CodeOf(
      val element: Lazy<Value>,
    ) : Value

    @JvmInline
    value class Splice(
      val element: Lazy<Value>,
    ) : Value

    class Var(
      val name: kotlin.String,
      val level: kotlin.Int,
    ) : Value

    class Meta(
      val index: kotlin.Int,
      val source: Range,
    ) : Value

    object Hole : Value
  }
}
