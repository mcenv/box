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
    /**
     * The type of this term.
     */
    abstract val type: Lazy<Term>

    data object Tag : Term() {
      override val type: Lazy<Term> get() = Type.END_LAZY

      val LAZY: Lazy<Tag> = lazyOf(Tag)
    }

    data class TagOf(
      val value: NbtType,
    ) : Term() {
      override val type: Lazy<Term> get() = Tag.LAZY
    }

    data class Type(
      val element: Term,
    ) : Term() {
      override val type: Lazy<Term> get() = BYTE_LAZY

      companion object {
        val END_LAZY: Lazy<Type> = lazyOf(Type(TagOf(NbtType.END)))
        val BYTE_LAZY: Lazy<Type> = lazyOf(Type(TagOf(NbtType.BYTE)))
        val SHORT_LAZY: Lazy<Type> = lazyOf(Type(TagOf(NbtType.SHORT)))
        val INT_LAZY: Lazy<Type> = lazyOf(Type(TagOf(NbtType.INT)))
        val LONG_LAZY: Lazy<Type> = lazyOf(Type(TagOf(NbtType.LONG)))
        val FLOAT_LAZY: Lazy<Type> = lazyOf(Type(TagOf(NbtType.FLOAT)))
        val DOUBLE_LAZY: Lazy<Type> = lazyOf(Type(TagOf(NbtType.DOUBLE)))
        val STRING_LAZY: Lazy<Type> = lazyOf(Type(TagOf(NbtType.STRING)))
        val BYTE_ARRAY_LAZY: Lazy<Type> = lazyOf(Type(TagOf(NbtType.BYTE_ARRAY)))
        val INT_ARRAY_LAZY: Lazy<Type> = lazyOf(Type(TagOf(NbtType.INT_ARRAY)))
        val LONG_ARRAY_LAZY: Lazy<Type> = lazyOf(Type(TagOf(NbtType.LONG_ARRAY)))
        val LIST_LAZY: Lazy<Type> = lazyOf(Type(TagOf(NbtType.LIST)))
        val COMPOUND_LAZY: Lazy<Type> = lazyOf(Type(TagOf(NbtType.COMPOUND)))
      }
    }

    data object Bool : Term() {
      override val type: Lazy<Term> get() = Type.BYTE_LAZY

      val LAZY: Lazy<Bool> = lazyOf(Bool)
    }

    data class BoolOf(
      val value: Boolean,
    ) : Term() {
      override val type: Lazy<Term> get() = Bool.LAZY
    }

    data class If(
      val condition: Term,
      val thenBranch: Term,
      val elseBranch: Term,
      override val type: Lazy<Term>,
    ) : Term()

    data class Is(
      val scrutinee: Term,
      val scrutineer: Pattern,
    ) : Term() {
      override val type: Lazy<Term> get() = Bool.LAZY
    }

    data object I8 : Term() {
      override val type: Lazy<Term> get() = Type.BYTE_LAZY

      val LAZY: Lazy<I8> = lazyOf(I8)
    }

    data class I8Of(
      val value: Byte,
    ) : Term() {
      override val type: Lazy<Term> get() = I8.LAZY
    }

    data object I16 : Term() {
      override val type: Lazy<Term> get() = Type.SHORT_LAZY

      val LAZY: Lazy<I16> = lazyOf(I16)
    }

    data class I16Of(
      val value: Short,
    ) : Term() {
      override val type: Lazy<Term> get() = I16.LAZY
    }

    data object I32 : Term() {
      override val type: Lazy<Term> get() = Type.INT_LAZY

      val LAZY: Lazy<I32> = lazyOf(I32)
    }

    data class I32Of(
      val value: Int,
    ) : Term() {
      override val type: Lazy<Term> get() = I32.LAZY
    }

    data object I64 : Term() {
      override val type: Lazy<Term> get() = Type.LONG_LAZY

      val LAZY: Lazy<I64> = lazyOf(I64)
    }

    data class I64Of(
      val value: Long,
    ) : Term() {
      override val type: Lazy<Term> get() = I64.LAZY
    }

    data object F32 : Term() {
      override val type: Lazy<Term> get() = Type.FLOAT_LAZY

      val LAZY: Lazy<F32> = lazyOf(F32)
    }

    data class F32Of(
      val value: Float,
    ) : Term() {
      override val type: Lazy<Term> get() = F32.LAZY
    }

    data object F64 : Term() {
      override val type: Lazy<Term> get() = Type.DOUBLE_LAZY

      val LAZY: Lazy<F64> = lazyOf(F64)
    }

    data class F64Of(
      val value: Double,
    ) : Term() {
      override val type: Lazy<Term> get() = F64.LAZY
    }

    data object Str : Term() {
      override val type: Lazy<Term> get() = Type.STRING_LAZY

      val LAZY: Lazy<Str> = lazyOf(Str)
    }

    data class StrOf(
      val value: String,
    ) : Term() {
      override val type: Lazy<Term> get() = Str.LAZY
    }

    data object I8Array : Term() {
      override val type: Lazy<Term> get() = Type.BYTE_ARRAY_LAZY

      val LAZY: Lazy<I8Array> = lazyOf(I8Array)
    }

    data class I8ArrayOf(
      val elements: List<Term>,
    ) : Term() {
      override val type: Lazy<Term> get() = I8Array.LAZY
    }

    data object I32Array : Term() {
      override val type: Lazy<Term> get() = Type.INT_ARRAY_LAZY

      val LAZY: Lazy<I32Array> = lazyOf(I32Array)
    }

    data class I32ArrayOf(
      val elements: List<Term>,
    ) : Term() {
      override val type: Lazy<Term> get() = I32Array.LAZY
    }

    data object I64Array : Term() {
      override val type: Lazy<Term> get() = Type.LONG_ARRAY_LAZY

      val LAZY: Lazy<I64Array> = lazyOf(I64Array)
    }

    data class I64ArrayOf(
      val elements: List<Term>,
    ) : Term() {
      override val type: Lazy<Term> get() = I64Array.LAZY
    }

    data class Vec(
      val element: Term,
    ) : Term() {
      override val type: Lazy<Term> get() = Type.LIST_LAZY
    }

    data class VecOf(
      val elements: List<Term>,
      override val type: Lazy<Term>,
    ) : Term()

    data class Struct(
      val elements: LinkedHashMap<String, Term>,
    ) : Term() {
      override val type: Lazy<Term> get() = Type.COMPOUND_LAZY
    }

    data class StructOf(
      val elements: LinkedHashMap<String, Term>,
      override val type: Lazy<Term>,
    ) : Term()

    data class Point(
      val element: Term,
      override val type: Lazy<Term>,
    ) : Term()

    data class Union(
      val elements: List<Term>,
      override val type: Lazy<Term>,
    ) : Term()

    data class Func(
      val open: Boolean,
      val params: List<Pair<Pattern, Term>>,
      val result: Term,
    ) : Term() {
      override val type: Lazy<Term> = Type.COMPOUND_LAZY
    }

    data class FuncOf(
      val open: Boolean,
      val params: List<Pattern>,
      val result: Term,
      override val type: Lazy<Term>,
    ) : Term()

    data class Apply(
      val open: Boolean,
      val func: Term,
      val args: List<Term>,
      override val type: Lazy<Term>,
    ) : Term()

    data class Code(
      val element: Term,
    ) : Term() {
      override val type: Lazy<Term> = Type.END_LAZY
    }

    data class CodeOf(
      val element: Term,
      override val type: Lazy<Term>,
    ) : Term()

    data class Splice(
      val element: Term,
      override val type: Lazy<Term>,
    ) : Term()

    data class Command(
      val element: Term,
      override val type: Lazy<Term>,
    ) : Term()

    data class Let(
      val binder: Pattern,
      val init: Term,
      val body: Term,
      override val type: Lazy<Term>,
    ) : Term()

    data class Var(
      val name: String,
      val idx: Idx,
      override val type: Lazy<Term>,
    ) : Term()

    data class Def(
      val def: Definition.Def,
      override val type: Lazy<Term>,
    ) : Term()

    data class Meta(
      val index: Int,
      val source: Range,
      override val type: Lazy<Term>,
    ) : Term()

    data object Hole : Term() {
      override val type: Lazy<Term> get() = LAZY

      val LAZY: Lazy<Hole> = lazyOf(Hole)
    }
  }

  /**
   * A well-typed pattern.
   */
  sealed class Pattern {
    data class I32Of(
      val value: Int,
    ) : Pattern()

    data class Var(
      val name: String,
    ) : Pattern()

    data object Drop : Pattern()

    data object Hole : Pattern()
  }
}
