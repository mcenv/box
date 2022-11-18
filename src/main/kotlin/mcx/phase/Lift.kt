package mcx.phase

import mcx.ast.Lifted
import mcx.ast.Location
import mcx.ast.Core as C
import mcx.ast.Lifted as L

class Lift private constructor(
  private val resource: C.Resource,
) {
  private val liftedResources: MutableList<L.Resource> = mutableListOf()
  private var freshFunctionId: Int = 0

  private fun lift(): List<L.Resource> {
    val annotations = resource.annotations.map { liftAnnotation(it) }
    return liftedResources + when (resource) {
      is C.Resource.JsonResource -> {
        val body = liftTerm(resource.body)
        L.Resource.JsonResource(annotations, resource.registry, resource.name, body)
      }
      is C.Resource.Function     -> {
        val binder = liftPattern(resource.binder)
        val param = liftType(resource.param)
        val result = liftType(resource.result)
        if (L.Annotation.Builtin in annotations) {
          L.Resource.Builtin(annotations, resource.name)
        } else {
          val body = liftTerm(resource.body)
          L.Resource.Function(annotations, resource.name, binder, param, result, body)
        }
      }
      is C.Resource.Hole         -> unexpectedHole()
    }
  }

  private fun liftAnnotation(
    annotation: C.Annotation,
  ): L.Annotation {
    return when (annotation) {
      is C.Annotation.Tick    -> L.Annotation.Tick
      is C.Annotation.Load    -> L.Annotation.Load
      is C.Annotation.NoDrop  -> L.Annotation.NoDrop
      is C.Annotation.Inline  -> L.Annotation.Inline
      is C.Annotation.Builtin -> L.Annotation.Builtin
      is C.Annotation.Hole    -> unexpectedHole()
    }
  }

  private fun liftType(
    type: C.Type,
  ): L.Type {
    return when (type) {
      is C.Type.End       -> L.Type.End
      is C.Type.Bool      -> L.Type.Bool
      is C.Type.Byte      -> L.Type.Byte
      is C.Type.Short     -> L.Type.Short
      is C.Type.Int       -> L.Type.Int
      is C.Type.Long      -> L.Type.Long
      is C.Type.Float     -> L.Type.Float
      is C.Type.Double    -> L.Type.Double
      is C.Type.String    -> L.Type.String
      is C.Type.ByteArray -> L.Type.ByteArray
      is C.Type.IntArray  -> L.Type.IntArray
      is C.Type.LongArray -> L.Type.LongArray
      is C.Type.List      -> L.Type.List(liftType(type.element))
      is C.Type.Compound  -> L.Type.Compound(type.elements.mapValues { liftType(it.value) })
      is C.Type.Ref       -> L.Type.Ref(liftType(type.element))
      is C.Type.Tuple     -> L.Type.Tuple(type.elements.map { liftType(it) })
      is C.Type.Code      -> error("unexpected: ${prettyType(type)}")
      is C.Type.Type      -> error("unexpected: ${prettyType(type)}")
      is C.Type.Hole      -> unexpectedHole()
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
      is C.Term.If      -> {
        val condition = liftTerm(term.condition)
        val thenFunction = liftTerm(term.thenClause).let { thenClause ->
          createFreshFunction(
            L.Term.Let(
              L.Pattern.Drop(listOf(L.Annotation.NoDrop), thenClause.type),
              thenClause,
              L.Term.Command("scoreboard players set #0 mcx 1", thenClause.type),
              thenClause.type,
            )
          )
        }
        val elseFunction = createFreshFunction(liftTerm(term.elseClause))
        L.Term.If(condition, thenFunction.name, elseFunction.name, type)
      }
      is C.Term.Let     -> L.Term.Let(liftPattern(term.binder), liftTerm(term.init), liftTerm(term.body), type)
      is C.Term.Var     -> L.Term.Var(term.level, type)
      is C.Term.Run     -> L.Term.Run(term.name, liftTerm(term.arg), type)
      is C.Term.Is      -> L.Term.Is(liftTerm(term.scrutinee), liftPattern(term.scrutineer), type)
      is C.Term.Command -> L.Term.Command(term.value, type)
      is C.Term.CodeOf  -> error("unexpected: code_of")
      is C.Term.Splice  -> error("unexpected: splice")
      is C.Term.TypeOf  -> error("unexpected: type_of")
      is C.Term.Hole    -> unexpectedHole()
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
      is C.Pattern.Var        -> L.Pattern.Var(pattern.level, annotations, type)
      is C.Pattern.Drop       -> L.Pattern.Drop(annotations, type)
      is C.Pattern.Hole       -> unexpectedHole()
    }
  }

  private fun createFreshFunction(
    body: L.Term,
  ): Lifted.Resource.Function {
    val type = L.Type.Tuple(emptyList())
    return L.Resource
      .Function(
        emptyList(),
        Location(resource.name.parts.dropLast(1) + "${resource.name.parts.last()}:${freshFunctionId++}"),
        L.Pattern.TupleOf(emptyList(), emptyList(), type),
        type,
        type,
        body,
      )
      .also { liftedResources += it }
  }

  private fun unexpectedHole(): Nothing =
    error("unexpected: hole")

  companion object {
    operator fun invoke(
      config: Config,
      resource: C.Resource,
    ): List<L.Resource> =
      Lift(resource).lift()
  }
}
