package mcx.pass.backend

import mcx.ast.Modifier
import mcx.data.NbtType
import mcx.pass.Context
import mcx.pass.backend.Lift.Ctx.Companion.emptyCtx
import mcx.pass.prettyPattern
import mcx.pass.prettyTerm
import mcx.ast.Core as C
import mcx.ast.Lifted as L

class Lift private constructor(
  private val context: Context,
  private val definition: C.Definition,
) {
  private val liftedDefinitions: MutableList<L.Definition> = mutableListOf()
  private val dispatchedProcs: MutableList<L.Definition.Function> = mutableListOf()
  private val dispatchedFuncs: MutableList<L.Definition.Function> = mutableListOf()
  private var freshFunctionId: Int = 0

  private fun lift(): Result {
    val modifiers = definition.modifiers.mapNotNull { liftModifier(it) }
    liftedDefinitions += when (definition) {
      is C.Definition.Def -> {
        val body = definition.body?.let { emptyCtx().liftTerm(it) }
        L.Definition.Function(modifiers, definition.name, emptyList(), body, null)
      }
    }
    return Result(liftedDefinitions, dispatchedProcs, dispatchedFuncs)
  }

  private fun liftModifier(modifier: Modifier): L.Modifier? {
    return when (modifier) {
      Modifier.BUILTIN -> L.Modifier.BUILTIN
      Modifier.EXPORT  -> null
      Modifier.REC     -> null
      Modifier.CONST   -> error("unexpected modifier: ${modifier.id}")
      Modifier.TEST    -> L.Modifier.TEST
    }
  }

  private fun Ctx.liftTerm(term: C.Term): L.Term {
    return when (term) {
      is C.Term.Tag         -> unexpectedTerm(term)
      is C.Term.TagOf       -> UNIT
      is C.Term.Type        -> UNIT
      is C.Term.Bool        -> UNIT
      is C.Term.BoolOf      -> L.Term.ByteOf(if (term.value) 1 else 0)
      is C.Term.If          -> {
        val condition = liftTerm(term.condition)
        val thenBranch = liftTerm(term.thenBranch)
        val elseBranch = liftTerm(term.elseBranch)
        val thenFunction = createFreshFunction(thenBranch, 1)
        val elseFunction = createFreshFunction(elseBranch, null)
        val type = thenBranch.type
        L.Term.If(condition, thenFunction.name, elseFunction.name, type)
      }
      is C.Term.Is          -> {
        val scrutinee = liftTerm(term.scrutinee)
        restoring {
          val scrutineer = liftPattern(term.scrutineer)
          L.Term.Is(scrutinee, scrutineer)
        }
      }
      is C.Term.Byte        -> UNIT
      is C.Term.ByteOf      -> L.Term.ByteOf(term.value)
      is C.Term.Short       -> UNIT
      is C.Term.ShortOf     -> L.Term.ShortOf(term.value)
      is C.Term.Int         -> UNIT
      is C.Term.IntOf       -> L.Term.IntOf(term.value)
      is C.Term.Long        -> UNIT
      is C.Term.LongOf      -> L.Term.LongOf(term.value)
      is C.Term.Float       -> UNIT
      is C.Term.FloatOf     -> L.Term.FloatOf(term.value)
      is C.Term.Double      -> UNIT
      is C.Term.DoubleOf    -> L.Term.DoubleOf(term.value)
      is C.Term.String      -> UNIT
      is C.Term.StringOf    -> L.Term.StringOf(term.value)
      is C.Term.ByteArray   -> UNIT
      is C.Term.ByteArrayOf -> {
        val elements = term.elements.map { liftTerm(it) }
        L.Term.ByteArrayOf(elements)
      }
      is C.Term.IntArray    -> UNIT
      is C.Term.IntArrayOf  -> {
        val elements = term.elements.map { liftTerm(it) }
        L.Term.IntArrayOf(elements)
      }
      is C.Term.LongArray   -> UNIT
      is C.Term.LongArrayOf -> {
        val elements = term.elements.map { liftTerm(it) }
        L.Term.LongArrayOf(elements)
      }
      is C.Term.List        -> UNIT
      is C.Term.ListOf      -> {
        val elements = term.elements.map { liftTerm(it) }
        L.Term.ListOf(elements)
      }
      is C.Term.Compound    -> UNIT
      is C.Term.CompoundOf  -> {
        val elements = term.elements.mapValuesTo(linkedMapOf()) { liftTerm(it.value) }
        L.Term.CompoundOf(elements)
      }
      is C.Term.Point       -> UNIT
      is C.Term.Union       -> UNIT
      is C.Term.Func        -> UNIT
      is C.Term.FuncOf      -> {
        with(emptyCtx()) {
          if (term.open) {
            val freeVars = freeVars(term)
            val capture = L.Pattern.CompoundOf(freeVars.mapValuesTo(linkedMapOf()) { (name, type) -> L.Pattern.Var(name, type) })
            val binders = term.params.map { liftPattern(it) }
            val result = liftTerm(term.result)
            val tag = context.freshId()
            L.Definition.Function(emptyList(), definition.name.module / "${definition.name.name}:${freshFunctionId++}", binders + capture, result, tag).also {
              liftedDefinitions += it
              dispatchedFuncs += it
            }
            val entries = freeVars.map { (name, type) -> L.Term.FuncOf.Entry(name, type) }
            L.Term.FuncOf(entries, tag)
          } else {
            val binders = term.params.map { liftPattern(it) }
            val result = liftTerm(term.result)
            val tag = context.freshId()
            L.Definition.Function(emptyList(), definition.name.module / "${definition.name.name}:${freshFunctionId++}", binders, result, tag).also {
              liftedDefinitions += it
              dispatchedProcs += it
            }
            L.Term.ProcOf(tag)
          }
        }
      }
      is C.Term.Apply       -> {
        val func = liftTerm(term.func)
        val args = term.args.map { liftTerm(it) }
        val type = eraseType(term.type)
        L.Term.Apply(term.open, func, args, type)
      }
      is C.Term.Code        -> unexpectedTerm(term)
      is C.Term.CodeOf      -> unexpectedTerm(term)
      is C.Term.Splice      -> unexpectedTerm(term)
      is C.Term.Command     -> {
        val element = (term.element as C.Term.StringOf).value
        val type = eraseType(term.type)
        L.Term.Command(element, type)
      }
      is C.Term.Let         -> {
        val init = liftTerm(term.init)
        restoring {
          val binder = liftPattern(term.binder)
          val body = liftTerm(term.body)
          L.Term.Let(binder, init, body)
        }
      }
      is C.Term.Var         -> {
        val type = eraseType(term.type)
        L.Term.Var(term.name, term.idx, type)
      }
      is C.Term.Def         -> {
        val type = eraseType(term.type)
        L.Term.Def(term.name, type)
      }
      is C.Term.Meta        -> unexpectedTerm(term)
      is C.Term.Hole        -> unexpectedTerm(term)
    }
  }

  private fun Ctx.liftPattern(pattern: C.Pattern<C.Term>): L.Pattern {
    return when (pattern) {
      is C.Pattern.IntOf      -> L.Pattern.IntOf(pattern.value)
      is C.Pattern.CompoundOf -> {
        val elements = pattern.elements.mapValuesTo(linkedMapOf()) { (_, element) -> liftPattern(element) }
        L.Pattern.CompoundOf(elements)
      }
      is C.Pattern.Var        -> {
        val type = eraseType(pattern.type)
        bind(pattern.name, type)
        L.Pattern.Var(pattern.name, type)
      }
      is C.Pattern.Drop       -> {
        val type = eraseType(pattern.type)
        L.Pattern.Drop(type)
      }
      is C.Pattern.Hole       -> unexpectedPattern(pattern)
    }
  }

  private fun freeVars(term: C.Term): LinkedHashMap<String, NbtType> {
    return when (term) {
      is C.Term.Tag         -> unexpectedTerm(term)
      is C.Term.TagOf       -> linkedMapOf()
      is C.Term.Type        -> freeVars(term.element)
      is C.Term.Bool        -> linkedMapOf()
      is C.Term.BoolOf      -> linkedMapOf()
      is C.Term.If          -> freeVars(term.condition).also { it += freeVars(term.thenBranch); it += freeVars(term.elseBranch) }
      is C.Term.Is          -> freeVars(term.scrutinee)
      is C.Term.Byte        -> linkedMapOf()
      is C.Term.ByteOf      -> linkedMapOf()
      is C.Term.Short       -> linkedMapOf()
      is C.Term.ShortOf     -> linkedMapOf()
      is C.Term.Int         -> linkedMapOf()
      is C.Term.IntOf       -> linkedMapOf()
      is C.Term.Long        -> linkedMapOf()
      is C.Term.LongOf      -> linkedMapOf()
      is C.Term.Float       -> linkedMapOf()
      is C.Term.FloatOf     -> linkedMapOf()
      is C.Term.Double      -> linkedMapOf()
      is C.Term.DoubleOf    -> linkedMapOf()
      is C.Term.String      -> linkedMapOf()
      is C.Term.StringOf    -> linkedMapOf()
      is C.Term.ByteArray   -> linkedMapOf()
      is C.Term.ByteArrayOf -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.IntArray    -> linkedMapOf()
      is C.Term.IntArrayOf  -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.LongArray   -> linkedMapOf()
      is C.Term.LongArrayOf -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.List        -> freeVars(term.element)
      is C.Term.ListOf      -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.Compound    -> term.elements.values.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.CompoundOf  -> term.elements.values.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.Point       -> freeVars(term.element)
      is C.Term.Union       -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.Func        -> freeVars(term.result).also { result -> term.params.forEach { result -= boundVars(it.first) } }
      is C.Term.FuncOf      -> freeVars(term.result).also { result -> term.params.forEach { result -= boundVars(it) } }
      is C.Term.Apply       -> freeVars(term.func).also { func -> term.args.forEach { func += freeVars(it) } }
      is C.Term.Code        -> unexpectedTerm(term)
      is C.Term.CodeOf      -> unexpectedTerm(term)
      is C.Term.Splice      -> unexpectedTerm(term)
      is C.Term.Command     -> linkedMapOf()
      is C.Term.Let         -> freeVars(term.init).also { it += freeVars(term.body); it -= boundVars(term.binder) }
      is C.Term.Var         -> linkedMapOf(term.name to eraseType(term.type))
      is C.Term.Def         -> linkedMapOf()
      is C.Term.Meta        -> unexpectedTerm(term)
      is C.Term.Hole        -> unexpectedTerm(term)
    }
  }

  private fun boundVars(pattern: C.Pattern<*>): Set<String> {
    return when (pattern) {
      is C.Pattern.IntOf      -> emptySet()
      is C.Pattern.CompoundOf -> pattern.elements.flatMapTo(hashSetOf()) { (_, element) -> boundVars(element) }
      is C.Pattern.Var        -> setOf(pattern.name)
      is C.Pattern.Drop       -> emptySet()
      is C.Pattern.Hole       -> unexpectedPattern(pattern)
    }
  }

  private fun eraseType(type: C.Term): NbtType {
    return when (type) {
      is C.Term.Tag         -> unexpectedTerm(type)
      is C.Term.TagOf       -> unexpectedTerm(type)
      is C.Term.Type        -> NbtType.BYTE
      is C.Term.Bool        -> NbtType.BYTE
      is C.Term.BoolOf      -> unexpectedTerm(type)
      is C.Term.If          -> eraseType(type.thenBranch)
      is C.Term.Is          -> unexpectedTerm(type)
      is C.Term.Byte        -> NbtType.BYTE
      is C.Term.ByteOf      -> unexpectedTerm(type)
      is C.Term.Short       -> NbtType.SHORT
      is C.Term.ShortOf     -> unexpectedTerm(type)
      is C.Term.Int         -> NbtType.INT
      is C.Term.IntOf       -> unexpectedTerm(type)
      is C.Term.Long        -> NbtType.LONG
      is C.Term.LongOf      -> unexpectedTerm(type)
      is C.Term.Float       -> NbtType.FLOAT
      is C.Term.FloatOf     -> unexpectedTerm(type)
      is C.Term.Double      -> NbtType.DOUBLE
      is C.Term.DoubleOf    -> unexpectedTerm(type)
      is C.Term.String      -> NbtType.STRING
      is C.Term.StringOf    -> unexpectedTerm(type)
      is C.Term.ByteArray   -> NbtType.BYTE_ARRAY
      is C.Term.ByteArrayOf -> unexpectedTerm(type)
      is C.Term.IntArray    -> NbtType.INT_ARRAY
      is C.Term.IntArrayOf  -> unexpectedTerm(type)
      is C.Term.LongArray   -> NbtType.LONG_ARRAY
      is C.Term.LongArrayOf -> unexpectedTerm(type)
      is C.Term.List        -> NbtType.LIST
      is C.Term.ListOf      -> unexpectedTerm(type)
      is C.Term.Compound    -> NbtType.COMPOUND
      is C.Term.CompoundOf  -> unexpectedTerm(type)
      is C.Term.Point       -> eraseType(type.elementType)
      is C.Term.Union       -> type.elements.firstOrNull()?.let { eraseType(it) } ?: NbtType.END
      is C.Term.Func        -> NbtType.COMPOUND
      is C.Term.FuncOf      -> unexpectedTerm(type)
      is C.Term.Apply       -> ((type.type as C.Term.Type).element as C.Term.TagOf).value
      is C.Term.Code        -> unexpectedTerm(type)
      is C.Term.CodeOf      -> unexpectedTerm(type)
      is C.Term.Splice      -> unexpectedTerm(type)
      is C.Term.Command     -> ((type.type as C.Term.Type).element as C.Term.TagOf).value
      is C.Term.Let         -> eraseType(type.body)
      is C.Term.Var         -> ((type.type as C.Term.Type).element as C.Term.TagOf).value
      is C.Term.Def         -> ((type.type as C.Term.Type).element as C.Term.TagOf).value
      is C.Term.Meta        -> unexpectedTerm(type)
      is C.Term.Hole        -> unexpectedTerm(type)
    }
  }

  private fun Ctx.createFreshFunction(
    body: L.Term,
    restore: Int?,
  ): L.Definition.Function {
    val params = types.map { (name, type) -> L.Pattern.Var(name, type) }
    return L.Definition.Function(
      listOf(L.Modifier.NO_DROP),
      definition.name.module / "${definition.name.name}:${freshFunctionId++}",
      params,
      body,
      restore,
    ).also { liftedDefinitions += it }
  }

  private class Ctx private constructor() {
    private val _types: MutableList<Pair<String, NbtType>> = mutableListOf()
    val types: List<Pair<String, NbtType>> get() = _types

    fun bind(
      name: String,
      type: NbtType,
    ) {
      _types += name to type
    }

    inline fun <R> restoring(action: () -> R): R {
      val restore = _types.size
      val result = action()
      repeat(_types.size - restore) {
        _types.removeLast()
      }
      return result
    }

    companion object {
      fun emptyCtx(): Ctx {
        return Ctx()
      }
    }
  }

  data class Result(
    val liftedDefinitions: List<L.Definition>,
    val dispatchedProcs: List<L.Definition.Function>,
    val dispatchedFuncs: List<L.Definition.Function>,
  )

  companion object {
    private val UNIT: L.Term = L.Term.ByteOf(0)

    private fun unexpectedTerm(term: C.Term): Nothing {
      error("unexpected term: ${prettyTerm(term)}")
    }

    private fun unexpectedPattern(pattern: C.Pattern<*>): Nothing {
      error("unexpected pattern: ${prettyPattern(pattern)}")
    }

    operator fun invoke(
      context: Context,
      definition: C.Definition,
    ): Result {
      return Lift(context, definition).lift()
    }
  }
}
