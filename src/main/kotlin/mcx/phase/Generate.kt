package mcx.phase

import mcx.ast.Json
import mcx.ast.Packed
import mcx.ast.Registry
import mcx.util.quoted
import mcx.ast.Packed as P

class Generate private constructor(
  private val config: Config,
  private val generator: Generator,
) {
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
        generator.entry(generatePath(resource.registry, resource.path))
        generateJson(resource.body)
      }
      is P.Resource.Functions    -> {
        generator.entry(generatePath(Registry.FUNCTIONS, resource.path))
        resource.commands.forEachIndexed { index, command ->
          if (index != 0) {
            generator.write("\n")
          }
          generator.write(command)
        }
      }
    }
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
    path: String,
  ): String =
    "data/minecraft/${registry.string}/$path.${registry.extension}"

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
