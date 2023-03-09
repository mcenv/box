package mcx.phase

import mcx.ast.Core.Pattern
import mcx.ast.Core.Term
import mcx.data.NbtType
import mcx.util.quoted
import mcx.util.toSubscript

fun prettyTerm(term: Term): String {
  return when (term) {
    is Term.Tag         -> "Tag"
    is Term.TagOf       -> {
      when (term.value) {
        NbtType.END        -> "EndTag"
        NbtType.BYTE       -> "ByteTag"
        NbtType.SHORT      -> "ShortTag"
        NbtType.INT        -> "IntTag"
        NbtType.LONG       -> "LongTag"
        NbtType.FLOAT      -> "FloatTag"
        NbtType.DOUBLE     -> "DoubleTag"
        NbtType.BYTE_ARRAY -> "ByteArrayTag"
        NbtType.STRING     -> "StringTag"
        NbtType.LIST       -> "ListTag"
        NbtType.COMPOUND   -> "CompoundTag"
        NbtType.INT_ARRAY  -> "IntArrayTag"
        NbtType.LONG_ARRAY -> "LongArrayTag"
      }
    }
    is Term.Type        -> "Type ${prettyTerm(term.tag)}"
    is Term.Bool        -> "Bool"
    is Term.BoolOf      -> term.value.toString()
    is Term.If          -> "if ${prettyTerm(term.condition)} then ${prettyTerm(term.thenBranch)} else ${prettyTerm(term.elseBranch)}"
    is Term.Is          -> "${prettyTerm(term.scrutinee)} is ${prettyPattern(term.scrutineer)}"
    is Term.Byte        -> "Byte"
    is Term.ByteOf      -> "${term.value}b"
    is Term.Short       -> "Short"
    is Term.ShortOf     -> "${term.value}s"
    is Term.Int         -> "Int"
    is Term.IntOf       -> term.value.toString()
    is Term.Long        -> "Long"
    is Term.LongOf      -> "${term.value}L"
    is Term.Float       -> "Float"
    is Term.FloatOf     -> "${term.value}f"
    is Term.Double      -> "Double"
    is Term.DoubleOf    -> "${term.value}d"
    is Term.String      -> "String"
    is Term.StringOf    -> term.value.quoted('"')
    is Term.ByteArray   -> "ByteArray"
    is Term.ByteArrayOf -> term.elements.joinToString(", ", "[Byte; ", "]") { prettyTerm(it) }
    is Term.IntArray    -> "IntArray"
    is Term.IntArrayOf  -> term.elements.joinToString(", ", "[Int; ", "]") { prettyTerm(it) }
    is Term.LongArray   -> "LongArray"
    is Term.LongArrayOf -> term.elements.joinToString(", ", "[Long; ", "]") { prettyTerm(it) }
    is Term.List        -> "List ${prettyTerm(term.element)}"
    is Term.ListOf      -> term.elements.joinToString(", ", "[", "]") { prettyTerm(it) }
    is Term.Compound    -> term.elements.entries.joinToString(", ", "Compound {", "}") { (key, element) -> "$key: ${prettyTerm(element)}" }
    is Term.CompoundOf  -> term.elements.entries.joinToString(", ", "{", "}") { (key, element) -> "$key: ${prettyTerm(element)}" }
    is Term.Union       -> term.elements.joinToString(", ", "Union {", "}") { prettyTerm(it) }
    is Term.Func        -> "Func ${term.params.joinToString(", ", "(", ")") { (binder, type) -> "${prettyPattern(binder)}: ${prettyTerm(type)}" }} -> ${prettyTerm(term.result)}"
    is Term.FuncOf      -> "\\${term.params.joinToString(", ", "(", ")") { prettyPattern(it) }} -> ${prettyTerm(term.result)}"
    is Term.Apply       -> "${prettyTerm(term.func)}${term.args.joinToString(", ", "(", ")") { prettyTerm(it) }}"
    is Term.Code        -> "Code ${prettyTerm(term.element)}"
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
    is Pattern.Splice     -> "$${prettyPattern(pattern.element)}"
    is Pattern.Var        -> pattern.name
    is Pattern.Drop       -> "_"
    is Pattern.Hole       -> "??"
  }
}
