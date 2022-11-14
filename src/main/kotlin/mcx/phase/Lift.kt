package mcx.phase

import mcx.ast.Location
import mcx.ast.Core as C
import mcx.ast.Lifted as L

class Lift private constructor() {
  private val liftedResources: MutableList<L.Resource> = mutableListOf()

  private fun liftModule(
    module: C.Module,
  ): L.Module {
    val resources = module.resources.map {
      liftResource(it)
    }
    return L.Module(module.name, resources + liftedResources)
  }

  private fun liftResource(
    resource: C.Resource,
  ): L.Resource {
    val env = Env(resource.name)
    val annotations = resource.annotations.map {
      liftAnnotation(it)
    }
    return when (resource) {
      is C.Resource.JsonResource -> {
        val body = env.liftTerm(resource.body)
        L.Resource.JsonResource(annotations, resource.registry, resource.name, body)
      }
      is C.Resource.Functions    -> {
        val params = resource.params.map { (name, type) ->
          name to liftType(type)
        }
        val result = liftType(resource.result)
        val body = env.liftTerm(resource.body)
        L.Resource.Functions(annotations, resource.name, params, result, body)
      }
      is C.Resource.Hole         -> unexpectedHole()
    }
  }

  private fun liftAnnotation(
    annotation: C.Annotation,
  ): L.Annotation {
    return when (annotation) {
      is C.Annotation.Tick -> L.Annotation.Tick
      is C.Annotation.Load -> L.Annotation.Load
      is C.Annotation.Hole -> unexpectedHole()
    }
  }

  private fun liftType(
    type: C.Type,
  ): L.Type {
    return when (type) {
      is C.Type.End      -> L.Type.End
      is C.Type.Bool     -> L.Type.Bool
      is C.Type.Byte     -> L.Type.Byte
      is C.Type.Short    -> L.Type.Short
      is C.Type.Int      -> L.Type.Int
      is C.Type.Long     -> L.Type.Long
      is C.Type.Float    -> L.Type.Float
      is C.Type.Double   -> L.Type.Double
      is C.Type.String   -> L.Type.String
      is C.Type.List     -> L.Type.List(liftType(type.element))
      is C.Type.Compound -> L.Type.Compound(type.elements.mapValues { liftType(it.value) })
      is C.Type.Box      -> L.Type.Box(liftType(type.element))
      is C.Type.Tuple    -> L.Type.Tuple(type.elements.map { liftType(it) })
      is C.Type.Hole     -> unexpectedHole()
    }
  }

  private fun Env.liftTerm(
    term: C.Term,
  ): L.Term {
    return when (term) {
      is C.Term.BoolOf     -> L.Term.BoolOf(term.value, liftType(term.type))
      is C.Term.ByteOf     -> L.Term.ByteOf(term.value, liftType(term.type))
      is C.Term.ShortOf    -> L.Term.ShortOf(term.value, liftType(term.type))
      is C.Term.IntOf      -> L.Term.IntOf(term.value, liftType(term.type))
      is C.Term.LongOf     -> L.Term.LongOf(term.value, liftType(term.type))
      is C.Term.FloatOf    -> L.Term.FloatOf(term.value, liftType(term.type))
      is C.Term.DoubleOf   -> L.Term.DoubleOf(term.value, liftType(term.type))
      is C.Term.StringOf   -> L.Term.StringOf(term.value, liftType(term.type))
      is C.Term.ListOf     -> L.Term.ListOf(term.values.map { liftTerm(it) }, liftType(term.type))
      is C.Term.CompoundOf -> L.Term.CompoundOf(term.values.mapValues { liftTerm(it.value) }, liftType(term.type))
      is C.Term.BoxOf      -> L.Term.BoxOf(liftTerm(term.value), liftType(term.type))
      is C.Term.TupleOf    -> L.Term.TupleOf(term.values.map { liftTerm(it) }, liftType(term.type))
      is C.Term.If         -> {
        val condition = liftTerm(term.condition)
        val type = liftType(term.type)
        val thenFunctions = liftTerm(term.thenClause).let { thenClause ->
          createFreshFunctions(type, L.Term.Let(
            L.Pattern.Var("x", thenClause.type),
            thenClause,
            L.Term.Let(
              L.Pattern.Var("_", L.Type.End),
              L.Term.Command("scoreboard players set #0 mcx 1", L.Type.End),
              L.Term.Var("x", thenClause.type),
              thenClause.type,
            ),
            thenClause.type
          ))
        }
        val elseFunctions = createFreshFunctions(type, liftTerm(term.elseClause))
        L.Term.If(condition, thenFunctions.name, elseFunctions.name, type)
      }
      is C.Term.Let        -> L.Term.Let(liftPattern(term.binder), liftTerm(term.init), liftTerm(term.body), liftType(term.type))
      is C.Term.Var        -> L.Term.Var(term.name, liftType(term.type))
      is C.Term.Run        -> L.Term.Run(term.name, term.args.map { liftTerm(it) }, liftType(term.type))
      is C.Term.Command    -> L.Term.Command(term.value, liftType(term.type))
      is C.Term.Hole       -> unexpectedHole()
    }
  }

  private fun liftPattern(
    pattern: C.Pattern,
  ): L.Pattern {
    return when (pattern) {
      is C.Pattern.TupleOf -> L.Pattern.TupleOf(pattern.elements.map { liftPattern(it) }, liftType(pattern.type))
      is C.Pattern.Var     -> L.Pattern.Var(pattern.name, liftType(pattern.type))
      is C.Pattern.Hole    -> unexpectedHole()
    }
  }

  private fun unexpectedHole(): Nothing =
    error("unexpected: hole")

  private inner class Env(
    private val name: Location,
  ) {
    private var id: Int = 0

    fun createFreshFunctions(
      result: L.Type,
      body: L.Term,
    ): L.Resource.Functions =
      L.Resource
        .Functions(emptyList(), Location(name.parts.dropLast(1) + "${name.parts.last()}:${id++}"), emptyList(), result, body)
        .also { liftedResources += it }
  }

  companion object {
    operator fun invoke(
      config: Config,
      module: C.Module,
    ): L.Module =
      Lift().liftModule(module)
  }
}
