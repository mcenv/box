package mcx.pass

import mcx.ast.Core.Pattern
import mcx.ast.Core.Term
import mcx.ast.common.Proj
import mcx.ast.common.Repr
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
      is Term.Tag        -> "tag"
      is Term.TagOf      -> prettyRepr(term.repr)
      is Term.Type       -> "(type ${go(term.element)})"
      is Term.Bool       -> "bool"
      is Term.BoolOf     -> term.value.toString()
      is Term.I8         -> "i8"
      is Term.I8Of       -> "${term.value}i8"
      is Term.I16        -> "i16"
      is Term.I16Of      -> "${term.value}i16"
      is Term.I32        -> "i32"
      is Term.I32Of      -> "${term.value}i32"
      is Term.I64        -> "i64"
      is Term.I64Of      -> "${term.value}i64"
      is Term.F32        -> "f32"
      is Term.F32Of      -> "${term.value}f32"
      is Term.F64        -> "f64"
      is Term.F64Of      -> "${term.value}f64"
      is Term.Wtf16      -> "wtf16"
      is Term.Wtf16Of    -> term.value.quoted('"')
      is Term.I8Array    -> "i8_array"
      is Term.I8ArrayOf  -> term.elements.joinToString(", ", "[i8; ", "]") { go(it) }
      is Term.I32Array   -> "i32_array"
      is Term.I32ArrayOf -> term.elements.joinToString(", ", "[i32; ", "]") { go(it) }
      is Term.I64Array   -> "i64_array"
      is Term.I64ArrayOf -> term.elements.joinToString(", ", "[i64; ", "]") { go(it) }
      is Term.Vec        -> "(vec ${go(term.element)})"
      is Term.VecOf      -> term.elements.joinToString(", ", "[", "]") { go(it) }
      is Term.Struct     -> term.elements.entries.joinToString(", ", "(struct {", "})") { (key, element) -> "$key : ${go(element)}" }
      is Term.StructOf   -> term.elements.entries.joinToString(", ", "{", "}") { (key, element) -> "$key : ${go(element)}" }
      is Term.Point      -> "(point ${prettyTerm(term.element)})"
      is Term.Union      -> term.elements.joinToString(", ", "(union {", "})") { go(it) }
      is Term.Func       -> "(${if (term.open) "func" else "proc"} ${term.params.joinToString(", ", "(", ")") { (binder, type) -> "${prettyPattern(binder)} : ${go(type)}" }} -> ${go(term.result)})"
      is Term.FuncOf     -> "(\\${if (term.open) "\\" else ""}${term.params.joinToString(", ", "(", ")") { prettyPattern(it) }} -> ${go(term.result)})"
      is Term.Apply      -> "(${go(term.func)}${term.args.joinToString(", ", "(", ")") { go(it) }})"
      is Term.Code       -> "(code ${go(term.element)})"
      is Term.CodeOf     -> "(`${go(term.element)})"
      is Term.Splice     -> "($${go(term.element)})"
      is Term.Command    -> "(/${go(term.element)})"
      is Term.Let        -> "let ${prettyPattern(term.binder)} = ${go(term.init)};\n${go(term.body)}"
      is Term.Match      -> "match ${go(term.scrutinee)} ${term.branches.joinToString(", ", "[", "]") { (pattern, body) -> "${prettyPattern(pattern)} -> ${go(body)}" }}"
      is Term.Project    -> "(${go(term.target)}.${term.projs.joinToString(".") { prettyProjection(it) }})"
      is Term.Var        -> term.name
      is Term.Def        -> term.def.name.toString()
      is Term.Meta       -> "?${term.index.toSubscript()}"
      is Term.Hole       -> "??"
    }
  }
  return go(term)
}

fun prettyPattern(
  pattern: Pattern,
): String {
  return when (pattern) {
    is Pattern.BoolOf   -> pattern.value.toString()
    is Pattern.I32Of    -> "${pattern.value}i32"
    is Pattern.VecOf    -> pattern.elements.joinToString(", ", "[", "]") { prettyPattern(it) }
    is Pattern.StructOf -> pattern.elements.entries.joinToString(", ", "{", "}") { (key, element) -> "$key : ${prettyPattern(element)}" }
    is Pattern.Var      -> pattern.name
    is Pattern.Drop     -> "_"
    is Pattern.Hole     -> "??"
  }
}

fun prettyProjection(
  proj: Proj,
): String {
  return when (proj) {
    is Proj.VecOf    -> "[${proj.index}]"
    is Proj.StructOf -> proj.name
  }
}

fun prettyRepr(
  repr: Repr,
): String {
  return when (repr) {
    Repr.END        -> "%end"
    Repr.BYTE       -> "%byte"
    Repr.SHORT      -> "%short"
    Repr.INT        -> "%int"
    Repr.LONG       -> "%long"
    Repr.FLOAT      -> "%float"
    Repr.DOUBLE     -> "%double"
    Repr.STRING     -> "%string"
    Repr.BYTE_ARRAY -> "%byte_array"
    Repr.INT_ARRAY  -> "%int_array"
    Repr.LONG_ARRAY -> "%long_array"
    Repr.LIST       -> "%list"
    Repr.COMPOUND   -> "%compound"
  }
}
