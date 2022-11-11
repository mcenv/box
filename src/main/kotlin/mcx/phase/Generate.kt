package mcx.phase

import mcx.ast.Json
import mcx.ast.Location
import mcx.ast.Registry
import mcx.util.quoted
import mcx.ast.Packed as P

class Generate private constructor(
  private val config: Config,
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
      is P.Resource.JsonResource -> {
        generator.entry(
          generatePath(
            resource.registry,
            resource.module,
            resource.name,
          )
        )
        generateJson(resource.body)
      }
      is P.Resource.Function     -> {
        generator.entry(
          generatePath(
            Registry.FUNCTIONS,
            resource.module,
            resource.name,
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
      is P.Instruction.Push  -> generator.write("data modify storage mcx: ${generateStack(instruction.tag.type)} append value ${generateTag(instruction.tag)}")
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
      is P.Instruction.Debug -> if (config.debug) {
        generator.write("# ")
        generator.write(instruction.message)
      }
    }
  }

  private fun generateStack(
    type: P.Type,
  ): String =
    when (type) {
      P.Type.BYTE     -> "byte"
      P.Type.SHORT    -> "short"
      P.Type.INT      -> "int"
      P.Type.LONG     -> "long"
      P.Type.FLOAT    -> "float"
      P.Type.DOUBLE   -> "double"
      P.Type.STRING   -> "string"
      P.Type.LIST     -> "list"
      P.Type.COMPOUND -> "compound"
    }

  private fun generateTag(
    tag: P.Tag,
  ): String =
    when (tag) {
      is P.Tag.ByteOf   -> "${tag.value}b"
      is P.Tag.ShortOf  -> "${tag.value}s"
      is P.Tag.IntOf    -> "${tag.value}"
      is P.Tag.LongOf   -> "${tag.value}l"
      is P.Tag.FloatOf  -> "${tag.value}f"
      is P.Tag.DoubleOf -> "${tag.value}"
      is P.Tag.StringOf -> tag.value.quoted('"')
    }

  private fun generateJson(
    json: Json,
  ) {
    when (json) {
      is Json.ObjectOf -> {
        generator.write("{")
        json.members.entries.forEachIndexed { index, (key, value) ->
          if (index != 0) {
            generator.write(",")
          }
          generator.write(key.quoted('"'))
          generator.write(":")
          generateJson(value)
        }
        generator.write("}")
      }
      is Json.ArrayOf  -> {
        generator.write("[")
        json.elements.forEachIndexed { index, element ->
          if (index != 0) {
            generator.write(",")
          }
          generateJson(element)
        }
        generator.write("]")
      }
      is Json.StringOf -> generator.write(json.value.quoted('"'))
      is Json.ByteOf   -> generator.write(json.value.toString())
      is Json.ShortOf  -> generator.write(json.value.toString())
      is Json.IntOf    -> generator.write(json.value.toString())
      is Json.LongOf   -> generator.write(json.value.toString())
      is Json.FloatOf  -> generator.write(json.value.toString())
      is Json.DoubleOf -> generator.write(json.value.toString())
      is Json.BoolOf   -> generator.write(json.value.toString())
    }
  }

  private fun generatePath(
    registry: Registry,
    module: Location,
    name: String,
  ): String =
    "data/${config.name}/${registry.plural}/$module/$name.${registry.extension}"

  private fun generateResourceLocation(
    module: Location,
    name: String,
  ): String =
    "${config.name}:$module/$name"

  interface Generator {
    fun entry(name: String)

    fun write(string: String)
  }

  companion object {
    operator fun invoke(
      config: Config,
      generator: Generator,
      root: P.Root,
    ) {
      Generate(
        config,
        generator,
      ).generateRoot(root)
    }
  }
}
