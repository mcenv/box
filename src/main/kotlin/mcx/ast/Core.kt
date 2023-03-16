package mcx.ast

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

  /**
   * A well-typed term.
   */
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
      val idx: Idx,
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

  /**
   * A well-typed pattern.
   */
  sealed interface Pattern {
    data class IntOf(
      val value: Int,
    ) : Pattern

    data class CompoundOf(
      val elements: List<Pair<String, Pattern>>,
    ) : Pattern

    data class CodeOf(
      val element: Pattern,
    ) : Pattern

    data class Var(
      val name: String,
    ) : Pattern

    object Drop : Pattern

    object Hole : Pattern
  }
}
