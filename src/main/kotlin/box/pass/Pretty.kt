package box.pass

import box.ast.Core.Pattern
import box.ast.Core.Term
import box.ast.common.Proj
import box.ast.common.Repr
import box.util.quoted
import box.util.toSubscript
import box.util.unreachable

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
      is Term.Unit       -> "unit"
      is Term.Bool       -> "bool"
      is Term.I8         -> "i8"
      is Term.I16        -> "i16"
      is Term.I32        -> "i32"
      is Term.I64        -> "i64"
      is Term.F32        -> "f32"
      is Term.F64        -> "f64"
      is Term.Wtf16      -> "wtf16"
      is Term.ConstOf<*> -> when (term.value) {
        is Unit    -> "()"
        is Boolean -> term.value.toString()
        is Byte    -> "${term.value}i8"
        is Short   -> "${term.value}i16"
        is Int     -> "${term.value}i32"
        is Long    -> "${term.value}i64"
        is Float   -> "${term.value}f32"
        is Double  -> "${term.value}f64"
        is String  -> term.value.quoted('"')
        else       -> unreachable()
      }
      is Term.I8Array    -> "i8_array"
      is Term.I8ArrayOf  -> term.elements.joinToString(", ", "[i8; ", "]") { go(it) }
      is Term.I32Array   -> "i32_array"
      is Term.I32ArrayOf -> term.elements.joinToString(", ", "[i32; ", "]") { go(it) }
      is Term.I64Array   -> "i64_array"
      is Term.I64ArrayOf -> term.elements.joinToString(", ", "[i64; ", "]") { go(it) }
      is Term.List       -> "(list ${go(term.element)})"
      is Term.ListOf     -> term.elements.joinToString(", ", "[", "]") { go(it) }
      is Term.Compound   -> term.elements.entries.joinToString(", ", "(compound {", "})") { (key, element) -> "$key : ${go(element)}" }
      is Term.CompoundOf -> term.elements.entries.joinToString(", ", "{", "}") { (key, element) -> "$key : ${go(element)}" }
      is Term.Point      -> "(point ${prettyTerm(term.element)})"
      is Term.Union      -> term.elements.joinToString(", ", "(union {", "})") { go(it) }
      is Term.Func       -> "(${if (term.open) "func" else "proc"} ${term.params.joinToString(", ", "(", ")") { (binder, type) -> "${prettyPattern(binder)} : ${go(type)}" }} -> ${go(term.result)})"
      is Term.FuncOf     -> "(\\${if (term.open) "\\" else ""}${term.params.joinToString(", ", "(", ")") { prettyPattern(it) }} -> ${go(term.result)})"
      is Term.Apply      -> "(${go(term.func)}${term.args.joinToString(", ", "(", ")") { go(it) }})"
      is Term.Code       -> "(code ${go(term.element)})"
      is Term.CodeOf     -> "(`${go(term.element)})"
      is Term.Splice     -> "($${go(term.element)})"
      is Term.Path       -> "(path ${go(term.element)})"
      is Term.PathOf     -> "(&${go(term.element)})"
      is Term.Get        -> "(*${go(term.element)})"
      is Term.Command    -> "(/${go(term.element)})"
      is Term.Let        -> "let ${prettyPattern(term.binder)} = ${go(term.init)};\n${go(term.body)}"
      is Term.If         -> "if ${go(term.scrutinee)} ${term.branches.joinToString(", ", "[", "]") { (pattern, body) -> "${prettyPattern(pattern)} -> ${go(body)}" }}"
      is Term.Project    -> "(${go(term.target)}.${term.projs.joinToString(".") { prettyProjection(it) }})"
      is Term.Var        -> term.name
      is Term.Def        -> term.def.name.toString()
      is Term.Meta       -> "?${term.index.toSubscript()}"
      is Term.Builtin    -> "(builtin ${term.builtin.name})"
      is Term.Hole       -> "??"
    }
  }
  return go(term)
}

fun prettyPattern(
  pattern: Pattern,
): String {
  return when (pattern) {
    is Pattern.ConstOf    -> when (pattern.value) {
      is Unit    -> "()"
      is Boolean -> pattern.value.toString()
      is Byte    -> "${pattern.value}i8"
      is Short   -> "${pattern.value}i16"
      is Int     -> "${pattern.value}i32"
      is Long    -> "${pattern.value}i64"
      is Float   -> "${pattern.value}f32"
      is Double  -> "${pattern.value}f64"
      is String  -> pattern.value.quoted('"')
      else       -> unreachable()
    }
    is Pattern.I8ArrayOf  -> pattern.elements.joinToString(", ", "[i8; ", "]") { prettyPattern(it) }
    is Pattern.I32ArrayOf -> pattern.elements.joinToString(", ", "[i32; ", "]") { prettyPattern(it) }
    is Pattern.I64ArrayOf -> pattern.elements.joinToString(", ", "[i64; ", "]") { prettyPattern(it) }
    is Pattern.ListOf     -> pattern.elements.joinToString(", ", "[", "]") { prettyPattern(it) }
    is Pattern.CompoundOf -> pattern.elements.entries.joinToString(", ", "{", "}") { (key, element) -> "$key : ${prettyPattern(element)}" }
    is Pattern.Var        -> pattern.name
    is Pattern.Drop       -> "_"
    is Pattern.Hole       -> "??"
  }
}

fun prettyProjection(
  proj: Proj,
): String {
  return when (proj) {
    is Proj.I8ArrayOf  -> "[${proj.index}]"
    is Proj.I32ArrayOf -> "[${proj.index}]"
    is Proj.I64ArrayOf -> "[${proj.index}]"
    is Proj.ListOf     -> "[${proj.index}]"
    is Proj.CompoundOf -> proj.name
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
