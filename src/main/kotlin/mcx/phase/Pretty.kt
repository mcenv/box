package mcx.phase

import mcx.util.quoted
import mcx.util.toSubscript
import mcx.ast.Core as C

// TODO: precedence-aware prettify

fun prettyKind(
  kind: C.Kind,
): String =
  when (kind) {
    is C.Kind.Type ->
      when (kind.arity) {
        0    -> "()"
        1    -> "*"
        else -> "(${"*, ".repeat(kind.arity - 1)}*)"
      }
    is C.Kind.Meta -> "?${kind.index.toSubscript()}"
    is C.Kind.Hole -> " "
  }

fun prettyType(
  type: C.Type,
): String =
  when (type) {
    is C.Type.Bool      -> if (type.value == null) "bool" else type.value.toString()
    is C.Type.Byte      -> if (type.value == null) "byte" else "${type.value}b"
    is C.Type.Short     -> if (type.value == null) "short" else "${type.value}s"
    is C.Type.Int       -> if (type.value == null) "int" else type.value.toString()
    is C.Type.Long      -> if (type.value == null) "long" else "${type.value}l"
    is C.Type.Float     -> if (type.value == null) "float" else "${type.value}f"
    is C.Type.Double    -> if (type.value == null) "double" else type.value.toString()
    is C.Type.String    -> if (type.value == null) "string" else type.value.quoted('"')
    is C.Type.ByteArray -> "[byte;]"
    is C.Type.IntArray  -> "[int;]"
    is C.Type.LongArray -> "[long;]"
    is C.Type.List      -> "[${prettyType(type.element)}]"
    is C.Type.Compound  -> type.elements.entries.joinToString(", ", "{", "}") { (key, element) -> "$key: ${prettyType(element)}" }
    is C.Type.Tuple     -> type.elements.joinToString(", ", "(", ")") { prettyType(it) }
    is C.Type.Union     -> type.elements.joinToString(", ", "union {", "}") { prettyType(it) }
    is C.Type.Func      -> "(func ${prettyType(type.param)} → ${prettyType(type.result)})"
    is C.Type.Clos      -> "(clos ${prettyType(type.param)} → ${prettyType(type.result)})"
    is C.Type.Code      -> "`${prettyType(type.element)}"
    is C.Type.Var       -> type.name
    is C.Type.Def       -> type.name.toString()
    is C.Type.Meta      -> "?${type.index.toSubscript()}"
    is C.Type.Hole      -> " "
  }

fun prettyPattern(
  pattern: C.Pattern,
): String =
  when (pattern) {
    is C.Pattern.IntOf      -> pattern.value.toString()
    is C.Pattern.IntRangeOf -> "(${pattern.min} .. ${pattern.max})"
    is C.Pattern.ListOf     -> pattern.elements.joinToString(", ", "[", "]") { prettyPattern(it) }
    is C.Pattern.CompoundOf -> pattern.elements.entries.joinToString(", ", "{", "}") { (key, element) -> "$key: ${prettyPattern(element)}" }
    is C.Pattern.TupleOf    -> pattern.elements.joinToString(", ", "(", ")") { prettyPattern(it) }
    is C.Pattern.Var        -> "(${pattern.name}: ${prettyType(pattern.type)})"
    is C.Pattern.Drop       -> "(_: ${prettyType(pattern.type)})"
    is C.Pattern.Hole       -> " "
  }
