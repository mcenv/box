package mcx.ast

object Packed {
  data class Module(
    val name: Location,
    val resources: List<Resource>,
  )

  sealed interface Resource {
    val name: Location

    data class JsonResource(
      val registry: Registry,
      override val name: Location,
      val body: Json,
    ) : Resource

    data class Functions(
      override val name: Location,
      val instructions: List<Instruction>,
    ) : Resource
  }

  enum class Type {
    END,
    BYTE,
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    STRING,
    LIST,
    COMPOUND,
  }

  sealed interface Instruction {
    data class Push(
      val tag: Tag,
    ) : Instruction

    data class Copy(
      val index: Int,
      val type: Type,
    ) : Instruction

    data class Drop(
      val index: Int,
      val type: Type,
    ) : Instruction

    data class Run(
      val name: Location,
    ) : Instruction

    data class Command(
      val value: String,
    ) : Instruction

    data class Debug(
      val message: String,
    ) : Instruction
  }

  sealed interface Tag {
    val type: Type

    data class ByteOf(val value: Byte) : Tag {
      override val type: Type get() = Type.BYTE
    }

    data class ShortOf(val value: Short) : Tag {
      override val type: Type get() = Type.SHORT
    }

    data class IntOf(val value: Int) : Tag {
      override val type: Type get() = Type.INT
    }

    data class LongOf(val value: Long) : Tag {
      override val type: Type get() = Type.LONG
    }

    data class FloatOf(val value: Float) : Tag {
      override val type: Type get() = Type.FLOAT
    }

    data class DoubleOf(val value: Double) : Tag {
      override val type: Type get() = Type.DOUBLE
    }

    data class StringOf(val value: String) : Tag {
      override val type: Type get() = Type.STRING
    }
  }
}
