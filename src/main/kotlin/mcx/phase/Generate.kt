package mcx.phase

import mcx.ast.Json
import mcx.ast.Location
import mcx.ast.Packed
import mcx.ast.Registry
import mcx.util.quoted
import java.security.MessageDigest
import mcx.ast.Packed as P

class Generate private constructor(
  private val config: Config,
  private val generator: Generator,
) {
  private val digest = MessageDigest.getInstance("SHA3-256")

  private fun generateModule(
    module: Packed.Module,
  ) {
    module.resources.forEach {
      generateResource(it)
    }
  }

  private fun generateResource(
    resource: P.Resource,
  ) {
    when (resource) {
      is P.Resource.JsonResource -> {
        generator.entry(generatePath(resource.registry, resource.name))
        generateJson(resource.body)
      }
      is P.Resource.Functions    -> {
        generator.entry(generatePath(Registry.FUNCTIONS, resource.name))
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
      is P.Instruction.Push    -> generator.write("data modify storage mcx: ${generateStack(instruction.tag.type)} append value ${generateTag(instruction.tag)}")
      is P.Instruction.Copy    -> {
        val stack = generateStack(instruction.type)
        generator.write("data modify storage mcx: $stack append from storage mcx: $stack[${instruction.index}]")
      }
      is P.Instruction.Drop    -> generator.write("data remove storage mcx: ${generateStack(instruction.type)}[${instruction.index}]")
      is P.Instruction.Run     -> generator.write("function ${generateResourceLocation(instruction.name)}")
      is P.Instruction.Command -> generator.write(instruction.value)
      is P.Instruction.Debug   -> if (config.debug) {
        generator.write("# ")
        generator.write(instruction.message)
      }
    }
  }

  private fun generateStack(
    type: P.Type,
  ): String =
    when (type) {
      P.Type.END      -> error("unexpected: end")
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
    name: Location,
  ): String =
    "data/minecraft/${registry.string}/${generateResourceLocation(name)}.${registry.extension}"

  private fun generateResourceLocation(
    name: Location,
  ): String =
    hash(name.toString())

  private fun hash(
    string: String,
  ): String =
    digest
      .digest(string.encodeToByteArray())
      .joinToString("") {
        it
          .toUByte()
          .toString(16)
          .padStart(2, '0')
      }

  interface Generator {
    fun entry(name: String)

    fun write(string: String)
  }

  companion object {
    operator fun invoke(
      config: Config,
      generator: Generator,
      module: Packed.Module,
    ) {
      Generate(config, generator).generateModule(module)
    }
  }
}
