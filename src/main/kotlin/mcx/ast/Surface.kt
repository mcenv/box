package mcx.ast

import mcx.data.NbtType
import mcx.lsp.Ranged
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

    data class Def(
      override val modifiers: List<Ranged<Modifier>>,
      override val name: Ranged<String>,
      val type: Term,
      val body: Term?,
      override val range: Range,
    ) : Definition

    data class Hole(
      override val range: Range,
    ) : Definition {
      override val modifiers: List<Ranged<Modifier>> get() = throw IllegalStateException()
      override val name: Ranged<String> get() = throw IllegalStateException()
    }
  }

  /**
   * A term or pattern that may not be well-typed.
   */
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
      val scrutineer: Term,
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
      val params: kotlin.collections.List<Pair<Term, Term>>,
      val result: Term,
      override val range: Range,
    ) : Term

    data class FuncOf(
      val params: kotlin.collections.List<Term>,
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
      val binder: Term,
      val init: Term,
      val body: Term,
      override val range: Range,
    ) : Term

    data class Var(
      val name: kotlin.String,
      override val range: Range,
    ) : Term

    data class As(
      val element: Term,
      val type: Term,
      override val range: Range,
    ) : Term

    data class Hole(
      override val range: Range,
    ) : Term
  }
}
