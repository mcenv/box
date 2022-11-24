package mcx.phase

import mcx.ast.Core as C
import mcx.ast.Lifted as L

class Lift private constructor(
  private val context: Context,
  private val definition: C.Definition,
) {
  private val liftedDefinitions: MutableList<L.Definition> = mutableListOf()
  private val types: MutableList<L.Type> = mutableListOf()
  private var savedSize: Int = 0
  private var freshFunctionId: Int = 0

  private fun lift(): List<L.Definition> /* TODO: add metadata to lifted definitions */ {
    val annotations = definition.annotations.map { liftAnnotation(it) }
    return liftedDefinitions + when (definition) {
      is C.Definition.Resource -> {
        val body = liftTerm(definition.body)
        L.Definition.Resource(annotations, definition.registry, definition.name, body)
      }
      is C.Definition.Function -> {
        val binder = liftPattern(definition.binder)
        if (L.Annotation.Builtin in annotations) {
          L.Definition.Builtin(annotations, definition.name)
        } else {
          val body = liftTerm(definition.body)
          L.Definition.Function(annotations, definition.name, binder, body, null)
        }
      }
      is C.Definition.Hole     -> throw UnexpectedHole
    }
  }

  private fun liftAnnotation(
    annotation: C.Annotation,
  ): L.Annotation {
    return when (annotation) {
      is C.Annotation.Export  -> error("unexpected: export")
      is C.Annotation.Tick    -> L.Annotation.Tick
      is C.Annotation.Load    -> L.Annotation.Load
      is C.Annotation.NoDrop  -> L.Annotation.NoDrop
      is C.Annotation.Inline  -> L.Annotation.Inline
      is C.Annotation.Builtin -> L.Annotation.Builtin
      is C.Annotation.Hole    -> throw UnexpectedHole
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
      is C.Type.Ref       -> L.Type.Ref(liftType(type.element))
      is C.Type.Tuple     -> L.Type.Tuple(type.elements.map { liftType(it) })
      is C.Type.Fun       -> L.Type.Fun(liftType(type.param), liftType(type.result))
      is C.Type.Union     -> L.Type.Union(type.elements.map { liftType(it) })
      is C.Type.Code      -> error("unexpected: ${prettyType(type)}")
      is C.Type.Var       -> error("unexpected: ${prettyType(type)}")
      is C.Type.Hole      -> throw UnexpectedHole
    }
  }

  private fun liftTerm(
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
      is C.Term.RefOf       -> L.Term.RefOf(liftTerm(term.element), type)
      is C.Term.TupleOf     -> L.Term.TupleOf(term.elements.map { liftTerm(it) }, type)
      is C.Term.FunOf   ->
        restoring {
          liftPattern(term.binder)
          val body = liftTerm(term.body)
          val id = context.liftedFunctions.size
          val bodyFunction = createFreshFunction(body, id) // TODO: capture and drop
          context.liftFunction(bodyFunction)
          L.Term.FunOf(id, type)
        }
      is C.Term.Apply   -> {
        val operator = liftTerm(term.operator)
        val arg = liftTerm(term.arg)
        L.Term.Run(Context.DISPATCH, L.Term.TupleOf(listOf(arg, operator), L.Type.Tuple(listOf(arg.type, operator.type))), type)
      }
      is C.Term.If      -> {
        val condition = liftTerm(term.condition)
        val thenFunction = createFreshFunction(liftTerm(term.thenClause), 1)
        val elseFunction = createFreshFunction(liftTerm(term.elseClause))
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
      is C.Term.Var     -> L.Term.Var(term.level, type)
      is C.Term.Run     -> L.Term.Run(term.name, liftTerm(term.arg), type)
      is C.Term.Is      ->
        restoring {
          L.Term.Is(liftTerm(term.scrutinee), liftPattern(term.scrutineer), type)
        }
      is C.Term.Command -> L.Term.Command(term.value, type)
      is C.Term.CodeOf  -> error("unexpected: code_of")
      is C.Term.Splice  -> error("unexpected: splice")
      is C.Term.Hole    -> throw UnexpectedHole
    }
  }

  private fun liftPattern(
    pattern: C.Pattern,
  ): L.Pattern {
    val annotations = pattern.annotations.map { liftAnnotation(it) }
    val type = liftType(pattern.type)
    return when (pattern) {
      is C.Pattern.IntOf      -> L.Pattern.IntOf(pattern.value, annotations, type)
      is C.Pattern.IntRangeOf -> L.Pattern.IntRangeOf(pattern.min, pattern.max, annotations, type)
      is C.Pattern.TupleOf    -> L.Pattern.TupleOf(pattern.elements.map { liftPattern(it) }, annotations, type)
      is C.Pattern.Var        -> {
        types += type
        L.Pattern.Var(pattern.level, annotations, type)
      }
      is C.Pattern.Drop       -> L.Pattern.Drop(annotations, type)
      is C.Pattern.Hole       -> throw UnexpectedHole
    }
  }

  private fun createFreshFunction(
    body: L.Term,
    restore: Int? = null,
  ): L.Definition.Function {
    val type = L.Type.Tuple(types.toList())
    val params = types.mapIndexed { level, entry ->
      L.Pattern.Var(level, emptyList(), entry)
    }
    return L.Definition
      .Function(
        emptyList(),
        definition.name.module / "${definition.name.name}:${freshFunctionId++}",
        L.Pattern.TupleOf(params, listOf(L.Annotation.NoDrop), type),
        body,
        restore,
      )
      .also { liftedDefinitions += it }
  }

  private inline fun <R> restoring(
    action: () -> R,
  ): R {
    savedSize = types.size
    val result = action()
    repeat(types.size - savedSize) {
      types.removeLast()
    }
    return result
  }

  companion object {
    operator fun invoke(
      context: Context,
      definition: C.Definition,
    ): List<L.Definition> =
      Lift(context, definition).lift()
  }
}
