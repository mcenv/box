package mcx.phase

import kotlinx.collections.immutable.PersistentList
import mcx.ast.Core.Kind
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
  val kind: Kind

  object Tag : Value {
    override val kind: Kind get() = Kind.TYPE_BYTE
  }

  @JvmInline
  value class TagOf(
    val value: NbtType,
  ) : Value {
    override val kind: Kind get() = Kind.BYTE
  }

  class Type(
    val element: Lazy<Value>,
    override val kind: Kind,
  ) : Value

  object Bool : Value {
    override val kind: Kind get() = Kind.TYPE_BYTE
  }

  @JvmInline
  value class BoolOf(
    val value: Boolean,
  ) : Value {
    override val kind: Kind get() = Kind.BYTE
  }

  class If(
    val condition: Value,
    val thenBranch: Lazy<Value>,
    val elseBranch: Lazy<Value>,
    override val kind: Kind,
  ) : Value

  class Is(
    val scrutinee: Lazy<Value>,
    val scrutineer: Pattern,
  ) : Value {
    override val kind: Kind get() = Kind.BYTE
  }

  object Byte : Value {
    override val kind: Kind get() = Kind.TYPE_BYTE
  }

  @JvmInline
  value class ByteOf(
    val value: kotlin.Byte,
  ) : Value {
    override val kind: Kind get() = Kind.BYTE
  }

  object Short : Value {
    override val kind: Kind get() = Kind.TYPE_SHORT
  }

  @JvmInline
  value class ShortOf(
    val value: kotlin.Short,
  ) : Value {
    override val kind: Kind get() = Kind.SHORT
  }

  object Int : Value {
    override val kind: Kind get() = Kind.TYPE_INT
  }

  @JvmInline
  value class IntOf(
    val value: kotlin.Int,
  ) : Value {
    override val kind: Kind get() = Kind.INT
  }

  object Long : Value {
    override val kind: Kind get() = Kind.TYPE_LONG
  }

  @JvmInline
  value class LongOf(
    val value: kotlin.Long,
  ) : Value {
    override val kind: Kind get() = Kind.LONG
  }

  object Float : Value {
    override val kind: Kind get() = Kind.TYPE_FLOAT
  }

  @JvmInline
  value class FloatOf(
    val value: kotlin.Float,
  ) : Value {
    override val kind: Kind get() = Kind.FLOAT
  }

  object Double : Value {
    override val kind: Kind get() = Kind.TYPE_DOUBLE
  }

  @JvmInline
  value class DoubleOf(
    val value: kotlin.Double,
  ) : Value {
    override val kind: Kind get() = Kind.DOUBLE
  }

  object String : Value {
    override val kind: Kind get() = Kind.TYPE_STRING
  }

  @JvmInline
  value class StringOf(
    val value: kotlin.String,
  ) : Value {
    override val kind: Kind get() = Kind.STRING
  }

  object ByteArray : Value {
    override val kind: Kind get() = Kind.TYPE_BYTE_ARRAY
  }

  @JvmInline
  value class ByteArrayOf(
    val elements: kotlin.collections.List<Lazy<Value>>,
  ) : Value {
    override val kind: Kind get() = Kind.BYTE_ARRAY
  }

  object IntArray : Value {
    override val kind: Kind get() = Kind.TYPE_INT_ARRAY
  }

  @JvmInline
  value class IntArrayOf(
    val elements: kotlin.collections.List<Lazy<Value>>,
  ) : Value {
    override val kind: Kind get() = Kind.INT_ARRAY
  }

  object LongArray : Value {
    override val kind: Kind get() = Kind.TYPE_LONG_ARRAY
  }

  @JvmInline
  value class LongArrayOf(
    val elements: kotlin.collections.List<Lazy<Value>>,
  ) : Value {
    override val kind: Kind get() = Kind.LONG_ARRAY
  }

  @JvmInline
  value class List(
    val element: Lazy<Value>,
  ) : Value {
    override val kind: Kind get() = Kind.TYPE_LIST
  }

  @JvmInline
  value class ListOf(
    val elements: kotlin.collections.List<Lazy<Value>>,
  ) : Value {
    override val kind: Kind get() = Kind.LIST
  }

  @JvmInline
  value class Compound(
    val elements: Map<kotlin.String, Lazy<Value>>,
  ) : Value {
    override val kind: Kind get() = Kind.TYPE_COMPOUND
  }

  @JvmInline
  value class CompoundOf(
    val elements: Map<kotlin.String, Lazy<Value>>,
  ) : Value {
    override val kind: Kind get() = Kind.COMPOUND
  }

  @JvmInline
  value class Union(
    val elements: kotlin.collections.List<Lazy<Value>>,
  ) : Value {
    override val kind: Kind get() = elements.firstOrNull()?.value?.kind ?: Kind.TYPE_END
  }

  class Func(
    val params: kotlin.collections.List<Lazy<Value>>,
    val result: Closure,
  ) : Value {
    override val kind: Kind get() = Kind.TYPE_COMPOUND
  }

  @JvmInline
  value class FuncOf(
    val result: Closure,
  ) : Value {
    override val kind: Kind get() = Kind.COMPOUND
  }

  class Apply(
    val func: Value,
    val args: kotlin.collections.List<Lazy<Value>>,
    override val kind: Kind,
  ) : Value

  class Var(
    val name: kotlin.String,
    val lvl: Lvl,
    override val kind: Kind,
  ) : Value

  class Def(
    val name: DefinitionLocation,
    val body: Term?,
    override val kind: Kind,
  ) : Value

  class Meta(
    val index: kotlin.Int,
    val source: Range,
    override val kind: Kind,
  ) : Value

  object Hole : Value {
    override val kind: Kind get() = Kind.Hole
  }
}

typealias Env = PersistentList<Lazy<Value>>

class Closure(
  val env: Env,
  val binders: List<Pattern>,
  val body: Term,
)
