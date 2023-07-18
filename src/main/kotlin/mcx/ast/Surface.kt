package mcx.ast

import mcx.ast.common.*
import mcx.ast.common.Annotation
import mcx.lsp.Ranged
import org.eclipse.lsp4j.Range

object Surface {
  data class Module(
    val name: ModuleLocation,
    val imports: List<Ranged<DefinitionLocation>>,
    val definitions: List<Definition>,
  )

  sealed class Definition {
    abstract val doc: String
    abstract val annotations: List<Ranged<Annotation>>
    abstract val modifiers: List<Ranged<Modifier>>
    abstract val name: Ranged<String>
    abstract val range: Range

    data class Def(
      override val doc: String,
      override val annotations: List<Ranged<Annotation>>,
      override val modifiers: List<Ranged<Modifier>>,
      override val name: Ranged<String>,
      val type: Term,
      val body: Term?,
      override val range: Range,
    ) : Definition()

    data class Hole(
      override val range: Range,
    ) : Definition() {
      override val doc: String get() = throw IllegalStateException()
      override val annotations: List<Ranged<Annotation>> get() = throw IllegalStateException()
      override val modifiers: List<Ranged<Modifier>> get() = throw IllegalStateException()
      override val name: Ranged<String> get() = throw IllegalStateException()
    }
  }

  /**
   * A term or pattern that may not be well-typed.
   */
  sealed class Term {
    abstract val range: Range

    data class Tag(
      override val range: Range,
    ) : Term()

    data class TagOf(
      val repr: Repr,
      override val range: Range,
    ) : Term()

    data class Type(
      val element: Term,
      override val range: Range,
    ) : Term()

    data class Unit(
      override val range: Range,
    ) : Term()

    data class UnitOf(
      override val range: Range,
    ) : Term()

    data class Bool(
      override val range: Range,
    ) : Term()

    data class BoolOf(
      val value: Boolean,
      override val range: Range,
    ) : Term()

    data class I8(
      override val range: Range,
    ) : Term()

    data class I8Of(
      val value: Byte,
      override val range: Range,
    ) : Term()

    data class I16(
      override val range: Range,
    ) : Term()

    data class I16Of(
      val value: Short,
      override val range: Range,
    ) : Term()

    data class I32(
      override val range: Range,
    ) : Term()

    data class I32Of(
      val value: Int,
      override val range: Range,
    ) : Term()

    data class I64(
      override val range: Range,
    ) : Term()

    data class I64Of(
      val value: Long,
      override val range: Range,
    ) : Term()

    data class F32(
      override val range: Range,
    ) : Term()

    data class F32Of(
      val value: Float,
      override val range: Range,
    ) : Term()

    data class F64(
      override val range: Range,
    ) : Term()

    data class F64Of(
      val value: Double,
      override val range: Range,
    ) : Term()

    data class Wtf16(
      override val range: Range,
    ) : Term()

    data class Wtf16Of(
      val value: String,
      override val range: Range,
    ) : Term()

    data class I8Array(
      override val range: Range,
    ) : Term()

    data class I8ArrayOf(
      val elements: kotlin.collections.List<Term>,
      override val range: Range,
    ) : Term()

    data class I32Array(
      override val range: Range,
    ) : Term()

    data class I32ArrayOf(
      val elements: kotlin.collections.List<Term>,
      override val range: Range,
    ) : Term()

    data class I64Array(
      override val range: Range,
    ) : Term()

    data class I64ArrayOf(
      val elements: kotlin.collections.List<Term>,
      override val range: Range,
    ) : Term()

    data class List(
      val element: Term,
      override val range: Range,
    ) : Term()

    data class ListOf(
      val elements: kotlin.collections.List<Term>,
      override val range: Range,
    ) : Term()

    data class Compound(
      val elements: kotlin.collections.List<Pair<Ranged<String>, Term>>,
      override val range: Range,
    ) : Term()

    data class CompoundOf(
      val elements: kotlin.collections.List<Pair<Ranged<String>, Term>>,
      override val range: Range,
    ) : Term()

    data class Point(
      val element: Term,
      override val range: Range,
    ) : Term()

    data class Union(
      val elements: kotlin.collections.List<Term>,
      override val range: Range,
    ) : Term()

    data class Func(
      val open: Boolean,
      val params: kotlin.collections.List<Pair<Term, Term>>,
      val result: Term,
      override val range: Range,
    ) : Term()

    data class FuncOf(
      val open: Boolean,
      val params: kotlin.collections.List<Term>,
      val result: Term,
      override val range: Range,
    ) : Term()

    data class Apply(
      val func: Term,
      val args: kotlin.collections.List<Term>,
      override val range: Range,
    ) : Term()

    data class Code(
      val element: Term,
      override val range: Range,
    ) : Term()

    data class CodeOf(
      val element: Term,
      override val range: Range,
    ) : Term()

    data class Splice(
      val element: Term,
      override val range: Range,
    ) : Term()

    data class Command(
      val element: Term,
      override val range: Range,
    ) : Term()

    data class Let(
      val binder: Term,
      val init: Term,
      val body: Term,
      override val range: Range,
    ) : Term()

    data class If(
      val scrutinee: Term,
      val branches: kotlin.collections.List<Pair<Term, Term>>,
      override val range: Range,
    ) : Term()

    data class Var(
      val name: String,
      override val range: Range,
    ) : Term()

    data class As(
      val element: Term,
      val type: Term,
      override val range: Range,
    ) : Term()

    data class Hole(
      override val range: Range,
    ) : Term()
  }
}
