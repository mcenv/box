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
    object Tag : Term {
      override fun toString(): kotlin.String = "Tag"
    }

    data class TagOf(
      val value: NbtType,
    ) : Term

    data class Type(
      val element: Term,
    ) : Term

    object Bool : Term {
      override fun toString(): kotlin.String = "Bool"
    }

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
      val scrutineer: Pattern<Term>,
    ) : Term

    object Byte : Term {
      override fun toString(): kotlin.String = "Byte"
    }

    data class ByteOf(
      val value: kotlin.Byte,
    ) : Term

    object Short : Term {
      override fun toString(): kotlin.String = "Short"
    }

    data class ShortOf(
      val value: kotlin.Short,
    ) : Term

    object Int : Term {
      override fun toString(): kotlin.String = "Int"
    }

    data class IntOf(
      val value: kotlin.Int,
    ) : Term

    object Long : Term {
      override fun toString(): kotlin.String = "Long"
    }

    data class LongOf(
      val value: kotlin.Long,
    ) : Term

    object Float : Term {
      override fun toString(): kotlin.String = "Float"
    }

    data class FloatOf(
      val value: kotlin.Float,
    ) : Term

    object Double : Term {
      override fun toString(): kotlin.String = "Double"
    }

    data class DoubleOf(
      val value: kotlin.Double,
    ) : Term

    object String : Term {
      override fun toString(): kotlin.String = "String"
    }

    data class StringOf(
      val value: kotlin.String,
    ) : Term

    object ByteArray : Term {
      override fun toString(): kotlin.String = "ByteArray"
    }

    data class ByteArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term

    object IntArray : Term {
      override fun toString(): kotlin.String = "IntArray"
    }

    data class IntArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term

    object LongArray : Term {
      override fun toString(): kotlin.String = "LongArray"
    }

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
      val params: kotlin.collections.List<Pair<Pattern<Term>, Term>>,
      val result: Term,
    ) : Term

    data class FuncOf(
      val params: kotlin.collections.List<Pattern<Term>>,
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
      val binder: Pattern<Term>,
      val init: Term,
      val body: Term,
    ) : Term

    data class Var(
      val name: kotlin.String,
      val idx: Idx,
      val type: Term,
    ) : Term

    data class Def(
      val name: DefinitionLocation,
      val body: Term?,
    ) : Term

    data class Meta(
      val index: kotlin.Int,
      val source: Range,
    ) : Term

    object Hole : Term {
      override fun toString(): kotlin.String = "Hole"
    }
  }

  /**
   * A well-typed pattern.
   */
  sealed interface Pattern<out T> {
    data class IntOf(
      val value: Int,
    ) : Pattern<Nothing>

    data class CompoundOf<T>(
      val elements: List<Pair<String, Pattern<T>>>,
    ) : Pattern<T>

    data class Var<T>(
      val name: String,
      val type: T,
    ) : Pattern<T>

    data class Drop<T>(
      val type: T,
    ) : Pattern<T>

    object Hole : Pattern<Nothing> {
      override fun toString(): String = "Hole"
    }
  }
}
