package mcx.ast

enum class Modifier(val id: String) {
  BUILTIN("builtin"),
  EXPORT("export"),
  REC("rec"),
  CONST("const");

  override fun toString(): String = id
}
