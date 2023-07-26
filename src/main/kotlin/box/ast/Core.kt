package box.ast

import box.ast.common.*
import box.ast.common.Annotation
import box.util.unreachable
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

    data object Unit : Term() {
      override val type: Term get() = Type.BYTE
    }

    data object Bool : Term() {
      override val type: Term get() = Type.BYTE
    }

    data object I8 : Term() {
      override val type: Term get() = Type.BYTE
    }

    data object I16 : Term() {
      override val type: Term get() = Type.SHORT
    }

    data object I32 : Term() {
      override val type: Term get() = Type.INT
    }

    data object I64 : Term() {
      override val type: Term get() = Type.LONG
    }

    data object F32 : Term() {
      override val type: Term get() = Type.FLOAT
    }

    data object F64 : Term() {
      override val type: Term get() = Type.DOUBLE
    }

    data object Wtf16 : Term() {
      override val type: Term get() = Type.STRING
    }

    data class ConstOf<out T>(
      val value: T & Any,
    ) : Term() {
      override val type: Term = when (value) {
        is kotlin.Unit -> Unit
        is Boolean     -> Bool
        is Byte        -> I8
        is Short       -> I16
        is Int         -> I32
        is Long        -> I64
        is Float       -> F32
        is Double      -> F64
        is String      -> Wtf16
        else           -> unreachable()
      }
    }

    data object I8Array : Term() {
      override val type: Term get() = Type.BYTE_ARRAY
    }

    data class I8ArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term() {
      override val type: Term get() = I8Array
    }

    data object I32Array : Term() {
      override val type: Term get() = Type.INT_ARRAY
    }

    data class I32ArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term() {
      override val type: Term get() = I32Array
    }

    data object I64Array : Term() {
      override val type: Term get() = Type.LONG_ARRAY
    }

    data class I64ArrayOf(
      val elements: kotlin.collections.List<Term>,
    ) : Term() {
      override val type: Term get() = I64Array
    }

    data class List(
      val element: Term,
    ) : Term() {
      override val type: Term get() = Type.LIST
    }

    data class ListOf(
      val elements: kotlin.collections.List<Term>,
      override val type: Term,
    ) : Term()

    data class Compound(
      val elements: LinkedHashMap<String, Term>,
    ) : Term() {
      override val type: Term get() = Type.COMPOUND
    }

    data class CompoundOf(
      val elements: LinkedHashMap<String, Term>,
      override val type: Term,
    ) : Term()

    data class Point(
      val element: Term,
      override val type: Term,
    ) : Term()

    data class Union(
      val elements: kotlin.collections.List<Term>,
      override val type: Term,
    ) : Term()

    data class Func(
      val open: Boolean,
      val params: kotlin.collections.List<Pair<Pattern, Term>>,
      val result: Term,
    ) : Term() {
      override val type: Term = Type.COMPOUND
    }

    data class FuncOf(
      val open: Boolean,
      val params: kotlin.collections.List<Pattern>,
      val result: Term,
      override val type: Term,
    ) : Term()

    data class Apply(
      val open: Boolean,
      val func: Term,
      val args: kotlin.collections.List<Term>,
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

    data class Path(
      val element: Term,
    ) : Term() {
      override val type: Term get() = Type.END
    }

    data class PathOf(
      val element: Term,
      override val type: Term,
    ) : Term()

    data class Get(
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

    data class If(
      val scrutinee: Term,
      val branches: kotlin.collections.List<Pair<Pattern, Term>>,
      override val type: Term,
    ) : Term()

    data class Project(
      val target: Term,
      val projs: kotlin.collections.List<Proj>,
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

    data class Builtin(
      val builtin: box.pass.Builtin,
    ) : Term() {
      override val type: Func get() = builtin.type
    }

    data object Hole : Term() {
      override val type: Term get() = Hole
    }
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
