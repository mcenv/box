package mcx.phase.backend

import mcx.ast.Json
import mcx.ast.Packed
import mcx.phase.Context
import mcx.util.quoted
import mcx.ast.Packed as P

class Generate private constructor(
  private val context: Context,
) {
  private fun generate(
    definition: P.Definition,
  ): Pair<String, String> =
    "data/minecraft/${definition.registry.string}/${definition.path}.${definition.registry.extension}" to generateDefinition(definition)

  private fun generateDefinition(
    definition: P.Definition,
  ): String {
    return when (definition) {
      is P.Definition.Resource      ->
        StringBuilder().apply { generateJson(definition.body) }
      is Packed.Definition.Function ->
        StringBuilder()
          .apply {
            definition.commands.forEachIndexed { index, command ->
              if (index != 0) {
                append('\n')
              }
              append(command)
            }
          }
    }.toString()
  }

  private fun StringBuilder.generateJson(
    json: Json,
  ) {
    when (json) {
      is Json.ObjectOf -> {
        append('{')
        json.members.entries.forEachIndexed { index, (key, value) ->
          if (index != 0) {
            append(',')
          }
          append(key.quoted('"'))
          append(':')
          generateJson(value)
        }
        append('}')
      }
      is Json.ArrayOf  -> {
        append('[')
        json.elements.forEachIndexed { index, element ->
          if (index != 0) {
            append(',')
          }
          generateJson(element)
        }
        append(']')
      }
      is Json.StringOf -> append(json.value.quoted('"'))
      is Json.ByteOf   -> append(json.value.toString())
      is Json.ShortOf  -> append(json.value.toString())
      is Json.IntOf    -> append(json.value.toString())
      is Json.LongOf   -> append(json.value.toString())
      is Json.FloatOf  -> append(json.value.toString())
      is Json.DoubleOf -> append(json.value.toString())
      is Json.BoolOf   -> append(json.value.toString())
    }
  }

  companion object {
    operator fun invoke(
      context: Context,
      definition: P.Definition,
    ): Pair<String, String> {
      return Generate(context).generate(definition)
    }
  }
}
