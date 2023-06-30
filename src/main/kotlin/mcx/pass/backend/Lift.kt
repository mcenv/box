package mcx.pass.backend

import mcx.ast.common.Modifier
import mcx.ast.common.Proj
import mcx.ast.common.Repr
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
      Modifier.DIRECT  -> null // ?
      Modifier.CONST   -> error("Unexpected modifier: ${modifier.id}")
      Modifier.TEST    -> L.Modifier.TEST
      Modifier.ERROR   -> error("Unexpected modifier: ${modifier.id}")
    }
  }

  private fun Ctx.liftTerm(term: C.Term): L.Term {
    return when (term) {
      is C.Term.Tag        -> {
        unexpectedTerm(term)
      }

      is C.Term.TagOf      -> {
        UNIT
      }

      is C.Term.Type       -> {
        UNIT
      }

      is C.Term.Bool       -> {
        UNIT
      }

      is C.Term.BoolOf     -> {
        L.Term.I8Of(if (term.value) 1 else 0)
      }

      is C.Term.If         -> {
        val condition = liftTerm(term.condition)
        val thenBranch = liftTerm(term.thenBranch)
        val elseBranch = liftTerm(term.elseBranch)
        val thenFunction = createFreshFunction(thenBranch, 1)
        val elseFunction = createFreshFunction(elseBranch, null)
        val type = thenBranch.repr
        L.Term.If(condition, thenFunction.name, elseFunction.name, type)
      }

      is C.Term.I8         -> {
        UNIT
      }

      is C.Term.I8Of       -> {
        L.Term.I8Of(term.value)
      }

      is C.Term.I16        -> {
        UNIT
      }

      is C.Term.I16Of      -> {
        L.Term.I16Of(term.value)
      }

      is C.Term.I32        -> {
        UNIT
      }

      is C.Term.I32Of      -> {
        L.Term.I32Of(term.value)
      }

      is C.Term.I64        -> {
        UNIT
      }

      is C.Term.I64Of      -> {
        L.Term.I64Of(term.value)
      }

      is C.Term.F32        -> {
        UNIT
      }

      is C.Term.F32Of      -> {
        L.Term.F32Of(term.value)
      }

      is C.Term.F64        -> {
        UNIT
      }

      is C.Term.F64Of      -> {
        L.Term.F64Of(term.value)
      }

      is C.Term.Wtf16      -> {
        UNIT
      }

      is C.Term.Wtf16Of    -> {
        L.Term.Wtf16Of(term.value)
      }

      is C.Term.I8Array    -> {
        UNIT
      }

      is C.Term.I8ArrayOf  -> {
        val elements = term.elements.map { liftTerm(it) }
        L.Term.I8ArrayOf(elements)
      }

      is C.Term.I32Array   -> {
        UNIT
      }

      is C.Term.I32ArrayOf -> {
        val elements = term.elements.map { liftTerm(it) }
        L.Term.I32ArrayOf(elements)
      }

      is C.Term.I64Array   -> {
        UNIT
      }

      is C.Term.I64ArrayOf -> {
        val elements = term.elements.map { liftTerm(it) }
        L.Term.I64ArrayOf(elements)
      }

      is C.Term.Vec        -> {
        UNIT
      }

      is C.Term.VecOf      -> {
        val elements = term.elements.map { liftTerm(it) }
        L.Term.VecOf(elements)
      }

      is C.Term.Struct     -> {
        UNIT
      }

      is C.Term.StructOf   -> {
        val elements = term.elements.mapValuesTo(linkedMapOf()) { liftTerm(it.value) }
        L.Term.StructOf(elements)
      }

      is C.Term.Point      -> {
        UNIT
      }

      is C.Term.Union      -> {
        UNIT
      }

      is C.Term.Func       -> {
        UNIT
      }

      is C.Term.FuncOf     -> {
        // Generate ID before the subterms are lifted to ensure the top-level proc always gets ID 0.
        val id = freshFunctionId++

        with(emptyCtx()) {
          if (term.open) {
            val freeVars = freeVars(term)
            val capture = L.Pattern.StructOf(freeVars.mapValuesTo(linkedMapOf()) { (name, type) -> L.Pattern.Var(name, type) })
            val binders = (term.params zip (term.type as C.Term.Func).params).map { (pattern, type) ->
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
            val binders = (term.params zip (term.type as C.Term.Func).params).map { (pattern, type) ->
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

      is C.Term.Apply      -> {
        val func = liftTerm(term.func)
        val args = term.args.map { liftTerm(it) }
        val repr = eraseToRepr(term.type)
        L.Term.Apply(term.open, func, args, repr)
      }

      is C.Term.Code       -> {
        unexpectedTerm(term)
      }

      is C.Term.CodeOf     -> {
        unexpectedTerm(term)
      }

      is C.Term.Splice     -> {
        unexpectedTerm(term)
      }

      is C.Term.Command    -> {
        val element = (term.element as C.Term.Wtf16Of).value
        val repr = eraseToRepr(term.type)
        L.Term.Command(element, repr)
      }

      is C.Term.Let        -> {
        val init = liftTerm(term.init)
        restoring {
          val binder = liftPattern(term.binder, term.init.type)
          val body = liftTerm(term.body)
          L.Term.Let(binder, init, body)
        }
      }

      is C.Term.Match      -> {
        TODO()
      }

      is C.Term.Project    -> {
        val projs = mutableListOf<Proj>()
        tailrec fun go(target: C.Term): L.Term {
          return when (target) {
            is C.Term.Project -> {
              projs += target.proj
              go(target.target)
            }
            is C.Term.Var     -> {
              val repr = eraseToRepr(term.type)
              L.Term.Proj(target.name, projs, repr)
            }
            else              -> {
              error("Unexpected: ${prettyTerm(target)}")
            }
          }
        }
        go(term.target)
      }

      is C.Term.Var        -> {
        val repr = eraseToRepr(term.type)
        L.Term.Var(term.name, repr)
      }

      is C.Term.Def        -> {
        val direct = Modifier.DIRECT in term.def.modifiers
        val repr = eraseToRepr(term.def.type)
        L.Term.Def(direct, term.def.name, repr)
      }

      is C.Term.Meta       -> {
        unexpectedTerm(term)
      }

      is C.Term.Hole       -> {
        unexpectedTerm(term)
      }
    }
  }

  @Suppress("NAME_SHADOWING")
  private fun Ctx.liftPattern(
    pattern: C.Pattern,
    type: C.Term,
  ): L.Pattern {
    return when (pattern) {
      is C.Pattern.I32Of    -> {
        L.Pattern.I32Of(pattern.value)
      }

      is C.Pattern.VecOf    -> {
        val type = type as C.Term.Vec
        val elementType = type.element
        val elements = pattern.elements.map { element ->
          liftPattern(element, elementType)
        }
        L.Pattern.VecOf(elements)
      }

      is C.Pattern.StructOf -> {
        val type = type as C.Term.Struct
        val elements = pattern.elements.mapValuesTo(linkedMapOf()) { (name, element) ->
          liftPattern(element, type.elements[name]!!)
        }
        L.Pattern.StructOf(elements)
      }

      is C.Pattern.Var      -> {
        val repr = eraseToRepr(type)
        bind(pattern.name, repr)
        L.Pattern.Var(pattern.name, repr)
      }

      is C.Pattern.Drop     -> {
        val repr = eraseToRepr(type)
        L.Pattern.Drop(repr)
      }

      is C.Pattern.Hole     -> {
        unexpectedPattern(pattern)
      }
    }
  }

  private fun freeVars(term: C.Term): LinkedHashMap<String, Repr> {
    return when (term) {
      is C.Term.Tag        -> unexpectedTerm(term)
      is C.Term.TagOf      -> linkedMapOf()
      is C.Term.Type       -> freeVars(term.element)
      is C.Term.Bool       -> linkedMapOf()
      is C.Term.BoolOf     -> linkedMapOf()
      is C.Term.If         -> freeVars(term.condition).also { it += freeVars(term.thenBranch); it += freeVars(term.elseBranch) }
      is C.Term.I8         -> linkedMapOf()
      is C.Term.I8Of       -> linkedMapOf()
      is C.Term.I16        -> linkedMapOf()
      is C.Term.I16Of      -> linkedMapOf()
      is C.Term.I32        -> linkedMapOf()
      is C.Term.I32Of      -> linkedMapOf()
      is C.Term.I64        -> linkedMapOf()
      is C.Term.I64Of      -> linkedMapOf()
      is C.Term.F32        -> linkedMapOf()
      is C.Term.F32Of      -> linkedMapOf()
      is C.Term.F64        -> linkedMapOf()
      is C.Term.F64Of      -> linkedMapOf()
      is C.Term.Wtf16      -> linkedMapOf()
      is C.Term.Wtf16Of    -> linkedMapOf()
      is C.Term.I8Array    -> linkedMapOf()
      is C.Term.I8ArrayOf  -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.I32Array   -> linkedMapOf()
      is C.Term.I32ArrayOf -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.I64Array   -> linkedMapOf()
      is C.Term.I64ArrayOf -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.Vec        -> freeVars(term.element)
      is C.Term.VecOf      -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.Struct     -> term.elements.values.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.StructOf   -> term.elements.values.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.Point      -> freeVars(term.element)
      is C.Term.Union      -> term.elements.fold(linkedMapOf()) { acc, element -> acc.also { it += freeVars(element) } }
      is C.Term.Func       -> freeVars(term.result).also { result -> term.params.forEach { result -= boundVars(it.first) } }
      is C.Term.FuncOf     -> freeVars(term.result).also { result -> term.params.forEach { result -= boundVars(it) } }
      is C.Term.Apply      -> freeVars(term.func).also { func -> term.args.forEach { func += freeVars(it) } }
      is C.Term.Code       -> unexpectedTerm(term)
      is C.Term.CodeOf     -> unexpectedTerm(term)
      is C.Term.Splice     -> unexpectedTerm(term)
      is C.Term.Command    -> linkedMapOf()
      is C.Term.Let        -> freeVars(term.init).also { it += freeVars(term.body); it -= boundVars(term.binder) }
      is C.Term.Match      -> term.branches.fold(freeVars(term.scrutinee)) { acc, (pattern, body) -> acc.also { it += freeVars(body); it -= boundVars(pattern) } }
      is C.Term.Project    -> freeVars(term.target)
      is C.Term.Var        -> linkedMapOf(term.name to eraseToRepr(term.type))
      is C.Term.Def        -> linkedMapOf()
      is C.Term.Meta       -> unexpectedTerm(term)
      is C.Term.Hole       -> unexpectedTerm(term)
    }
  }

  private fun boundVars(pattern: C.Pattern): Set<String> {
    return when (pattern) {
      is C.Pattern.I32Of    -> emptySet()
      is C.Pattern.VecOf    -> pattern.elements.fold(hashSetOf()) { acc, element -> acc.also { it += boundVars(element) } }
      is C.Pattern.StructOf -> pattern.elements.values.fold(hashSetOf()) { acc, element -> acc.also { it += boundVars(element) } }
      is C.Pattern.Var      -> setOf(pattern.name)
      is C.Pattern.Drop     -> emptySet()
      is C.Pattern.Hole     -> unexpectedPattern(pattern)
    }
  }

  private fun eraseToRepr(type: C.Term): Repr {
    return ((type.type as C.Term.Type).element as C.Term.TagOf).repr
  }

  private fun Ctx.createFreshFunction(
    body: L.Term,
    restore: Int?,
  ): L.Definition.Function {
    val params = reprs.map { (name, type) -> L.Pattern.Var(name, type) }
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
    private val _reprs: MutableList<Pair<String, Repr>> = mutableListOf()
    val reprs: List<Pair<String, Repr>> get() = _reprs

    fun bind(
      name: String,
      repr: Repr,
    ) {
      _reprs += name to repr
    }

    inline fun <R> restoring(action: () -> R): R {
      val restore = _reprs.size
      val result = action()
      repeat(_reprs.size - restore) {
        _reprs.removeLast()
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

    private fun unexpectedTerm(term: C.Term): Nothing {
      error("Unexpected term: ${prettyTerm(term)}")
    }

    private fun unexpectedPattern(pattern: C.Pattern): Nothing {
      error("Unexpected pattern: ${prettyPattern(pattern)}")
    }

    operator fun invoke(
      context: Context,
      definition: C.Definition,
    ): Result {
      return Lift(context, definition).lift()
    }
  }
}
