package mcx.phase.backend

import mcx.ast.Modifier
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
        val thenFunction = createFreshFunction(liftTerm(term.thenBranch), 1)
        val elseFunction = createFreshFunction(liftTerm(term.elseBranch), null)
        val type = liftType(term.type)
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
        // remap levels
        with(emptyCtx()) {
          val binders = term.params.map { liftPattern(it) }
          val binderSize = types.size
          val freeVars = freeVars(term)
          freeVars.forEach { (name, type) -> bind(name, type.second) }
          val capture = L.Pattern.CompoundOf(
            freeVars.entries
              .mapIndexed { index, (name, type) -> name to L.Pattern.Var(name, binderSize + index, type.second) }
              .toMap()
          )
          val result = liftTerm(term.result)
          val tag = context.freshId()
          val bodyFunction = L.Definition.Function(
            emptyList(),
            definition.name.module / "${definition.name.name}:${freshFunctionId++}",
            binders + capture,
            result,
            tag,
          ).also { liftedDefinitions += it }
          dispatchedDefinitions += bodyFunction
          L.Term.FuncOf(freeVars.entries.map { (name, type) -> Triple(name, type.first, type.second) }, tag)
        }
      }
      is C.Term.Apply       -> {
        val func = liftTerm(term.func)
        val args = term.args.map { liftTerm(it) }
        val type = liftType(term.type)
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
        val type = liftType(term.type)
        L.Term.Var(term.name, this[term.name], type)
      }
      is C.Term.Def         -> {
        val type = liftType(term.type)
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
        val elements = pattern.elements.mapValues { liftPattern(it.value) }
        L.Pattern.CompoundOf(elements)
      }
      is C.Pattern.CodeOf     -> unexpectedPattern(pattern)
      is C.Pattern.Var        -> {
        val type = liftType(pattern.type)
        bind(pattern.name, type)
        L.Pattern.Var(pattern.name, types.lastIndex, type)
      }
      is C.Pattern.Drop       -> {
        val type = liftType(pattern.type)
        L.Pattern.Drop(type)
      }
      is C.Pattern.Hole       -> unexpectedPattern(pattern)
    }
  }

  private fun freeVars(
    term: C.Term,
  ): LinkedHashMap<String, Pair<Int, NbtType>> {
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
      is C.Term.Var         -> linkedMapOf(term.name to (term.level to liftType(term.type)))
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
      is C.Pattern.CompoundOf -> pattern.elements.values.flatMapTo(hashSetOf()) { boundVars(it) }
      is C.Pattern.CodeOf     -> unexpectedPattern(pattern)
      is C.Pattern.Var        -> setOf(pattern.name)
      is C.Pattern.Drop       -> emptySet()
      is C.Pattern.Hole       -> unexpectedPattern(pattern)
    }
  }

  // TODO: quote the type and get its tag?
  private fun liftType(
    type: C.Value,
  ): NbtType {
    return when (type) {
      is C.Value.Tag         -> unexpectedType(type)
      is C.Value.TagOf       -> unexpectedType(type)
      is C.Value.Type        -> (type.tag.value as C.Value.TagOf).value
      is C.Value.Bool        -> NbtType.BYTE
      is C.Value.BoolOf      -> unexpectedType(type)
      is C.Value.If          -> unexpectedType(type)
      is C.Value.Is          -> unexpectedType(type)
      is C.Value.Byte        -> NbtType.BYTE
      is C.Value.ByteOf      -> unexpectedType(type)
      is C.Value.Short       -> NbtType.SHORT
      is C.Value.ShortOf     -> unexpectedType(type)
      is C.Value.Int         -> NbtType.INT
      is C.Value.IntOf       -> unexpectedType(type)
      is C.Value.Long        -> NbtType.LONG
      is C.Value.LongOf      -> unexpectedType(type)
      is C.Value.Float       -> NbtType.FLOAT
      is C.Value.FloatOf     -> unexpectedType(type)
      is C.Value.Double      -> NbtType.DOUBLE
      is C.Value.DoubleOf    -> unexpectedType(type)
      is C.Value.String      -> NbtType.STRING
      is C.Value.StringOf    -> unexpectedType(type)
      is C.Value.ByteArray   -> NbtType.BYTE_ARRAY
      is C.Value.ByteArrayOf -> unexpectedType(type)
      is C.Value.IntArray    -> NbtType.INT_ARRAY
      is C.Value.IntArrayOf  -> unexpectedType(type)
      is C.Value.LongArray   -> NbtType.LONG_ARRAY
      is C.Value.LongArrayOf -> unexpectedType(type)
      is C.Value.List        -> NbtType.LIST
      is C.Value.ListOf      -> unexpectedType(type)
      is C.Value.Compound    -> NbtType.COMPOUND
      is C.Value.CompoundOf  -> unexpectedType(type)
      is C.Value.Union       -> type.elements.firstOrNull()?.value?.let { liftType(it) } ?: NbtType.END
      is C.Value.Func        -> NbtType.COMPOUND
      is C.Value.FuncOf      -> unexpectedType(type)
      is C.Value.Apply       -> unexpectedType(type)
      is C.Value.Code        -> unexpectedType(type)
      is C.Value.CodeOf      -> unexpectedType(type)
      is C.Value.Splice      -> unexpectedType(type)
      is C.Value.Let         -> unexpectedType(type)
      is C.Value.Var         -> unexpectedType(type)
      is C.Value.Def         -> unexpectedType(type) // ?
      is C.Value.Meta        -> unexpectedType(type)
      is C.Value.Hole        -> unexpectedType(type)
    }
  }

  private fun Ctx.createFreshFunction(
    body: L.Term,
    restore: Int?,
  ): L.Definition.Function {
    val params = types.mapIndexed { index, (name, type) -> L.Pattern.Var(name, index, type) }
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

    operator fun get(name: String): Int {
      return _types.indexOfLast { it.first == name }
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
      error("unexpected modifier: ${modifier.token}")
    }

    private fun unexpectedTerm(term: C.Term): Nothing {
      error("unexpected term: ${prettyTerm(term)}")
    }

    private fun unexpectedPattern(pattern: C.Pattern): Nothing {
      error("unexpected pattern: ${prettyPattern(pattern)}")
    }

    private fun unexpectedType(value: C.Value): Nothing {
      error("unexpected type: $value")
    }

    operator fun invoke(
      context: Context,
      definition: C.Definition,
    ): Result {
      return Lift(context, definition).lift()
    }
  }
}
