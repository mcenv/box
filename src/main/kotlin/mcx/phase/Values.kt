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
 * [Value]s should only be used for calculations, not for storage.
 */
sealed interface Value {
  object Tag : Value

  @JvmInline
  value class TagOf(
    val value: NbtType,
  ) : Value

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

  class FuncOf(
    val result: Closure,
  ) : Value

  class Apply(
    val func: Value,
    val args: kotlin.collections.List<Lazy<Value>>,
  ) : Value

  class Let(
    val binder: Pattern,
    val init: Value,
    val body: Value,
  ) : Value

  data class Var(
    val name: kotlin.String,
    val lvl: Lvl,
  ) : Value

  class Def(
    val name: DefinitionLocation,
    val body: Term?,
  ) : Value

  class Meta(
    val index: kotlin.Int,
    val source: Range,
  ) : Value

  object Hole : Value
}

typealias Env = PersistentList<Lazy<Value>>

class Closure(
  val env: Env,
  val binders: List<Pattern>,
  val body: Term,
)
