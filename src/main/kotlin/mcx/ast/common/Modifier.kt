package mcx.ast.common

enum class Modifier(val id: String) {
  EXPORT("export"),
  REC("rec"),
  DIRECT("direct"),
  CONST("const"),
  TEST("test"),
  ERROR("error");

  override fun toString(): String =
    id
}
