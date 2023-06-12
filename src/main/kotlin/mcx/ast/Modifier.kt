package mcx.ast

enum class Modifier(val id: String) {
  BUILTIN("builtin"),
  EXPORT("export"),
  REC("rec"),
  DIRECT("direct"),
  CONST("const"),
  TEST("test"),
  ERROR("error");

  override fun toString(): String =
    id
}
