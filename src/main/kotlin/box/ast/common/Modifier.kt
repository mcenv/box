package box.ast.common

enum class Modifier(val id: String) {
  EXPORT("export"),
  INLINE("inline"),
  REC("rec"),
  DIRECT("direct"),
  CONST("const"),
  TEST("test"),
  ERROR("error");

  override fun toString(): String {
    return id
  }
}
