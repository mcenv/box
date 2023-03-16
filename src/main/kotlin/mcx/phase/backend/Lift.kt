package mcx.phase.backend

import mcx.ast.Lvl
import mcx.ast.Modifier
import mcx.ast.toLvl
import mcx.data.NbtType
import mcx.phase.Context
import mcx.phase.backend.Lift.Ctx.Companion.emptyCtx
import mcx.phase.prettyPattern
import mcx.phase.prettyTerm
import mcx.ast.Core as C
import mcx.ast.Lifted as L

class Lift private constructor(
  private val context: Context,
  private val definition: C.Definition,
) {
  private val liftedDefinitions: MutableList<L.Definition> = mutableListOf()
  private val dispatchedDefinitions: MutableList<L.Definition.Function> = mutableListOf()
  private var freshFunctionId: Int = 0

  private fun lift(): Result {
    val modifiers = definition.modifiers.mapNotNull { liftModifier(it) }
    when (definition) {
      is C.Definition.Def -> {
        val body = emptyCtx().liftTerm(definition.body!!)
        liftedDefinitions += L.Definition.Function(modifiers, definition.name, emptyList(), body, null)
      }
    }
    return Result(liftedDefinitions, dispatchedDefinitions)
  }

  private fun liftModifier(
    modifier: Modifier,
  ): L.Modifier? {
    return when (modifier) {
      Modifier.BUILTIN -> L.Modifier.BUILTIN
      Modifier.EXPORT  -> null
      Modifier.REC     -> null
      Modifier.INLINE  -> unexpectedModifier(modifier)
      Modifier.CONST   -> unexpectedModifier(modifier)
      Modifier.WORLD   -> null
    }
  }

  private fun Ctx.liftTerm(
    term: C.Term,
  ): L.Term {
    return when (term) {
      is C.Term.Tag         -> unexpectedTerm(term)
      is C.Term.TagOf       -> L.Term.TagOf(term.value)
      is C.Term.Type        -> L.Term.Unit
      is C.Term.Bool        -> L.Term.Unit
      is C.Term.BoolOf      -> L.Term.BoolOf(term.value)
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
      is C.Term.Byte        -> L.Term.Unit
      is C.Term.ByteOf      -> L.Term.ByteOf(term.value)
      is C.Term.Short       -> L.Term.Unit
      is C.Term.ShortOf     -> L.Term.ShortOf(term.value)
      is C.Term.Int         -> L.Term.Unit
      is C.Term.IntOf       -> L.Term.IntOf(term.value)
      is C.Term.Long        -> L.Term.Unit
      is C.Term.LongOf      -> L.Term.LongOf(term.value)
      is C.Term.Float       -> L.Term.Unit
      is C.Term.FloatOf     -> L.Term.FloatOf(term.value)
      is C.Term.Double      -> L.Term.Unit
      is C.Term.DoubleOf    -> L.Term.DoubleOf(term.value)
      is C.Term.String      -> L.Term.Unit
      is C.Term.StringOf    -> L.Term.StringOf(term.value)
      is C.Term.ByteArray   -> L.Term.Unit
      is C.Term.ByteArrayOf -> {
        val elements = term.elements.map { liftTerm(it) }
        L.Term.ByteArrayOf(elements)
      }
      is C.Term.IntArray    -> L.Term.Unit
      is C.Term.IntArrayOf  -> {
        val elements = term.elements.map { liftTerm(it) }
        L.Term.IntArrayOf(elements)
      }
      is C.Term.LongArray   -> L.Term.Unit
      is C.Term.LongArrayOf -> {
        val elements = term.elements.map { liftTerm(it) }
        L.Term.LongArrayOf(elements)
      }
      is C.Term.List        -> L.Term.Unit
      is C.Term.ListOf      -> {
        val elements = term.elements.map { liftTerm(it) }
        L.Term.ListOf(elements)
      }
      is C.Term.Compound    -> L.Term.Unit
      is C.Term.CompoundOf  -> {
        val elements = term.elements.mapValues { liftTerm(it.value) }
        L.Term.CompoundOf(elements)
      }
      is C.Term.Union       -> L.Term.Unit
      is C.Term.Func        -> L.Term.Unit
      is C.Term.FuncOf      -> {
        restoring { // ?
          val binders = term.params.map { liftPattern(it) }
          val freeVars = freeVars(term)
          freeVars.forEach { (name, type) -> bind(name, type) }
          val capture = L.Pattern.CompoundOf(
            freeVars.entries.map { (name, type) ->
              name to L.Pattern.Var(name, type)
            }
          )
          val result = liftTerm(term.result)
          val tag = context.freshId()
          L.Definition.Function(
            emptyList(),
            definition.name.module / "${definition.name.name}:${freshFunctionId++}",
            binders + capture,
            result,
            tag,
          ).also {
            liftedDefinitions += it
            dispatchedDefinitions += it
          }
          val types = freeVars.entries.map { (name, type) -> name to type }
          L.Term.FuncOf(types, tag)
        }
      }
      is C.Term.Apply       -> {
        val func = liftTerm(term.func)
        val args = term.args.map { liftTerm(it) }
        val type = NbtType.END // TODO
        L.Term.Apply(func, args, type)
      }
      is C.Term.Code        -> unexpectedTerm(term)
      is C.Term.CodeOf      -> unexpectedTerm(term)
      is C.Term.Splice      -> unexpectedTerm(term)
      is C.Term.Let         -> {
        val init = liftTerm(term.init)
        restoring {
          val binder = liftPattern(term.binder)
          val body = liftTerm(term.body)
          L.Term.Let(binder, init, body)
        }
      }
      is C.Term.Var         -> {
        val type = this[next.toLvl(term.idx)].second
        L.Term.Var(term.name, term.idx, type)
      }
      is C.Term.Def         -> {
        val type = NbtType.END // TODO
        L.Term.Def(term.name, type)
      }
      is C.Term.Meta        -> unexpectedTerm(term)
      is C.Term.Hole        -> unexpectedTerm(term)
    }
  }

  private fun Ctx.liftPattern(
    pattern: C.Pattern,
  ): L.Pattern {
    return when (pattern) {
      is C.Pattern.IntOf      -> L.Pattern.IntOf(pattern.value)
      is C.Pattern.CompoundOf -> {
        val elements = pattern.elements.map { (key, element) -> key to liftPattern(element) }
        L.Pattern.CompoundOf(elements)
      }
      is C.Pattern.CodeOf     -> unexpectedPattern(pattern)
      is C.Pattern.Var        -> {
        val type = liftType(pattern.type)
        bind(pattern.name, type)
        L.Pattern.Var(pattern.name, type)
      }
      is C.Pattern.Drop       -> {
        val type = NbtType.END // TODO
        L.Pattern.Drop(type)
      }
      is C.Pattern.Hole       -> unexpectedPattern(pattern)
    }
  }

  private fun freeVars(
    term: C.Term,
  ): LinkedHashMap<String, NbtType> {
    return when (term) {
      is C.Term.Tag         -> unexpectedTerm(term)
      is C.Term.TagOf       -> linkedMapOf()
      is C.Term.Type        -> freeVars(term.tag)
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
      is C.Term.Union       -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.Func        -> freeVars(term.result).also { result -> term.params.forEach { result -= boundVars(it.first) } }
      is C.Term.FuncOf      -> freeVars(term.result).also { result -> term.params.forEach { result -= boundVars(it) } }
      is C.Term.Apply       -> freeVars(term.func).also { func -> term.args.forEach { func += freeVars(it) } }
      is C.Term.Code        -> unexpectedTerm(term)
      is C.Term.CodeOf      -> unexpectedTerm(term)
      is C.Term.Splice      -> unexpectedTerm(term)
      is C.Term.Let         -> freeVars(term.init).also { it += freeVars(term.body); it -= boundVars(term.binder) }
      is C.Term.Var         -> linkedMapOf(term.name to liftType(term.type))
      is C.Term.Def         -> linkedMapOf()
      is C.Term.Meta        -> unexpectedTerm(term)
      is C.Term.Hole        -> unexpectedTerm(term)
    }
  }

  private fun boundVars(
    pattern: C.Pattern,
  ): Set<String> {
    return when (pattern) {
      is C.Pattern.IntOf      -> emptySet()
      is C.Pattern.CompoundOf -> pattern.elements.flatMapTo(hashSetOf()) { (_, element) -> boundVars(element) }
      is C.Pattern.CodeOf     -> unexpectedPattern(pattern)
      is C.Pattern.Var        -> setOf(pattern.name)
      is C.Pattern.Drop       -> emptySet()
      is C.Pattern.Hole       -> unexpectedPattern(pattern)
    }
  }

  private fun liftType(
    type: C.Term,
  ): NbtType {
    return when (type) {
      is C.Term.Tag         -> unexpectedTerm(type)
      is C.Term.TagOf       -> unexpectedTerm(type)
      is C.Term.Type        -> (type.tag as C.Term.TagOf).value
      is C.Term.Bool        -> NbtType.BYTE
      is C.Term.BoolOf      -> unexpectedTerm(type)
      is C.Term.If          -> unexpectedTerm(type)
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
      is C.Term.Union       -> type.elements.firstOrNull()?.let { liftType(it) } ?: NbtType.END
      is C.Term.Func        -> NbtType.COMPOUND
      is C.Term.FuncOf      -> unexpectedTerm(type)
      is C.Term.Apply       -> unexpectedTerm(type)
      is C.Term.Code        -> unexpectedTerm(type)
      is C.Term.CodeOf      -> unexpectedTerm(type)
      is C.Term.Splice      -> unexpectedTerm(type)
      is C.Term.Let         -> unexpectedTerm(type)
      is C.Term.Var         -> liftType(type.type) // ?
      is C.Term.Def         -> unexpectedTerm(type) // ?
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
    val next: Lvl get() = Lvl(_types.size)

    operator fun get(lvl: Lvl): Pair<String, NbtType> {
      return _types[lvl.value]
    }

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
    val dispatchedDefinitions: List<L.Definition.Function>,
  )

  companion object {
    private fun unexpectedModifier(modifier: Modifier): Nothing {
      error("unexpected modifier: ${modifier.id}")
    }

    private fun unexpectedTerm(term: C.Term): Nothing {
      error("unexpected term: ${prettyTerm(term)}")
    }

    private fun unexpectedPattern(pattern: C.Pattern): Nothing {
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
