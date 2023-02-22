package mcx.ast

import mcx.util.Ranged
import org.eclipse.lsp4j.Range

object Surface {
  data class Module(
    val name: ModuleLocation,
    val imports: List<Ranged<DefinitionLocation>>,
    val definitions: List<Definition>,
  )

  sealed interface Definition {
    val modifiers: List<Ranged<Modifier>>
    val name: Ranged<String>
    val range: Range

    data class Function(
      override val modifiers: List<Ranged<Modifier>>,
      override val name: Ranged<String>,
      val typeParams: List<String>,
      val binder: Pattern,
      val result: Surface.Type,
      val body: Term?,
      override val range: Range,
    ) : Definition

    data class Type(
      override val modifiers: List<Ranged<Modifier>>,
      override val name: Ranged<String>,
      val kind: Kind,
      val body: Surface.Type,
      override val range: Range,
    ) : Definition

    data class Test(
      override val modifiers: List<Ranged<Modifier>>,
      override val name: Ranged<String>,
      val body: Term,
      override val range: Range,
    ) : Definition

    data class Hole(
      override val range: Range,
    ) : Definition {
      override val modifiers: List<Ranged<Modifier>> get() = throw IllegalStateException()
      override val name: Ranged<String> get() = throw IllegalStateException()
    }
  }

  sealed interface Kind {
    val range: Range

    data class Type(
      val arity: Int,
      override val range: Range,
    ) : Kind

    data class Hole(
      override val range: Range,
    ) : Kind
  }

  sealed interface Type {
    val range: Range

    data class Bool(
      val value: Boolean?,
      override val range: Range,
    ) : Type

    data class Byte(
      val value: kotlin.Byte?,
      override val range: Range,
    ) : Type

    data class Short(
      val value: kotlin.Short?,
      override val range: Range,
    ) : Type

    data class Int(
      val value: kotlin.Int?,
      override val range: Range,
    ) : Type

    data class Long(
      val value: kotlin.Long?,
      override val range: Range,
    ) : Type

    data class Float(
      val value: kotlin.Float?,
      override val range: Range,
    ) : Type

    data class Double(
      val value: kotlin.Double?,
      override val range: Range,
    ) : Type

    data class String(
      val value: kotlin.String?,
      override val range: Range,
    ) : Type

    data class ByteArray(
      override val range: Range,
    ) : Type

    data class IntArray(
      override val range: Range,
    ) : Type

    data class LongArray(
      override val range: Range,
    ) : Type

    data class List(
      val element: Type,
      override val range: Range,
    ) : Type

    data class Compound(
      val elements: Map<kotlin.String, Type>,
      override val range: Range,
    ) : Type

    data class Tuple(
      val elements: kotlin.collections.List<Type>,
      override val range: Range,
    ) : Type

    data class Func(
      val param: Type,
      val result: Type,
      override val range: Range,
    ) : Type

    data class Clos(
      val param: Type,
      val result: Type,
      override val range: Range,
    ) : Type

    data class Union(
      val elements: kotlin.collections.List<Type>,
      override val range: Range,
    ) : Type

    data class Code(
      val element: Type,
      override val range: Range,
    ) : Type

    data class Var(
      val name: kotlin.String,
      override val range: Range,
    ) : Type

    data class Meta(
      override val range: Range,
    ) : Type

    data class Hole(
      override val range: Range,
    ) : Type
  }

  sealed interface Term {
    val range: Range

    data class BoolOf(
      val value: Boolean,
      override val range: Range,
    ) : Term

    data class ByteOf(
      val value: Byte,
      override val range: Range,
    ) : Term

    data class ShortOf(
      val value: Short,
      override val range: Range,
    ) : Term

    data class IntOf(
      val value: Int,
      override val range: Range,
    ) : Term

    data class LongOf(
      val value: Long,
      override val range: Range,
    ) : Term

    data class FloatOf(
      val value: Float,
      override val range: Range,
    ) : Term

    data class DoubleOf(
      val value: Double,
      override val range: Range,
    ) : Term

    data class StringOf(
      val parts: List<Part>,
      override val range: Range,
    ) : Term {
      sealed interface Part {
        data class Raw(val value: String) : Part
        data class Interpolate(val element: Term) : Part
      }
    }

    data class ByteArrayOf(
      val elements: List<Term>,
      override val range: Range,
    ) : Term

    data class IntArrayOf(
      val elements: List<Term>,
      override val range: Range,
    ) : Term

    data class LongArrayOf(
      val elements: List<Term>,
      override val range: Range,
    ) : Term

    data class ListOf(
      val elements: List<Term>,
      override val range: Range,
    ) : Term

    data class CompoundOf(
      val elements: List<Pair<Ranged<String>, Term>>,
      override val range: Range,
    ) : Term

    data class TupleOf(
      val elements: List<Term>,
      override val range: Range,
    ) : Term

    data class FuncOf(
      val binder: Pattern,
      val body: Term,
      override val range: Range,
    ) : Term

    data class ClosOf(
      val binder: Pattern,
      val body: Term,
      override val range: Range,
    ) : Term

    data class Apply(
      val operator: Term,
      val operand: Term,
      override val range: Range,
    ) : Term

    data class If(
      val condition: Term,
      val thenClause: Term,
      val elseClause: Term,
      override val range: Range,
    ) : Term

    data class Let(
      val binder: Pattern,
      val init: Term,
      val body: Term,
      override val range: Range,
    ) : Term

    data class Var(
      val name: String,
      override val range: Range,
    ) : Term

    data class Run(
      val name: Ranged<String>,
      val typeArgs: Ranged<List<Type>>,
      val arg: Term,
      override val range: Range,
    ) : Term

    data class Is(
      val scrutinee: Term,
      val scrutineer: Pattern,
      override val range: Range,
    ) : Term

    data class Command(
      val element: Term,
      override val range: Range,
    ) : Term

    data class CodeOf(
      val element: Term,
      override val range: Range,
    ) : Term

    data class Splice(
      val element: Term,
      override val range: Range,
    ) : Term

    data class Hole(
      override val range: Range,
    ) : Term
  }

  sealed interface Pattern {
    val range: Range

    data class IntOf(
      val value: Int,
      override val range: Range,
    ) : Pattern

    data class IntRangeOf(
      val min: Int,
      val max: Int,
      override val range: Range,
    ) : Pattern

    data class ListOf(
      val elements: List<Pattern>,
      override val range: Range,
    ) : Pattern

    data class CompoundOf(
      val elements: List<Pair<Ranged<String>, Pattern>>,
      override val range: Range,
    ) : Pattern

    data class TupleOf(
      val elements: List<Pattern>,
      override val range: Range,
    ) : Pattern

    data class Var(
      val name: String,
      override val range: Range,
    ) : Pattern

    data class Drop(
      override val range: Range,
    ) : Pattern

    data class Anno(
      val element: Pattern,
      val type: Type,
      override val range: Range,
    ) : Pattern

    data class Hole(
      override val range: Range,
    ) : Pattern
  }
}
