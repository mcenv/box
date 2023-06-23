package mcx.pass.backend

import mcx.ast.Modifier
import mcx.ast.Projection
import mcx.ast.Repr
import mcx.data.NbtType
import mcx.pass.Context
import mcx.pass.backend.Lift.Ctx.Companion.emptyCtx
import mcx.pass.prettyPattern
import mcx.pass.prettyTerm
import mcx.ast.Elaborated as E
import mcx.ast.Lifted as L

@Suppress("NAME_SHADOWING")
class Lift private constructor(
  private val context: Context,
  private val definition: E.Definition,
) {
  private val liftedDefinitions: MutableList<L.Definition> = mutableListOf()
  private val dispatchedProcs: MutableList<L.Definition.Function> = mutableListOf()
  private val dispatchedFuncs: MutableList<L.Definition.Function> = mutableListOf()
  private var freshFunctionId: Int = 0

  private fun lift(): Result {
    val modifiers = definition.modifiers.mapNotNull { liftModifier(it) }
    liftedDefinitions += when (definition) {
      is E.Definition.Def -> {
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
      Modifier.DIRECT  -> null // ?
      Modifier.CONST   -> error("Unexpected modifier: ${modifier.id}")
      Modifier.TEST    -> L.Modifier.TEST
      Modifier.ERROR   -> error("Unexpected modifier: ${modifier.id}")
    }
  }

  private fun Ctx.liftTerm(term: E.Term): L.Term {
    return when (term) {
      is E.Term.Tag        -> {
        unexpectedTerm(term)
      }

      is E.Term.TagOf      -> {
        UNIT
      }

      is E.Term.Type       -> {
        UNIT
      }

      is E.Term.Bool       -> {
        UNIT
      }

      is E.Term.BoolOf     -> {
        L.Term.I8Of(if (term.value) 1 else 0)
      }

      is E.Term.If         -> {
        val condition = liftTerm(term.condition)
        val thenBranch = liftTerm(term.thenBranch)
        val elseBranch = liftTerm(term.elseBranch)
        val thenFunction = createFreshFunction(thenBranch, 1)
        val elseFunction = createFreshFunction(elseBranch, null)
        val type = thenBranch.type
        L.Term.If(condition, thenFunction.name, elseFunction.name, type)
      }

      is E.Term.I8         -> {
        UNIT
      }

      is E.Term.I8Of       -> {
        L.Term.I8Of(term.value)
      }

      is E.Term.I16        -> {
        UNIT
      }

      is E.Term.I16Of      -> {
        L.Term.I16Of(term.value)
      }

      is E.Term.I32        -> {
        UNIT
      }

      is E.Term.I32Of      -> {
        L.Term.I32Of(term.value)
      }

      is E.Term.I64        -> {
        UNIT
      }

      is E.Term.I64Of      -> {
        L.Term.I64Of(term.value)
      }

      is E.Term.F32        -> {
        UNIT
      }

      is E.Term.F32Of      -> {
        L.Term.F32Of(term.value)
      }

      is E.Term.F64        -> {
        UNIT
      }

      is E.Term.F64Of      -> {
        L.Term.F64Of(term.value)
      }

      is E.Term.Str        -> {
        UNIT
      }

      is E.Term.StrOf      -> {
        L.Term.StrOf(term.value)
      }

      is E.Term.I8Array    -> {
        UNIT
      }

      is E.Term.I8ArrayOf  -> {
        val elements = term.elements.map { liftTerm(it) }
        L.Term.I8ArrayOf(elements)
      }

      is E.Term.I32Array   -> {
        UNIT
      }

      is E.Term.I32ArrayOf -> {
        val elements = term.elements.map { liftTerm(it) }
        L.Term.I32ArrayOf(elements)
      }

      is E.Term.I64Array   -> {
        UNIT
      }

      is E.Term.I64ArrayOf -> {
        val elements = term.elements.map { liftTerm(it) }
        L.Term.I64ArrayOf(elements)
      }

      is E.Term.Vec        -> {
        UNIT
      }

      is E.Term.VecOf      -> {
        val elements = term.elements.map { liftTerm(it) }
        L.Term.VecOf(elements)
      }

      is E.Term.Struct     -> {
        UNIT
      }

      is E.Term.StructOf   -> {
        val elements = term.elements.mapValuesTo(linkedMapOf()) { liftTerm(it.value) }
        L.Term.StructOf(elements)
      }

      is E.Term.Ref        -> {
        UNIT
      }

      is E.Term.RefOf      -> {
        val element = liftTerm(term.element)
        L.Term.RefOf(element)
      }

      is E.Term.Point      -> {
        UNIT
      }

      is E.Term.Union      -> {
        UNIT
      }

      is E.Term.Func       -> {
        UNIT
      }

      is E.Term.FuncOf     -> {
        // Generate ID before the subterms are lifted to ensure the top-level proc always gets ID 0.
        val id = freshFunctionId++

        with(emptyCtx()) {
          if (term.open) {
            val freeVars = freeVars(term)
            val capture = L.Pattern.StructOf(freeVars.mapValuesTo(linkedMapOf()) { (name, type) -> L.Pattern.Var(name, type) })
            val binders = (term.params zip (term.type as E.Term.Func).params).map { (pattern, type) ->
              liftPattern(pattern, type.second)
            }
            val result = liftTerm(term.result)
            val tag = context.freshFuncId()
            L.Definition.Function(emptyList(), definition.name.let { it.module / "${it.name}:$id" }, binders + capture, result, tag).also {
              liftedDefinitions += it
              dispatchedFuncs += it
            }
            val entries = freeVars.map { (name, type) -> L.Term.FuncOf.Entry(name, type) }
            L.Term.FuncOf(entries, tag)
          } else {
            val binders = (term.params zip (term.type as E.Term.Func).params).map { (pattern, type) ->
              liftPattern(pattern, type.second)
            }
            val result = liftTerm(term.result)
            val tag = context.freshProcId()
            val function = L.Definition.Function(emptyList(), definition.name.let { it.module / "${it.name}:$id" }, binders, result, tag).also {
              liftedDefinitions += it
              dispatchedProcs += it
            }
            L.Term.ProcOf(function)
          }
        }
      }

      is E.Term.Apply      -> {
        val func = liftTerm(term.func)
        val args = term.args.map { liftTerm(it) }
        val type = eraseType(term.type)
        L.Term.Apply(term.open, func, args, type)
      }

      is E.Term.Code       -> {
        unexpectedTerm(term)
      }

      is E.Term.CodeOf     -> {
        unexpectedTerm(term)
      }

      is E.Term.Splice     -> {
        unexpectedTerm(term)
      }

      is E.Term.Command    -> {
        val element = (term.element as E.Term.StrOf).value
        val type = eraseType(term.type)
        L.Term.Command(element, type)
      }

      is E.Term.Let        -> {
        val init = liftTerm(term.init)
        restoring {
          val binder = liftPattern(term.binder, term.init.type)
          val body = liftTerm(term.body)
          L.Term.Let(binder, init, body)
        }
      }

      is E.Term.Match      -> {
        TODO()
      }

      is E.Term.Proj       -> {
        val projections = mutableListOf<Projection>()
        tailrec fun go(target: E.Term): L.Term {
          return when (target) {
            is E.Term.Proj -> {
              projections += target.projection
              go(target.target)
            }
            is E.Term.Var  -> {
              val type = eraseType(term.type)
              L.Term.Proj(target.name, projections, type)
            }
            else           -> {
              error("Unexpected: ${prettyTerm(target)}")
            }
          }
        }
        go(term.target)
      }

      is E.Term.Var        -> {
        val type = eraseType(term.type)
        L.Term.Var(term.name, type)
      }

      is E.Term.Def        -> {
        val direct = Modifier.DIRECT in term.def.modifiers
        val type = eraseType(term.def.type)
        L.Term.Def(direct, term.def.name, type)
      }

      is E.Term.Meta       -> {
        unexpectedTerm(term)
      }

      is E.Term.Hole       -> {
        unexpectedTerm(term)
      }
    }
  }

  private fun Ctx.liftPattern(
    pattern: E.Pattern,
    type: E.Term,
  ): L.Pattern {
    return when (pattern) {
      is E.Pattern.I32Of    -> {
        L.Pattern.I32Of(pattern.value)
      }

      is E.Pattern.StructOf -> {
        val elements = pattern.elements.mapValuesTo(linkedMapOf()) { (_, element) ->
          liftPattern(element, type)
        }
        L.Pattern.StructOf(elements)
      }

      is E.Pattern.Var      -> {
        val type = eraseType(type)
        bind(pattern.name, type)
        L.Pattern.Var(pattern.name, type)
      }

      is E.Pattern.Drop     -> {
        val type = eraseType(type)
        L.Pattern.Drop(type)
      }

      is E.Pattern.Hole     -> {
        unexpectedPattern(pattern)
      }
    }
  }

  private fun freeVars(term: E.Term): LinkedHashMap<String, NbtType> {
    return when (term) {
      is E.Term.Tag        -> unexpectedTerm(term)
      is E.Term.TagOf      -> linkedMapOf()
      is E.Term.Type       -> freeVars(term.element)
      is E.Term.Bool       -> linkedMapOf()
      is E.Term.BoolOf     -> linkedMapOf()
      is E.Term.If         -> freeVars(term.condition).also { it += freeVars(term.thenBranch); it += freeVars(term.elseBranch) }
      is E.Term.I8         -> linkedMapOf()
      is E.Term.I8Of       -> linkedMapOf()
      is E.Term.I16        -> linkedMapOf()
      is E.Term.I16Of      -> linkedMapOf()
      is E.Term.I32        -> linkedMapOf()
      is E.Term.I32Of      -> linkedMapOf()
      is E.Term.I64        -> linkedMapOf()
      is E.Term.I64Of      -> linkedMapOf()
      is E.Term.F32        -> linkedMapOf()
      is E.Term.F32Of      -> linkedMapOf()
      is E.Term.F64        -> linkedMapOf()
      is E.Term.F64Of      -> linkedMapOf()
      is E.Term.Str        -> linkedMapOf()
      is E.Term.StrOf      -> linkedMapOf()
      is E.Term.I8Array    -> linkedMapOf()
      is E.Term.I8ArrayOf  -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is E.Term.I32Array   -> linkedMapOf()
      is E.Term.I32ArrayOf -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is E.Term.I64Array   -> linkedMapOf()
      is E.Term.I64ArrayOf -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is E.Term.Vec        -> freeVars(term.element)
      is E.Term.VecOf      -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is E.Term.Struct     -> term.elements.values.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is E.Term.StructOf   -> term.elements.values.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is E.Term.Ref        -> freeVars(term.element)
      is E.Term.RefOf      -> freeVars(term.element)
      is E.Term.Point      -> freeVars(term.element)
      is E.Term.Union      -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is E.Term.Func       -> freeVars(term.result).also { result -> term.params.forEach { result -= boundVars(it.first) } }
      is E.Term.FuncOf     -> freeVars(term.result).also { result -> term.params.forEach { result -= boundVars(it) } }
      is E.Term.Apply      -> freeVars(term.func).also { func -> term.args.forEach { func += freeVars(it) } }
      is E.Term.Code       -> unexpectedTerm(term)
      is E.Term.CodeOf     -> unexpectedTerm(term)
      is E.Term.Splice     -> unexpectedTerm(term)
      is E.Term.Command    -> linkedMapOf()
      is E.Term.Let        -> freeVars(term.init).also { it += freeVars(term.body); it -= boundVars(term.binder) }
      is E.Term.Match      -> term.branches.fold(freeVars(term.scrutinee)) { acc, (pattern, body) -> acc.also { it += freeVars(body); it -= boundVars(pattern) } }
      is E.Term.Proj       -> freeVars(term.target)
      is E.Term.Var        -> linkedMapOf(term.name to eraseType(term.type))
      is E.Term.Def        -> linkedMapOf()
      is E.Term.Meta       -> unexpectedTerm(term)
      is E.Term.Hole       -> unexpectedTerm(term)
    }
  }

  private fun boundVars(pattern: E.Pattern): Set<String> {
    return when (pattern) {
      is E.Pattern.I32Of    -> emptySet()
      is E.Pattern.StructOf -> pattern.elements.values.fold(hashSetOf()) { acc, element -> acc.also { it += boundVars(element) } }
      is E.Pattern.Var      -> setOf(pattern.name)
      is E.Pattern.Drop     -> emptySet()
      is E.Pattern.Hole     -> unexpectedPattern(pattern)
    }
  }

  private fun eraseType(type: E.Term): NbtType {
    return when (((type.type as E.Term.Type).element as E.Term.TagOf).repr) {
      Repr.End       -> NbtType.END
      Repr.Byte      -> NbtType.BYTE
      Repr.Short     -> NbtType.SHORT
      Repr.Int       -> NbtType.INT
      Repr.Long      -> NbtType.LONG
      Repr.Float     -> NbtType.FLOAT
      Repr.Double    -> NbtType.DOUBLE
      Repr.ByteArray -> NbtType.BYTE_ARRAY
      Repr.IntArray  -> NbtType.INT_ARRAY
      Repr.LongArray -> NbtType.LONG_ARRAY
      Repr.String    -> NbtType.STRING
      Repr.List      -> NbtType.LIST
      Repr.Compound  -> NbtType.COMPOUND
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
    ).also {
      liftedDefinitions += it
    }
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
    private val UNIT: L.Term = L.Term.I8Of(0)

    private fun unexpectedTerm(term: E.Term): Nothing {
      error("Unexpected term: ${prettyTerm(term)}")
    }

    private fun unexpectedPattern(pattern: E.Pattern): Nothing {
      error("Unexpected pattern: ${prettyPattern(pattern)}")
    }

    operator fun invoke(
      context: Context,
      definition: E.Definition,
    ): Result {
      return Lift(context, definition).lift()
    }
  }
}
