package mcx.phase

import mcx.ast.Core.Pattern
import mcx.ast.Core.Term
import mcx.data.NbtType
import mcx.util.quoted
import mcx.util.toSubscript

fun prettyTerm(
  term: Term,
): String {
  fun Int.go(
    term: Term,
  ): String {
    return when (term) {
      is Term.Tag         -> "tag"
      is Term.TagOf       -> {
        when (term.value) {
          NbtType.END        -> "end_tag"
          NbtType.BYTE       -> "byte_tag"
          NbtType.SHORT      -> "short_tag"
          NbtType.INT        -> "int_tag"
          NbtType.LONG       -> "long_tag"
          NbtType.FLOAT      -> "float_tag"
          NbtType.DOUBLE     -> "double_tag"
          NbtType.STRING     -> "string_tag"
          NbtType.BYTE_ARRAY -> "byte_array_tag"
          NbtType.INT_ARRAY  -> "int_array_tag"
          NbtType.LONG_ARRAY -> "long_array_tag"
          NbtType.LIST       -> "list_tag"
          NbtType.COMPOUND   -> "compound_tag"
        }
      }
      is Term.Type        -> "type ${2.go(term.tag)}"
      is Term.Bool        -> "bool"
      is Term.BoolOf      -> term.value.toString()
      is Term.If          -> "if ${1.go(term.condition)} then ${1.go(term.thenBranch)} else ${1.go(term.elseBranch)}"
      is Term.Is          -> par(0, "${1.go(term.scrutinee)} is ${prettyPattern(term.scrutineer)}")
      is Term.Byte        -> "byte"
      is Term.ByteOf      -> "${term.value}b"
      is Term.Short       -> "short"
      is Term.ShortOf     -> "${term.value}s"
      is Term.Int         -> "int"
      is Term.IntOf       -> term.value.toString()
      is Term.Long        -> "long"
      is Term.LongOf      -> "${term.value}L"
      is Term.Float       -> "float"
      is Term.FloatOf     -> "${term.value}f"
      is Term.Double      -> "double"
      is Term.DoubleOf    -> "${term.value}d"
      is Term.String      -> "string"
      is Term.StringOf    -> term.value.quoted('"')
      is Term.ByteArray   -> "byte_array"
      is Term.ByteArrayOf -> term.elements.joinToString(", ", "[byte; ", "]") { 0.go(it) }
      is Term.IntArray    -> "int_array"
      is Term.IntArrayOf  -> term.elements.joinToString(", ", "[int; ", "]") { 0.go(it) }
      is Term.LongArray   -> "long_array"
      is Term.LongArrayOf -> term.elements.joinToString(", ", "[long; ", "]") { 0.go(it) }
      is Term.List        -> "list ${2.go(term.element)}"
      is Term.ListOf      -> term.elements.joinToString(", ", "[", "]") { 0.go(it) }
      is Term.Compound    -> term.elements.entries.joinToString(", ", "compound {", "}") { (key, element) -> "$key : ${0.go(element)}" }
      is Term.CompoundOf  -> term.elements.entries.joinToString(", ", "{", "}") { (key, element) -> "$key : ${0.go(element)}" }
      is Term.Union       -> term.elements.joinToString(", ", "union {", "}") { 0.go(it) }
      is Term.Func        -> "func ${term.params.joinToString(", ", "(", ")") { (binder, type) -> "${prettyPattern(binder)} : ${0.go(type)}" }} -> ${0.go(term.result)}"
      is Term.FuncOf      -> "\\${term.params.joinToString(", ", "(", ")") { prettyPattern(it) }} -> ${0.go(term.result)}"
      is Term.Apply       -> par(1, "${2.go(term.func)}${term.args.joinToString(", ", "(", ")") { 0.go(it) }}")
      is Term.Let         -> "let ${prettyPattern(term.binder)} = ${0.go(term.init)};\n${0.go(term.body)}"
      is Term.Var         -> term.name
      is Term.Def         -> term.name.toString()
      is Term.Meta        -> "?${term.index.toSubscript()}"
      is Term.Hole        -> "??"
    }
  }
  return 0.go(term)
}

fun prettyPattern(
  pattern: Pattern,
): String {
  return when (pattern) {
    is Pattern.IntOf      -> pattern.value.toString()
    is Pattern.CompoundOf -> pattern.elements.joinToString(", ", "{", "}") { (key, element) -> "$key : ${prettyPattern(element)}" }
    is Pattern.Var        -> pattern.name
    is Pattern.Drop       -> "_"
    is Pattern.Hole       -> "??"
  }
}

private fun Int.par(
  prec: Int,
  string: String,
): String {
  return if (prec < this) "($string)" else string
}
