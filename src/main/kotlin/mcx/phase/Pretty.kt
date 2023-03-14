package mcx.phase

import mcx.ast.Core.Pattern
import mcx.ast.Core.Term
import mcx.data.NbtType
import mcx.util.quoted
import mcx.util.toSubscript

fun prettyTerm(term: Term): String {
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
    is Term.Type        -> "type ${prettyTerm(term.tag)}"
    is Term.Bool        -> "bool"
    is Term.BoolOf      -> term.value.toString()
    is Term.If          -> "if ${prettyTerm(term.condition)} then ${prettyTerm(term.thenBranch)} else ${prettyTerm(term.elseBranch)}"
    is Term.Is          -> "${prettyTerm(term.scrutinee)} is ${prettyPattern(term.scrutineer)}"
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
    is Term.ByteArrayOf -> term.elements.joinToString(", ", "[byte; ", "]") { prettyTerm(it) }
    is Term.IntArray    -> "int_array"
    is Term.IntArrayOf  -> term.elements.joinToString(", ", "[int; ", "]") { prettyTerm(it) }
    is Term.LongArray   -> "long_array"
    is Term.LongArrayOf -> term.elements.joinToString(", ", "[long; ", "]") { prettyTerm(it) }
    is Term.List        -> "list ${prettyTerm(term.element)}"
    is Term.ListOf      -> term.elements.joinToString(", ", "[", "]") { prettyTerm(it) }
    is Term.Compound    -> term.elements.entries.joinToString(", ", "compound {", "}") { (key, element) -> "$key: ${prettyTerm(element)}" }
    is Term.CompoundOf  -> term.elements.entries.joinToString(", ", "{", "}") { (key, element) -> "$key: ${prettyTerm(element)}" }
    is Term.Union       -> term.elements.joinToString(", ", "union {", "}") { prettyTerm(it) }
    is Term.Func        -> "func ${term.params.joinToString(", ", "(", ")") { (binder, type) -> "${prettyPattern(binder)}: ${prettyTerm(type)}" }} -> ${prettyTerm(term.result)}"
    is Term.FuncOf      -> "\\${term.params.joinToString(", ", "(", ")") { prettyPattern(it) }} -> ${prettyTerm(term.result)}"
    is Term.Apply       -> "${prettyTerm(term.func)}${term.args.joinToString(", ", "(", ")") { prettyTerm(it) }}"
    is Term.Code        -> "code ${prettyTerm(term.element)}"
    is Term.CodeOf      -> "`${prettyTerm(term.element)}"
    is Term.Splice      -> "$${prettyTerm(term.element)}"
    is Term.Let         -> "let ${prettyPattern(term.binder)} = ${prettyTerm(term.init)};\n${prettyTerm(term.body)}"
    is Term.Var         -> term.name
    is Term.Def         -> term.name.toString()
    is Term.Meta        -> "?${term.index.toSubscript()}"
    is Term.Hole        -> "??"
  }
}

fun prettyPattern(pattern: Pattern): String {
  return when (pattern) {
    is Pattern.IntOf      -> pattern.value.toString()
    is Pattern.CompoundOf -> pattern.elements.entries.joinToString(", ", "{", "}") { (key, element) -> "$key: ${prettyPattern(element)}" }
    is Pattern.CodeOf     -> "`${prettyPattern(pattern.element)}"
    is Pattern.Var        -> pattern.name
    is Pattern.Drop       -> "_"
    is Pattern.Hole       -> "??"
  }
}
