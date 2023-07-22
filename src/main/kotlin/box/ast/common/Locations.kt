package box.ast.common

// TODO: Merge [ModuleLocation] and [DefinitionLocation] into one class.

data class ModuleLocation(
  val parts: List<String>,
) {
  constructor(
    vararg parts: String,
  ) : this(parts.toList())

  operator fun div(
    name: String,
  ): DefinitionLocation {
    return DefinitionLocation(this, name)
  }

  override fun toString(): String {
    return parts.joinToString("::")
  }
}

data class DefinitionLocation(
  val module: ModuleLocation,
  val name: String,
) {
  override fun toString(): String {
    return "$module::$name"
  }
}
