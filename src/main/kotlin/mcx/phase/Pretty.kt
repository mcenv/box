package mcx.phase

import mcx.ast.Core as C

fun prettyType0(type: C.Type0): String {
  return when (type) {
    is C.Type0.End      -> "end"
    is C.Type0.Bool     -> "bool"
    is C.Type0.Byte     -> "byte"
    is C.Type0.Short    -> "short"
    is C.Type0.Int      -> "int"
    is C.Type0.Long     -> "long"
    is C.Type0.Float    -> "float"
    is C.Type0.Double   -> "double"
    is C.Type0.String   -> "string"
    is C.Type0.List     -> "[${prettyType0(type.element)}]"
    is C.Type0.Compound -> type.elements.entries.joinToString(
      ", ",
      "{",
      "}"
    ) { (key, element) -> "$key: ${prettyType0(element)}" }
    is C.Type0.Box      -> "box(${prettyType0(type.element)})"
    is C.Type0.Hole     -> "?"
  }
}
