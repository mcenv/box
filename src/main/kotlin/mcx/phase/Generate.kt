package mcx.phase

import mcx.ast.Location
import mcx.util.quoted
import mcx.ast.Packed as P

class Generate private constructor(
  private val pack: String,
  private val generator: Generator,
) {
  private fun generateRoot(
    root: P.Root,
  ) {
    root.resources.forEach {
      generateResource(it)
    }
  }

  private fun generateResource(
    resource: P.Resource,
  ) {
    when (resource) {
      is P.Resource.Function -> {
        generator.entry(
          generatePath(
            "functions",
            resource.module,
            resource.name,
            "mcfunction",
          )
        )
        resource.instructions.forEachIndexed { index, instruction ->
          if (index != 0) {
            generator.write("\n")
          }
          generateInstruction(instruction)
        }
      }
    }
  }

  private fun generateInstruction(
    instruction: P.Instruction,
  ) {
    when (instruction) {
      is P.Instruction.Push  -> generator.write("data modify storage mcx: ${generateStack(instruction.type)} append value ${generateTag(instruction.tag)}")
      is P.Instruction.Copy  -> {
        val stack = generateStack(instruction.type)
        generator.write("data modify storage mcx: $stack append from storage mcx: $stack[${instruction.index}]")
      }
      is P.Instruction.Drop  -> generator.write("data remove storage mcx: ${generateStack(instruction.type)}[${instruction.index}]")
      is P.Instruction.Run   -> generator.write(
        "function ${
          generateResourceLocation(
            instruction.module,
            instruction.name,
          )
        }"
      )
      is P.Instruction.Debug -> {
        generator.write("# ")
        generator.write(instruction.message)
      }
    }
  }

  private fun generateStack(
    type: P.Type,
  ): String =
    when (type) {
      P.Type.INT    -> "int"
      P.Type.STRING -> "string"
    }

  private fun generateTag(
    tag: P.Tag,
  ): String =
    when (tag) {
      is P.Tag.IntOf    -> "${tag.value}"
      is P.Tag.StringOf -> tag.value.quoted('"')
    }

  private fun generatePath(
    registry: String,
    module: Location,
    name: String,
    extension: String,
  ): String =
    "data/$pack/$registry/$module/$name.$extension"

  private fun generateResourceLocation(
    module: Location,
    name: String,
  ): String =
    "$pack:$module/$name"

  interface Generator {
    fun entry(name: String)

    fun write(string: String)
  }

  companion object {
    operator fun invoke(
      pack: String,
      generator: Generator,
      root: P.Root,
    ) {
      Generate(
        pack,
        generator,
      ).generateRoot(root)
    }
  }
}
