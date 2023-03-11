package mcx.ast

import mcx.data.NbtType
import mcx.lsp.Ranged
import org.eclipse.lsp4j.Range

object Resolved {
  data class Module(
    val name: ModuleLocation,
    val imports: List<Ranged<DefinitionLocation>>,
    val definitions: Map<DefinitionLocation, Definition>,
  )

  sealed interface Definition {
    val modifiers: List<Ranged<Modifier>>
    val name: Ranged<DefinitionLocation>
    val range: Range

    data class Def(
      override val modifiers: List<Ranged<Modifier>>,
      override val name: Ranged<DefinitionLocation>,
      val type: Term,
      val body: Term?,
      override val range: Range,
    ) : Definition

    data class Hole(
      override val range: Range,
    ) : Definition {
      override val modifiers: List<Ranged<Modifier>> get() = throw IllegalStateException()
      override val name: Ranged<DefinitionLocation> get() = throw IllegalStateException()
    }
  }

  sealed interface Term {
    val range: Range

    data class Tag(
      override val range: Range,
    ) : Term

    data class TagOf(
      val value: NbtType,
      override val range: Range,
    ) : Term

    data class Type(
      val tag: Term,
      override val range: Range,
    ) : Term

    data class Bool(
      override val range: Range,
    ) : Term

    data class BoolOf(
      val value: Boolean,
      override val range: Range,
    ) : Term

    data class If(
      val condition: Term,
      val thenBranch: Term,
      val elseBranch: Term,
      override val range: Range,
    ) : Term

    data class Is(
      val scrutinee: Term,
      val scrutineer: Pattern,
      override val range: Range,
    ) : Term

    data class Byte(
      override val range: Range,
    ) : Term

    data class ByteOf(
      val value: kotlin.Byte,
      override val range: Range,
    ) : Term

    data class Short(
      override val range: Range,
    ) : Term

    data class ShortOf(
      val value: kotlin.Short,
      override val range: Range,
    ) : Term

    data class Int(
      override val range: Range,
    ) : Term

    data class IntOf(
      val value: kotlin.Int,
      override val range: Range,
    ) : Term

    data class Long(
      override val range: Range,
    ) : Term

    data class LongOf(
      val value: kotlin.Long,
      override val range: Range,
    ) : Term

    data class Float(
      override val range: Range,
    ) : Term

    data class FloatOf(
      val value: kotlin.Float,
      override val range: Range,
    ) : Term

    data class Double(
      override val range: Range,
    ) : Term

    data class DoubleOf(
      val value: kotlin.Double,
      override val range: Range,
    ) : Term

    data class String(
      override val range: Range,
    ) : Term

    data class StringOf(
      val value: kotlin.String,
      override val range: Range,
    ) : Term

    data class ByteArray(
      override val range: Range,
    ) : Term

    data class ByteArrayOf(
      val elements: kotlin.collections.List<Term>,
      override val range: Range,
    ) : Term

    data class IntArray(
      override val range: Range,
    ) : Term

    data class IntArrayOf(
      val elements: kotlin.collections.List<Term>,
      override val range: Range,
    ) : Term

    data class LongArray(
      override val range: Range,
    ) : Term

    data class LongArrayOf(
      val elements: kotlin.collections.List<Term>,
      override val range: Range,
    ) : Term

    data class List(
      val element: Term,
      override val range: Range,
    ) : Term

    data class ListOf(
      val elements: kotlin.collections.List<Term>,
      override val range: Range,
    ) : Term

    data class Compound(
      val elements: kotlin.collections.List<Pair<Ranged<kotlin.String>, Term>>,
      override val range: Range,
    ) : Term

    data class CompoundOf(
      val elements: kotlin.collections.List<Pair<Ranged<kotlin.String>, Term>>,
      override val range: Range,
    ) : Term

    data class Union(
      val elements: kotlin.collections.List<Term>,
      override val range: Range,
    ) : Term

    data class Func(
      val params: kotlin.collections.List<Pair<Pattern, Term>>,
      val result: Term,
      override val range: Range,
    ) : Term

    data class FuncOf(
      val params: kotlin.collections.List<Pattern>,
      val result: Term,
      override val range: Range,
    ) : Term

    data class Apply(
      val func: Term,
      val args: kotlin.collections.List<Term>,
      override val range: Range,
    ) : Term

    data class Code(
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

    data class Let(
      val binder: Pattern,
      val init: Term,
      val body: Term,
      override val range: Range,
    ) : Term

    data class Var(
      val name: kotlin.String,
      val level: kotlin.Int,
      override val range: Range,
    ) : Term

    data class Def(
      val name: DefinitionLocation,
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

    data class CompoundOf(
      val elements: List<Pair<Ranged<String>, Pattern>>,
      override val range: Range,
    ) : Pattern

    data class CodeOf(
      val element: Pattern,
      override val range: Range,
    ) : Pattern

    data class Var(
      val name: String,
      val level: Int,
      override val range: Range,
    ) : Pattern

    data class Drop(
      override val range: Range,
    ) : Pattern

    data class Anno(
      val element: Pattern,
      val type: Term,
      override val range: Range,
    ) : Pattern

    data class Hole(
      override val range: Range,
    ) : Pattern
  }
}
