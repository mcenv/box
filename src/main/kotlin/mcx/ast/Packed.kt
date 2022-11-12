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

    data class Functions(
      override val path: String,
      val commands: List<String>, // TODO: use data types
    ) : Resource
  }

  enum class Type {
    END,
    BYTE,
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    STRING,
    LIST,
    COMPOUND,
  }
}
