package mcx.ast

import mcx.data.NbtType
import org.eclipse.lsp4j.Range

object Core {
  data class Module(
    val name: ModuleLocation,
    val definitions: List<Definition>,
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
      val body: Term?,
    ) : Definition()
  }

  /**
   * A well-typed term.
   */
  sealed class Term {
    data object Tag : Term()

    data class TagOf(
      val value: NbtType,
    ) : Term()

    data class Type(
      val element: Term,
    ) : Term()

    data object Bool : Term()

    data class BoolOf(
      val value: Boolean,
    ) : Term()

    data class If(
      val condition: Term,
      val thenBranch: Term,
      val elseBranch: Term,
    ) : Term()

    data class Is(
      val scrutinee: Term,
      val scrutineer: Pattern<Term>,
    ) : Term()

    data object I8 : Term()

    data class I8Of(
      val value: Byte,
    ) : Term()

    data object I16 : Term()

    data class I16Of(
      val value: Short,
    ) : Term()

    data object I32 : Term()

    data class I32Of(
      val value: Int,
    ) : Term()

    data object I64 : Term()

    data class I64Of(
      val value: Long,
    ) : Term()

    data object F32 : Term()

    data class F32Of(
      val value: Float,
    ) : Term()

    data object F64 : Term()

    data class F64Of(
      val value: Double,
    ) : Term()

    data object Str : Term()

    data class StrOf(
      val value: String,
    ) : Term()

    data object I8Array : Term()

    data class I8ArrayOf(
      val elements: List<Term>,
    ) : Term()

    data object I32Array : Term()

    data class I32ArrayOf(
      val elements: List<Term>,
    ) : Term()

    data object I64Array : Term()

    data class I64ArrayOf(
      val elements: List<Term>,
    ) : Term()

    data class Vec(
      val element: Term,
    ) : Term()

    data class VecOf(
      val elements: List<Term>,
    ) : Term()

    data class Struct(
      val elements: LinkedHashMap<String, Term>,
    ) : Term()

    data class StructOf(
      val elements: LinkedHashMap<String, Term>,
    ) : Term()

    data class Point(
      val element: Term,
      val elementType: Term,
    ) : Term()

    data class Union(
      val elements: List<Term>,
    ) : Term()

    data class Func(
      val open: Boolean,
      val params: List<Pair<Pattern<Term>, Term>>,
      val result: Term,
    ) : Term()

    data class FuncOf(
      val open: Boolean,
      val params: List<Pattern<Term>>,
      val result: Term,
    ) : Term()

    data class Apply(
      val open: Boolean,
      val func: Term,
      val args: List<Term>,
      val type: Term,
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

    data class Command(
      val element: Term,
      val type: Term,
    ) : Term()

    data class Let(
      val binder: Pattern<Term>,
      val init: Term,
      val body: Term,
    ) : Term()

    data class Var(
      val name: String,
      val idx: Idx,
      val type: Term,
    ) : Term()

    data class Def(
      val def: Definition.Def,
    ) : Term()

    data class Meta(
      val index: Int,
      val source: Range,
    ) : Term()

    data object Hole : Term()
  }

  /**
   * A well-typed pattern.
   */
  sealed class Pattern<out T> {
    data class I32Of(
      val value: Int,
    ) : Pattern<Nothing>()

    data class CompoundOf<T>(
      val elements: LinkedHashMap<String, Pattern<T>>,
    ) : Pattern<T>()

    data class Var<T>(
      val name: String,
      val type: T,
    ) : Pattern<T>()

    data class Drop<T>(
      val type: T,
    ) : Pattern<T>()

    data object Hole : Pattern<Nothing>()
  }
}
