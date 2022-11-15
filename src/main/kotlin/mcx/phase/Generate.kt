package mcx.phase

import mcx.ast.Json
import mcx.ast.Packed
import mcx.util.quoted
import mcx.ast.Packed as P

class Generate private constructor(
  private val config: Config,
) {
  private fun generateResource(
    resource: P.Resource,
  ): String {
    return when (resource) {
      is P.Resource.JsonResource  ->
        StringBuilder().apply { generateJson(resource.body) }
      is Packed.Resource.Function ->
        StringBuilder()
          .apply {
            resource.commands.forEachIndexed { index, command ->
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
      config: Config,
      resource: P.Resource,
    ): String {
      return Generate(config).generateResource(resource)
    }
  }
}
