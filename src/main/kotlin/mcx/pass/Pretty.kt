package mcx.pass

import mcx.ast.Core.Pattern
import mcx.ast.Core.Term
import mcx.util.quoted
import mcx.util.toSubscript

// TODO: Remove redundant parentheses.

fun prettyTerm(
  term: Term,
): String {
  fun go(
    term: Term,
  ): String {
    return when (term) {
      is Term.Tag     -> "tag"
      is Term.TagOf   -> "${term.value}_tag"
      is Term.Type    -> "(type ${go(term.element)})"
      is Term.Bool    -> "bool"
      is Term.BoolOf  -> term.value.toString()
      is Term.If      -> "(if ${go(term.condition)} then ${go(term.thenBranch)} else ${go(term.elseBranch)})"
      is Term.Is      -> "(${go(term.scrutinee)} is ${prettyPattern(term.scrutineer)})"
      is Term.Byte    -> "byte"
      is Term.ByteOf  -> "${term.value}b"
      is Term.Short   -> "short"
      is Term.ShortOf -> "${term.value}s"
      is Term.Int     -> "int"
      is Term.IntOf   -> term.value.toString()
      is Term.Long        -> "long"
      is Term.LongOf      -> "${term.value}L"
      is Term.Float       -> "float"
      is Term.FloatOf     -> "${term.value}f"
      is Term.Double      -> "double"
      is Term.DoubleOf    -> "${term.value}d"
      is Term.String      -> "string"
      is Term.StringOf    -> term.value.quoted('"')
      is Term.ByteArray   -> "byte_array"
      is Term.ByteArrayOf -> term.elements.joinToString(", ", "[byte; ", "]") { go(it) }
      is Term.IntArray    -> "int_array"
      is Term.IntArrayOf  -> term.elements.joinToString(", ", "[int; ", "]") { go(it) }
      is Term.LongArray   -> "long_array"
      is Term.LongArrayOf -> term.elements.joinToString(", ", "[long; ", "]") { go(it) }
      is Term.List        -> "(list ${go(term.element)})"
      is Term.ListOf      -> term.elements.joinToString(", ", "[", "]") { go(it) }
      is Term.Compound    -> term.elements.entries.joinToString(", ", "compound{", "}") { (key, element) -> "$key : ${go(element)}" }
      is Term.CompoundOf  -> term.elements.entries.joinToString(", ", "{", "}") { (key, element) -> "$key : ${go(element)}" }
      is Term.Point       -> "(point ${prettyTerm(term.element)})"
      is Term.Union       -> term.elements.joinToString(", ", "union{", "}") { go(it) }
      is Term.Func        -> "(func ${term.params.joinToString(", ", "(", ")") { (binder, type) -> "${prettyPattern(binder)} : ${go(type)}" }} -> ${go(term.result)})"
      is Term.FuncOf      -> "(\\${term.params.joinToString(", ", "(", ")") { prettyPattern(it) }} -> ${go(term.result)})"
      is Term.Apply       -> "(${go(term.func)}${term.args.joinToString(", ", "(", ")") { go(it) }})"
      is Term.Code        -> "(code ${go(term.element)})"
      is Term.CodeOf      -> "(`${go(term.element)})"
      is Term.Splice      -> "($${go(term.element)})"
      is Term.Let         -> "let ${prettyPattern(term.binder)} = ${go(term.init)};\n${go(term.body)}"
      is Term.Var         -> term.name
      is Term.Def         -> term.name.toString()
      is Term.Meta        -> "?${term.index.toSubscript()}"
      is Term.Hole        -> "??"
    }
  }
  return go(term)
}

fun prettyPattern(
  pattern: Pattern<*>,
): String {
  return when (pattern) {
    is Pattern.IntOf      -> pattern.value.toString()
    is Pattern.CompoundOf -> pattern.elements.entries.joinToString(", ", "{", "}") { (key, element) -> "$key : ${prettyPattern(element)}" }
    is Pattern.Var        -> pattern.name
    is Pattern.Drop       -> "_"
    is Pattern.Hole       -> "??"
  }
}
