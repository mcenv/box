package mcx.ast

import mcx.ast.common.DefinitionLocation
import mcx.ast.common.Repr

object Lifted {
  sealed class Definition {
    abstract val modifiers: List<Modifier>
    abstract val name: DefinitionLocation

    data class Function(
      override val modifiers: List<Modifier>,
      override val name: DefinitionLocation,
      val context: List<Pattern>,
      val params: List<Pattern>,
      val body: Term?,
      val restore: Int?,
    ) : Definition()
  }

  enum class Modifier {
    BUILTIN,
    TEST,
    TOP,
  }

  sealed class Term {
    abstract val repr: Repr

    data class I8Of(
      val value: Byte,
    ) : Term() {
      override val repr: Repr get() = Repr.BYTE
    }

    data class I16Of(
      val value: Short,
    ) : Term() {
      override val repr: Repr get() = Repr.SHORT
    }

    data class I32Of(
      val value: Int,
    ) : Term() {
      override val repr: Repr get() = Repr.INT
    }

    data class I64Of(
      val value: Long,
    ) : Term() {
      override val repr: Repr get() = Repr.LONG
    }

    data class F32Of(
      val value: Float,
    ) : Term() {
      override val repr: Repr get() = Repr.FLOAT
    }

    data class F64Of(
      val value: Double,
    ) : Term() {
      override val repr: Repr get() = Repr.DOUBLE
    }

    data class Wtf16Of(
      val value: String,
    ) : Term() {
      override val repr: Repr get() = Repr.STRING
    }

    data class I8ArrayOf(
      val elements: List<Term>,
    ) : Term() {
      override val repr: Repr get() = Repr.BYTE_ARRAY
    }

    data class I32ArrayOf(
      val elements: List<Term>,
    ) : Term() {
      override val repr: Repr get() = Repr.INT_ARRAY
    }

    data class I64ArrayOf(
      val elements: List<Term>,
    ) : Term() {
      override val repr: Repr get() = Repr.LONG_ARRAY
    }

    data class VecOf(
      val elements: List<Term>,
    ) : Term() {
      override val repr: Repr get() = Repr.LIST
    }

    data class StructOf(
      val elements: LinkedHashMap<String, Term>,
    ) : Term() {
      override val repr: Repr get() = Repr.COMPOUND
    }

    data class ProcOf(
      val function: Definition.Function,
    ) : Term() {
      override val repr: Repr get() = Repr.INT
    }

    data class FuncOf(
      val entries: List<Entry>,
      val tag: Int,
    ) : Term() {
      override val repr: Repr get() = Repr.COMPOUND

      data class Entry(
        val name: String,
        val type: Repr,
      )
    }

    data class Apply(
      val open: Boolean,
      val func: Term,
      val args: List<Term>,
      override val repr: Repr,
    ) : Term()

    data class Command(
      val element: String,
      override val repr: Repr,
    ) : Term()

    data class Let(
      val binder: Pattern,
      val init: Term,
      val body: Term,
    ) : Term() {
      override val repr: Repr = body.repr
    }

    data class If(
      val scrutinee: Term,
      val branches: List<Pair<Pattern, DefinitionLocation>>,
      override val repr: Repr,
    ) : Term()

    data class Var(
      val name: String,
      override val repr: Repr,
    ) : Term()

    data class Def(
      val direct: Boolean,
      val name: DefinitionLocation,
      override val repr: Repr,
    ) : Term()
  }

  sealed class Pattern {
    abstract val repr: Repr

    data class BoolOf(
      val value: Boolean,
    ) : Pattern() {
      override val repr: Repr get() = Repr.BYTE
    }

    data class I8Of(
      val value: Byte,
    ) : Pattern() {
      override val repr: Repr get() = Repr.BYTE
    }

    data class I16Of(
      val value: Short,
    ) : Pattern() {
      override val repr: Repr get() = Repr.SHORT
    }

    data class I32Of(
      val value: Int,
    ) : Pattern() {
      override val repr: Repr get() = Repr.INT
    }

    data class I64Of(
      val value: Long,
    ) : Pattern() {
      override val repr: Repr get() = Repr.LONG
    }

    data class F32Of(
      val value: Float,
    ) : Pattern() {
      override val repr: Repr get() = Repr.FLOAT
    }

    data class F64Of(
      val value: Double,
    ) : Pattern() {
      override val repr: Repr get() = Repr.DOUBLE
    }

    data class Wtf16Of(
      val value: String,
    ) : Pattern() {
      override val repr: Repr get() = Repr.STRING
    }

    data class VecOf(
      val elements: List<Pattern>,
    ) : Pattern() {
      override val repr: Repr get() = Repr.LIST
    }

    data class StructOf(
      val elements: LinkedHashMap<String, Pattern>,
    ) : Pattern() {
      override val repr: Repr get() = Repr.COMPOUND
    }

    data class Var(
      val name: String,
      override val repr: Repr,
    ) : Pattern()

    data class Drop(
      override val repr: Repr,
    ) : Pattern()
  }
}
