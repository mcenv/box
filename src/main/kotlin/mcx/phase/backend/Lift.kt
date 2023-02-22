package mcx.phase.backend

import mcx.ast.Modifier
import mcx.phase.Context
import mcx.phase.backend.Lift.Env.Companion.emptyEnv
import mcx.phase.prettyType
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
        val env = emptyEnv()
        val binder = L.Pattern.TupleOf(emptyList(), L.Type.Tuple(emptyList()))
        val body = env.liftTerm(requireNotNull(definition.body) { "non-static function '${definition.name}' without body" })
        liftedDefinitions += L.Definition.Function(modifiers, definition.name, binder, body, null)
      }
    }
    return Result(liftedDefinitions, dispatchedDefinitions)
  }

  private fun liftModifier(
    modifier: Modifier,
  ): L.Modifier? {
    return when (modifier) {
      Modifier.NO_DROP -> L.Modifier.NO_DROP
      Modifier.BUILTIN -> L.Modifier.BUILTIN
      Modifier.EXPORT  -> null
      Modifier.INLINE  -> error("unexpected: $modifier")
      Modifier.STATIC  -> error("unexpected: $modifier")
    }
  }

  private fun liftType(
    type: C.Type,
  ): L.Type {
    return when (type) {
      is C.Type.Bool      -> L.Type.Bool(type.value)
      is C.Type.Byte      -> L.Type.Byte(type.value)
      is C.Type.Short     -> L.Type.Short(type.value)
      is C.Type.Int       -> L.Type.Int(type.value)
      is C.Type.Long      -> L.Type.Long(type.value)
      is C.Type.Float     -> L.Type.Float(type.value)
      is C.Type.Double    -> L.Type.Double(type.value)
      is C.Type.String    -> L.Type.String(type.value)
      is C.Type.ByteArray -> L.Type.ByteArray
      is C.Type.IntArray  -> L.Type.IntArray
      is C.Type.LongArray -> L.Type.LongArray
      is C.Type.List      -> L.Type.List(liftType(type.element))
      is C.Type.Compound  -> L.Type.Compound(type.elements.mapValues { liftType(it.value) })
      is C.Type.Tuple     -> L.Type.Tuple(type.elements.map { liftType(it) })
      is C.Type.Func      -> L.Type.Func(liftType(type.param), liftType(type.result))
      is C.Type.Clos      -> L.Type.Clos(liftType(type.param), liftType(type.result))
      is C.Type.Union     -> L.Type.Union(type.elements.map { liftType(it) })
      is C.Type.Code      -> error("unexpected: ${prettyType(type)}")
      is C.Type.Var       -> error("unexpected: ${prettyType(type)}")
      is C.Type.Def       -> L.Type.Def(type.name, lazy { liftType(type.body) })
      is C.Type.Meta      -> error("unexpected: ${prettyType(type)}")
      is C.Type.Hole      -> error("unexpected: ${prettyType(type)}")
    }
  }

  private fun Env.liftTerm(
    term: C.Term,
  ): L.Term {
    val type = liftType(term.type)
    return when (term) {
      is C.Term.BoolOf      -> L.Term.BoolOf(term.value, type)
      is C.Term.ByteOf      -> L.Term.ByteOf(term.value, type)
      is C.Term.ShortOf     -> L.Term.ShortOf(term.value, type)
      is C.Term.IntOf       -> L.Term.IntOf(term.value, type)
      is C.Term.LongOf      -> L.Term.LongOf(term.value, type)
      is C.Term.FloatOf     -> L.Term.FloatOf(term.value, type)
      is C.Term.DoubleOf    -> L.Term.DoubleOf(term.value, type)
      is C.Term.StringOf    -> L.Term.StringOf(term.value, type)
      is C.Term.ByteArrayOf -> L.Term.ByteArrayOf(term.elements.map { liftTerm(it) }, type)
      is C.Term.IntArrayOf  -> L.Term.IntArrayOf(term.elements.map { liftTerm(it) }, type)
      is C.Term.LongArrayOf -> L.Term.LongArrayOf(term.elements.map { liftTerm(it) }, type)
      is C.Term.ListOf      -> L.Term.ListOf(term.elements.map { liftTerm(it) }, type)
      is C.Term.CompoundOf  -> L.Term.CompoundOf(term.elements.mapValues { liftTerm(it.value) }, type)
      is C.Term.TupleOf     -> L.Term.TupleOf(term.elements.map { liftTerm(it) }, type)
      is C.Term.FuncOf      -> {
        with(emptyEnv()) {
          val binder = liftPattern(term.binder)
          val body = liftTerm(term.body)
          val tag = context.freshId()
          val bodyFunction = L.Definition
            .Function(
              emptyList(),
              definition.name.module / "${definition.name.name}:${freshFunctionId++}",
              binder,
              body,
              tag,
            )
            .also { liftedDefinitions += it }
          dispatchedDefinitions += bodyFunction
          L.Term.FuncOf(tag, type)
        }
      }
      is C.Term.ClosOf      -> {
        // remap levels
        with(emptyEnv()) {
          val binder = liftPattern(term.binder)
          val binderSize = types.size
          val freeVars = freeVars(term)
          freeVars.forEach { (name, type) -> bind(name, type.second) }
          val capture = L.Pattern.CompoundOf(
            freeVars.entries.mapIndexed { index, (name, type) -> name to L.Pattern.Var(binderSize + index, type.second) },
            L.Type.Compound(freeVars.mapValues { it.value.second }),
          )
          val body = liftTerm(term.body)
          val tag = context.freshId()
          val bodyFunction = L.Definition
            .Function(
              emptyList(),
              definition.name.module / "${definition.name.name}:${freshFunctionId++}",
              L.Pattern.TupleOf(listOf(binder, capture), L.Type.Tuple(listOf(binder.type, capture.type))),
              body,
              tag,
            )
            .also { liftedDefinitions += it }
          dispatchedDefinitions += bodyFunction
          L.Term.ClosOf(tag, freeVars.entries.map { (name, type) -> Triple(name, type.first, type.second) }, type)
        }
      }
      is C.Term.Apply       -> {
        val operator = liftTerm(term.operator)
        val arg = liftTerm(term.arg)
        L.Term.Apply(operator, arg, type)
      }
      is C.Term.If          -> {
        val condition = liftTerm(term.condition)
        val thenFunction = createFreshFunction(liftTerm(term.thenClause), 1)
        val elseFunction = createFreshFunction(liftTerm(term.elseClause), null)
        L.Term.If(condition, thenFunction.name, elseFunction.name, type)
      }
      is C.Term.Let     -> {
        val init = liftTerm(term.init)
        val (binder, body) = restoring {
          val binder = liftPattern(term.binder)
          val body = liftTerm(term.body)
          binder to body
        }
        L.Term.Let(binder, init, body, type)
      }
      is C.Term.Var     -> L.Term.Var(this[term.name], type)
      is C.Term.Is      ->
        restoring {
          L.Term.Is(liftTerm(term.scrutinee), liftPattern(term.scrutineer), type)
        }
      is C.Term.Command -> L.Term.Command((term.element as C.Term.StringOf).value, type)
      is C.Term.CodeOf  -> error("unexpected: code_of")
      is C.Term.Splice  -> error("unexpected: splice")
      is C.Term.Hole    -> error("unexpected: hole")
    }
  }

  private fun Env.liftPattern(
    pattern: C.Pattern,
  ): L.Pattern {
    val type = liftType(pattern.type)
    return when (pattern) {
      is C.Pattern.IntOf      -> L.Pattern.IntOf(pattern.value, type)
      is C.Pattern.IntRangeOf -> L.Pattern.IntRangeOf(pattern.min, pattern.max, type)
      is C.Pattern.ListOf     -> L.Pattern.ListOf(pattern.elements.map { liftPattern(it) }, type)
      is C.Pattern.CompoundOf -> L.Pattern.CompoundOf(pattern.elements.map { (name, element) -> name to liftPattern(element) }, type)
      is C.Pattern.TupleOf    -> L.Pattern.TupleOf(pattern.elements.map { liftPattern(it) }, type)
      is C.Pattern.Var        -> {
        bind(pattern.name, type)
        L.Pattern.Var(types.lastIndex, type)
      }
      is C.Pattern.Drop       -> L.Pattern.Drop(type)
      is C.Pattern.Hole       -> error("unexpected: hole")
    }
  }

  private fun freeVars(
    term: C.Term,
  ): LinkedHashMap<String, Pair<Int, L.Type>> {
    return when (term) {
      is C.Term.BoolOf      -> linkedMapOf()
      is C.Term.ByteOf      -> linkedMapOf()
      is C.Term.ShortOf     -> linkedMapOf()
      is C.Term.IntOf       -> linkedMapOf()
      is C.Term.LongOf      -> linkedMapOf()
      is C.Term.FloatOf     -> linkedMapOf()
      is C.Term.DoubleOf    -> linkedMapOf()
      is C.Term.StringOf    -> linkedMapOf()
      is C.Term.ByteArrayOf -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.IntArrayOf  -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.LongArrayOf -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.ListOf      -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.CompoundOf  -> term.elements.values.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.TupleOf     -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.FuncOf      -> linkedMapOf()
      is C.Term.ClosOf      -> freeVars(term.body).also { it -= boundVars(term.binder) }
      is C.Term.Apply       -> freeVars(term.operator).also { it += freeVars(term.arg) }
      is C.Term.If          -> freeVars(term.condition).also { it += freeVars(term.thenClause); it += freeVars(term.elseClause) }
      is C.Term.Let         -> freeVars(term.init).also { it += freeVars(term.body); it -= boundVars(term.binder) }
      is C.Term.Var         -> linkedMapOf(term.name to (term.level to liftType(term.type)))
      is C.Term.Is          -> freeVars(term.scrutinee)
      is C.Term.Command     -> linkedMapOf()
      is C.Term.CodeOf      -> error("unexpected: code_of")
      is C.Term.Splice      -> error("unexpected: splice")
      is C.Term.Hole        -> error("unexpected: hole")
    }
  }

  private fun boundVars(
    pattern: C.Pattern,
  ): Set<String> {
    return when (pattern) {
      is C.Pattern.IntOf      -> emptySet()
      is C.Pattern.IntRangeOf -> emptySet()
      is C.Pattern.ListOf     -> pattern.elements.flatMapTo(hashSetOf()) { boundVars(it) }
      is C.Pattern.CompoundOf -> pattern.elements.values.flatMapTo(hashSetOf()) { boundVars(it) }
      is C.Pattern.TupleOf    -> pattern.elements.flatMapTo(hashSetOf()) { boundVars(it) }
      is C.Pattern.Var        -> setOf(pattern.name)
      is C.Pattern.Drop       -> emptySet()
      is C.Pattern.Hole       -> error("unexpected: hole")
    }
  }

  private fun Env.createFreshFunction(
    body: L.Term,
    restore: Int?,
  ): L.Definition.Function {
    val type = L.Type.Tuple(types.map { (_, type) -> type })
    val params = types.mapIndexed { level, (_, type) ->
      L.Pattern.Var(level, type)
    }
    return L.Definition
      .Function(
        listOf(L.Modifier.NO_DROP),
        definition.name.module / "${definition.name.name}:${freshFunctionId++}",
        L.Pattern.TupleOf(params, type),
        body,
        restore,
      )
      .also { liftedDefinitions += it }
  }

  private class Env private constructor() {
    private var savedSize: Int = 0
    private val _types: MutableList<Pair<String, L.Type>> = mutableListOf()
    val types: List<Pair<String, L.Type>> get() = _types

    operator fun get(
      name: String,
    ): Int =
      _types.indexOfLast { it.first == name }

    fun bind(
      name: String,
      type: L.Type,
    ) {
      _types += name to type
    }

    inline fun <R> restoring(
      action: () -> R,
    ): R {
      savedSize = _types.size
      val result = action()
      repeat(_types.size - savedSize) {
        _types.removeLast()
      }
      return result
    }

    companion object {
      fun emptyEnv(): Env =
        Env()
    }
  }

  data class Result(
    val liftedDefinitions: List<L.Definition>,
    val dispatchedDefinitions: List<L.Definition.Function>,
  )

  companion object {
    operator fun invoke(
      context: Context,
      definition: C.Definition,
    ): Result =
      Lift(context, definition).lift()
  }
}
