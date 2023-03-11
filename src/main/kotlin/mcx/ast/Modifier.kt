package mcx.ast

enum class Modifier(val token: String) {
  BUILTIN("builtin"),
  EXPORT("export"),
  INLINE("inline"),
  CONST("const"),
  WORLD("world"),
}
