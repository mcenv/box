package mcx.ast

object Pack {
  data class Root(
    val module: Location,
    val resources: List<Resource>,
  )

  sealed interface Resource {
    val module: Location
    val name: String

    data class Function(
      override val module: Location,
      override val name: String,
      val instructions: List<Instruction>,
    ) : Resource
  }

  enum class Type {
    INT,
    STRING,
  }

  sealed interface Instruction {
    data class Push(
      val value: Tag,
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
