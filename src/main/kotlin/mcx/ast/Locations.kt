package mcx.ast

data class ModuleLocation(
  val parts: List<String>,
) {
  constructor(vararg parts: String) : this(parts.toList())

  operator fun div(
    name: String,
  ): DefinitionLocation =
    DefinitionLocation(this, name)

  override fun toString(): String =
    parts.joinToString("::")
}

data class DefinitionLocation(
  val module: ModuleLocation,
  val name: String,
) {
  override fun toString(): String =
    "$module::$name"
}
