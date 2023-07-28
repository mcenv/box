package box.ast

import box.ast.common.*
import box.ast.common.Annotation
import org.eclipse.lsp4j.Range

object Core {
  data class Module(
    val name: ModuleLocation,
    val imports: List<DefinitionLocation>,
    val definitions: LinkedHashMap<DefinitionLocation, Definition>,
  )

  sealed class Definition {
    abstract val doc: String
    abstract val annotations: List<Annotation>
    abstract val modifiers: List<Modifier>
    abstract val name: DefinitionLocation

    data class Def(
      override val doc: String,
      override val annotations: List<Annotation>,
      override val modifiers: List<Modifier>,
      override val name: DefinitionLocation,
      val type: Term,
      val body: Term,
    ) : Definition()
  }

  /**
   * A well-typed term.
   */
  sealed class Term {
    data object Tag : Term()

    data class TagOf(
      val repr: Repr,
    ) : Term()

    data class Type(
      val element: Term,
    ) : Term()

    data object Unit : Term()

    data object Bool : Term()

    data object I8 : Term()

    data object I16 : Term()

    data object I32 : Term()

    data object I64 : Term()

    data object F32 : Term()

    data object F64 : Term()

    data object Wtf16 : Term()

    data class ConstOf<out T>(
      val value: T & Any,
    ) : Term()

    data object I8Array : Term()

    data class I8ArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term()

    data object I32Array : Term()

    data class I32ArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term()

    data object I64Array : Term()

    data class I64ArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term()

    data class List(
      val element: Term,
    ) : Term()

    data class ListOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term()

    data class Compound(
      val elements: LinkedHashMap<String, Term>,
    ) : Term()

    data class CompoundOf(
      val elements: LinkedHashMap<String, Term>,
    ) : Term()

    data class Point(
      val elementType: Term,
      val element: Term,
    ) : Term()

    data class Union(
      val elements: kotlin.collections.List<Term>,
    ) : Term()

    data class Func(
      val open: Boolean,
      val params: kotlin.collections.List<Pair<Pattern, Term>>,
      val result: Term,
    ) : Term()

    data class FuncOf(
      val open: Boolean,
      val params: kotlin.collections.List<Pair<Pattern, Term>>,
      val result: Term,
    ) : Term()

    data class Apply(
      val open: Boolean,
      val func: Term,
      val args: kotlin.collections.List<Term>,
    ) : Term()

    data class Code(
      val element: Term,
    ) : Term()

    data class CodeOf(
      val element: Term,
    ) : Term()

    data class Splice(
      val element: Term,
    ) : Term()

    data class Path(
      val element: Term,
    ) : Term()

    data class PathOf(
      val element: Term,
    ) : Term()

    data class Get(
      val element: Term,
    ) : Term()

    data class Command(
      val element: Term,
      val type: Term,
    ) : Term()

    data class Let(
      val binder: Pattern,
      val init: Term,
      val body: Term,
    ) : Term()

    data class If(
      val scrutinee: Term,
      val branches: kotlin.collections.List<Pair<Pattern, Term>>,
    ) : Term()

    data class Project(
      val target: Term,
      val projs: kotlin.collections.List<Proj>,
    ) : Term()

    data class Var(
      val name: String,
      val idx: Idx,
    ) : Term()

    data class Def(
      val def: Definition.Def,
    ) : Term()

    data class Meta(
      val index: Int,
      val source: Range,
    ) : Term()

    data class Builtin(
      val builtin: box.pass.Builtin,
    ) : Term()

    data object Hole : Term()
  }

  /**
   * A well-typed pattern.
   */
  sealed class Pattern {
    data class ConstOf(
      val value: Any,
    ) : Pattern()

    data class I8ArrayOf(
      val elements: List<Pattern>,
    ) : Pattern()

    data class I32ArrayOf(
      val elements: List<Pattern>,
    ) : Pattern()

    data class I64ArrayOf(
      val elements: List<Pattern>,
    ) : Pattern()

    data class ListOf(
      val elements: List<Pattern>,
    ) : Pattern()

    data class CompoundOf(
      val elements: LinkedHashMap<String, Pattern>,
    ) : Pattern()

    data class Var(
      val name: String,
    ) : Pattern()

    data object Drop : Pattern()

    data object Hole : Pattern()
  }
}
