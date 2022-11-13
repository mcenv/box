package mcx.phase

import mcx.ast.Core as C

fun prettyType(type: C.Type): String {
  return when (type) {
    is C.Type.End      -> "end"
    is C.Type.Bool     -> "bool"
    is C.Type.Byte     -> "byte"
    is C.Type.Short    -> "short"
    is C.Type.Int      -> "int"
    is C.Type.Long     -> "long"
    is C.Type.Float    -> "float"
    is C.Type.Double   -> "double"
    is C.Type.String   -> "string"
    is C.Type.List     -> "[${prettyType(type.element)}]"
    is C.Type.Compound -> type.elements.entries.joinToString(", ", "{", "}") { (key, element) -> "$key: ${prettyType(element)}" }
    is C.Type.Box      -> "&${prettyType(type.element)}"
    is C.Type.Hole     -> "?"
  }
}
