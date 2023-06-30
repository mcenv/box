package mcx.ast

import mcx.ast.common.*
import mcx.ast.common.Annotation
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
    /**
     * The type of this term.
     */
    abstract val type: Term

    data object Tag : Term() {
      override val type: Term get() = Type.END
    }

    data class TagOf(
      val repr: Repr,
    ) : Term() {
      override val type: Term get() = Tag
    }

    data class Type(
      val element: Term,
    ) : Term() {
      override val type: Term get() = BYTE

      companion object {
        val END: Type = Type(TagOf(Repr.END))
        val BYTE: Type = Type(TagOf(Repr.BYTE))
        val SHORT: Type = Type(TagOf(Repr.SHORT))
        val INT: Type = Type(TagOf(Repr.INT))
        val LONG: Type = Type(TagOf(Repr.LONG))
        val FLOAT: Type = Type(TagOf(Repr.FLOAT))
        val DOUBLE: Type = Type(TagOf(Repr.DOUBLE))
        val STRING: Type = Type(TagOf(Repr.STRING))
        val BYTE_ARRAY: Type = Type(TagOf(Repr.BYTE_ARRAY))
        val INT_ARRAY: Type = Type(TagOf(Repr.INT_ARRAY))
        val LONG_ARRAY: Type = Type(TagOf(Repr.LONG_ARRAY))
        val LIST: Type = Type(TagOf(Repr.LIST))
        val COMPOUND: Type = Type(TagOf(Repr.COMPOUND))
      }
    }

    data object Bool : Term() {
      override val type: Term get() = Type.BYTE
    }

    data class BoolOf(
      val value: Boolean,
    ) : Term() {
      override val type: Term get() = Bool
    }

    data class If(
      val condition: Term,
      val thenBranch: Term,
      val elseBranch: Term,
      override val type: Term,
    ) : Term()

    data object I8 : Term() {
      override val type: Term get() = Type.BYTE
    }

    data class I8Of(
      val value: Byte,
    ) : Term() {
      override val type: Term get() = I8
    }

    data object I16 : Term() {
      override val type: Term get() = Type.SHORT
    }

    data class I16Of(
      val value: Short,
    ) : Term() {
      override val type: Term get() = I16
    }

    data object I32 : Term() {
      override val type: Term get() = Type.INT
    }

    data class I32Of(
      val value: Int,
    ) : Term() {
      override val type: Term get() = I32
    }

    data object I64 : Term() {
      override val type: Term get() = Type.LONG
    }

    data class I64Of(
      val value: Long,
    ) : Term() {
      override val type: Term get() = I64
    }

    data object F32 : Term() {
      override val type: Term get() = Type.FLOAT
    }

    data class F32Of(
      val value: Float,
    ) : Term() {
      override val type: Term get() = F32
    }

    data object F64 : Term() {
      override val type: Term get() = Type.DOUBLE
    }

    data class F64Of(
      val value: Double,
    ) : Term() {
      override val type: Term get() = F64
    }

    data object Wtf16 : Term() {
      override val type: Term get() = Type.STRING
    }

    data class Wtf16Of(
      val value: String,
    ) : Term() {
      override val type: Term get() = Wtf16
    }

    data object I8Array : Term() {
      override val type: Term get() = Type.BYTE_ARRAY
    }

    data class I8ArrayOf(
      val elements: List<Term>,
    ) : Term() {
      override val type: Term get() = I8Array
    }

    data object I32Array : Term() {
      override val type: Term get() = Type.INT_ARRAY
    }

    data class I32ArrayOf(
      val elements: List<Term>,
    ) : Term() {
      override val type: Term get() = I32Array
    }

    data object I64Array : Term() {
      override val type: Term get() = Type.LONG_ARRAY
    }

    data class I64ArrayOf(
      val elements: List<Term>,
    ) : Term() {
      override val type: Term get() = I64Array
    }

    data class Vec(
      val element: Term,
    ) : Term() {
      override val type: Term get() = Type.LIST
    }

    data class VecOf(
      val elements: List<Term>,
      override val type: Term,
    ) : Term()

    data class Struct(
      val elements: LinkedHashMap<String, Term>,
    ) : Term() {
      override val type: Term get() = Type.COMPOUND
    }

    data class StructOf(
      val elements: LinkedHashMap<String, Term>,
      override val type: Term,
    ) : Term()

    data class Point(
      val element: Term,
      override val type: Term,
    ) : Term()

    data class Union(
      val elements: List<Term>,
      override val type: Term,
    ) : Term()

    data class Func(
      val open: Boolean,
      val params: List<Pair<Pattern, Term>>,
      val result: Term,
    ) : Term() {
      override val type: Term = Type.COMPOUND
    }

    data class FuncOf(
      val open: Boolean,
      val params: List<Pattern>,
      val result: Term,
      override val type: Term,
    ) : Term()

    data class Apply(
      val open: Boolean,
      val func: Term,
      val args: List<Term>,
      override val type: Term,
    ) : Term()

    data class Code(
      val element: Term,
    ) : Term() {
      override val type: Term = Type.END
    }

    data class CodeOf(
      val element: Term,
      override val type: Term,
    ) : Term()

    data class Splice(
      val element: Term,
      override val type: Term,
    ) : Term()

    data class Command(
      val element: Term,
      override val type: Term,
    ) : Term()

    data class Let(
      val binder: Pattern,
      val init: Term,
      val body: Term,
      override val type: Term,
    ) : Term()

    data class Match(
      val scrutinee: Term,
      val branches: List<Pair<Pattern, Term>>,
      override val type: Term,
    ) : Term()

    data class Proj(
      val target: Term,
      val projection: Projection,
      override val type: Term,
    ) : Term()

    data class Var(
      val name: String,
      val idx: Idx,
      override val type: Term,
    ) : Term()

    data class Def(
      val def: Definition.Def,
      override val type: Term,
    ) : Term()

    data class Meta(
      val index: Int,
      val source: Range,
      override val type: Term,
    ) : Term()

    data object Hole : Term() {
      override val type: Term get() = Hole
    }
  }

  /**
   * A well-typed pattern.
   */
  sealed class Pattern {
    data class I32Of(
      val value: Int,
    ) : Pattern()

    data class StructOf(
      val elements: LinkedHashMap<String, Pattern>,
    ) : Pattern()

    data class Var(
      val name: String,
    ) : Pattern()

    data object Drop : Pattern()

    data object Hole : Pattern()
  }
}
