package mcx.ast

enum class Modifier(val id: String) {
  BUILTIN("builtin"),
  EXPORT("export"),
  REC("rec"),
  CONST("const"),
  TEST("test");

  override fun toString(): String =
    id
}
