package mcx.ast

object Packed {
  data class Root(
    val module: Location,
    val resources: List<Resource>,
  )

  sealed interface Resource {
    val module: Location
    val name: String

    data class JsonResource(
      val registry: Registry,
      override val module: Location,
      override val name: String,
      val body: Json,
    ) : Resource

    data class Function(
      override val module: Location,
      override val name: String,
      val instructions: List<Instruction>,
    ) : Resource
  }

  enum class Type {
    INT,
    STRING,
    LIST,
    COMPOUND,
  }

  sealed interface Instruction {
    data class Push(
      val tag: Tag,
      val type: Type,
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
      val module: Location,
      val name: String,
    ) : Instruction

    data class Debug(
      val message: String,
    ) : Instruction
  }

  sealed interface Tag {
    data class IntOf(val value: Int) : Tag

    data class StringOf(val value: String) : Tag
  }
}
