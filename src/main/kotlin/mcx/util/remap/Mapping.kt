package mcx.util.remap

data class Mapping(
  val classMapping: Map<String, Class>,
) {
  data class Class(
    val name: String,
    val fieldMapping: Map<String, Field>,
    val methodMapping: Map<Pair<String, String>, Method>,
  )

  data class Field(
    val name: String,
  )

  data class Method(
    val name: String,
  )
}
