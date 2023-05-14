package mcx.ast

import mcx.data.NbtType

object Lifted {
  sealed class Definition {
    abstract val modifiers: List<Modifier>
    abstract val name: DefinitionLocation

    data class Function(
      override val modifiers: List<Modifier>,
      override val name: DefinitionLocation,
      val params: List<Pattern>,
      val body: Term?,
      val restore: Int?,
    ) : Definition()
  }

  enum class Modifier {
    NO_DROP,
    BUILTIN,
    TEST,
  }

  sealed class Term {
    abstract val type: NbtType

    data class If(
      val condition: Term,
      val thenName: DefinitionLocation,
      val elseName: DefinitionLocation,
      override val type: NbtType,
    ) : Term()

    data class Is(
      val scrutinee: Term,
      val scrutineer: Pattern,
    ) : Term() {
      override val type: NbtType get() = NbtType.BYTE
    }

    data class ByteOf(
      val value: Byte,
    ) : Term() {
      override val type: NbtType get() = NbtType.BYTE
    }

    data class ShortOf(
      val value: Short,
    ) : Term() {
      override val type: NbtType get() = NbtType.SHORT
    }

    data class IntOf(
      val value: Int,
    ) : Term() {
      override val type: NbtType get() = NbtType.INT
    }

    data class LongOf(
      val value: Long,
    ) : Term() {
      override val type: NbtType get() = NbtType.LONG
    }

    data class FloatOf(
      val value: Float,
    ) : Term() {
      override val type: NbtType get() = NbtType.FLOAT
    }

    data class DoubleOf(
      val value: Double,
    ) : Term() {
      override val type: NbtType get() = NbtType.DOUBLE
    }

    data class StringOf(
      val value: String,
    ) : Term() {
      override val type: NbtType get() = NbtType.STRING
    }

    data class ByteArrayOf(
      val elements: List<Term>,
    ) : Term() {
      override val type: NbtType get() = NbtType.BYTE_ARRAY
    }

    data class IntArrayOf(
      val elements: List<Term>,
    ) : Term() {
      override val type: NbtType get() = NbtType.INT_ARRAY
    }

    data class LongArrayOf(
      val elements: List<Term>,
    ) : Term() {
      override val type: NbtType get() = NbtType.LONG_ARRAY
    }

    data class ListOf(
      val elements: List<Term>,
    ) : Term() {
      override val type: NbtType get() = NbtType.LIST
    }

    data class CompoundOf(
      val elements: LinkedHashMap<String, Term>,
    ) : Term() {
      override val type: NbtType get() = NbtType.COMPOUND
    }

    data class ProcOf(
      val tag: Int,
    ) : Term() {
      override val type: NbtType get() = NbtType.INT
    }

    data class FuncOf(
      val entries: List<Entry>,
      val tag: Int,
    ) : Term() {
      override val type: NbtType get() = NbtType.COMPOUND

      data class Entry(
        val name: String,
        val type: NbtType,
      )
    }

    data class Apply(
      val func: Term,
      val args: List<Term>,
      override val type: NbtType,
    ) : Term()

    data class Command(
      val element: String,
      override val type: NbtType,
    ) : Term()

    data class Let(
      val binder: Pattern,
      val init: Term,
      val body: Term,
    ) : Term() {
      override val type: NbtType = body.type
    }

    data class Var(
      val name: String,
      val idx: Idx,
      override val type: NbtType,
    ) : Term()

    data class Def(
      val name: DefinitionLocation,
      override val type: NbtType,
    ) : Term()
  }

  sealed class Pattern {
    abstract val type: NbtType

    data class IntOf(
      val value: Int,
    ) : Pattern() {
      override val type: NbtType get() = NbtType.INT
    }

    data class CompoundOf(
      val elements: LinkedHashMap<String, Pattern>,
    ) : Pattern() {
      override val type: NbtType get() = NbtType.COMPOUND
    }

    data class Var(
      val name: String,
      override val type: NbtType,
    ) : Pattern()

    data class Drop(
      override val type: NbtType,
    ) : Pattern()
  }
}
