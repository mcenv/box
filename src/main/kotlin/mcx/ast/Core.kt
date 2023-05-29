package mcx.ast

import mcx.data.NbtType
import org.eclipse.lsp4j.Range

object Core {
  data class Module(
    val name: ModuleLocation,
    val definitions: List<Definition>,
  )

  sealed class Definition {
    abstract val annotations: List<Annotation>
    abstract val modifiers: List<Modifier>
    abstract val name: DefinitionLocation

    data class Def(
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

    data object Byte : Term()

    data class ByteOf(
      val value: kotlin.Byte,
    ) : Term()

    data object Short : Term()

    data class ShortOf(
      val value: kotlin.Short,
    ) : Term()

    data object Int : Term()

    data class IntOf(
      val value: kotlin.Int,
    ) : Term()

    data object Long : Term()

    data class LongOf(
      val value: kotlin.Long,
    ) : Term()

    data object Float : Term()

    data class FloatOf(
      val value: kotlin.Float,
    ) : Term()

    data object Double : Term()

    data class DoubleOf(
      val value: kotlin.Double,
    ) : Term()

    data object String : Term()

    data class StringOf(
      val value: kotlin.String,
    ) : Term()

    data object ByteArray : Term()

    data class ByteArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term()

    data object IntArray : Term()

    data class IntArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term()

    data object LongArray : Term()

    data class LongArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term()

    data class List(
      val element: Term,
    ) : Term()

    data class ListOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term()

    data class Compound(
      val elements: LinkedHashMap<kotlin.String, Term>,
    ) : Term()

    data class CompoundOf(
      val elements: LinkedHashMap<kotlin.String, Term>,
    ) : Term()

    data class Point(
      val element: Term,
      val elementType: Term,
    ) : Term()

    data class Union(
      val elements: kotlin.collections.List<Term>,
    ) : Term()

    data class Func(
      val open: Boolean,
      val params: kotlin.collections.List<Pair<Pattern<Term>, Term>>,
      val result: Term,
    ) : Term()

    data class FuncOf(
      val open: Boolean,
      val params: kotlin.collections.List<Pattern<Term>>,
      val result: Term,
    ) : Term()

    data class Apply(
      val open: Boolean,
      val func: Term,
      val args: kotlin.collections.List<Term>,
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
      val name: kotlin.String,
      val idx: Idx,
      val type: Term,
    ) : Term()

    // TODO: store [Definition.Def] instead?
    data class Def(
      val builtin: Boolean,
      val name: DefinitionLocation,
      val body: Term?,
      val type: Term,
    ) : Term()

    data class Meta(
      val index: kotlin.Int,
      val source: Range,
    ) : Term()

    data object Hole : Term()
  }

  /**
   * A well-typed pattern.
   */
  sealed class Pattern<out T> {
    data class IntOf(
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
