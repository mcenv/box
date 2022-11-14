package mcx.ast

object Packed {
  data class Module(
    val name: Location,
    val resources: List<Resource>,
  )

  sealed interface Resource {
    val path: String

    data class JsonResource(
      val registry: Registry,
      override val path: String,
      val body: Json,
    ) : Resource

    data class Function(
      override val path: String,
      val commands: List<String>, // TODO: use data types
    ) : Resource
  }

  enum class Type(
    val stack: String,
  ) {
    END("end"),
    BYTE("byte"),
    SHORT("short"),
    INT("int"),
    LONG("long"),
    FLOAT("float"),
    DOUBLE("double"),
    STRING("string"),
    BYTE_ARRAY("byte_array"),
    INT_ARRAY("int_array"),
    LONG_ARRAY("long_array"),
    LIST("list"),
    COMPOUND("compound"),
  }
}
