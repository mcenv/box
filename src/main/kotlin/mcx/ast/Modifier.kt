package mcx.ast

enum class Modifier(val id: String) {
  BUILTIN("builtin"),
  EXPORT("export"),
  REC("rec"),
  DIRECT("direct"),
  CONST("const"),
  TEST("test");

  override fun toString(): String =
    id
}
