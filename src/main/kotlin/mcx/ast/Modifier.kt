package mcx.ast

enum class Modifier(val id: String) {
  BUILTIN("builtin"),
  EXPORT("export"),
  INLINE("inline"),
  CONST("const"),
  WORLD("world"),
}
