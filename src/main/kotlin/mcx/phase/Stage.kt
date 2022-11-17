package mcx.phase

import mcx.ast.Location
import mcx.phase.Stage.Env.Companion.emptyEnv
import kotlin.math.max
import mcx.ast.Core as C

class Stage private constructor(
  private val dependencies: Map<Location, C.Resource>,
) {
  private fun stageResource(
    resource: C.Resource,
  ): C.Resource? {
    return when (resource) {
      is C.Resource.JsonResource -> resource
      is C.Resource.Function     ->
        if (max(getMinStage(resource.param), getMinStage(resource.result)) == 0) {
          C.Resource
            .Function(resource.annotations, resource.name, resource.binder, resource.param, resource.result)
            .also {
              it.body = stageTerm(resource.body)
            }
        } else {
          null
        }
      is C.Resource.Hole         -> unexpectedHole()
    }
  }

  private fun getMinStage(
    type: C.Type,
  ): Int {
    return when (type) {
      is C.Type.End       -> 0
      is C.Type.Bool      -> 0
      is C.Type.Byte      -> 0
      is C.Type.Short     -> 0
      is C.Type.Int       -> 0
      is C.Type.Long      -> 0
      is C.Type.Float     -> 0
      is C.Type.Double    -> 0
      is C.Type.String    -> 0
      is C.Type.ByteArray -> 0
      is C.Type.IntArray  -> 0
      is C.Type.LongArray -> 0
      is C.Type.List      -> getMinStage(type.element)
      is C.Type.Compound  -> type.elements.values.maxOfOrNull { getMinStage(it) } ?: 0
      is C.Type.Ref       -> getMinStage(type.element)
      is C.Type.Tuple     -> type.elements.maxOfOrNull { getMinStage(it) } ?: 0
      is C.Type.Code      -> getMinStage(type.element) + 1
      is C.Type.Hole      -> unexpectedHole()
    }
  }

  private fun stageTerm(
    term: C.Term,
  ): C.Term {
    return when (term) {
      is C.Term.BoolOf      -> term
      is C.Term.ByteOf      -> term
      is C.Term.ShortOf     -> term
      is C.Term.IntOf       -> term
      is C.Term.LongOf      -> term
      is C.Term.FloatOf     -> term
      is C.Term.DoubleOf    -> term
      is C.Term.StringOf    -> term
      is C.Term.ByteArrayOf -> C.Term.ByteArrayOf(term.elements.map { stageTerm(it) }, term.type)
      is C.Term.IntArrayOf  -> C.Term.IntArrayOf(term.elements.map { stageTerm(it) }, term.type)
      is C.Term.LongArrayOf -> C.Term.LongArrayOf(term.elements.map { stageTerm(it) }, term.type)
      is C.Term.ListOf      -> C.Term.ListOf(term.elements.map { stageTerm(it) }, term.type)
      is C.Term.CompoundOf  -> C.Term.CompoundOf(term.elements.mapValues { stageTerm(it.value) }, term.type)
      is C.Term.RefOf       -> C.Term.RefOf(stageTerm(term.element), term.type)
      is C.Term.TupleOf     -> C.Term.TupleOf(term.elements.map { stageTerm(it) }, term.type)
      is C.Term.If          -> C.Term.If(stageTerm(term.condition), stageTerm(term.thenClause), stageTerm(term.elseClause), term.type)
      is C.Term.Let         -> C.Term.Let(term.binder, stageTerm(term.init), stageTerm(term.body), term.type)
      is C.Term.Var         -> term
      is C.Term.Run         -> C.Term.Run(term.name, stageTerm(term.arg), term.type)
      is C.Term.Is          -> C.Term.Is(stageTerm(term.scrutinee), term.scrutineer, term.type)
      is C.Term.Command     -> term
      is C.Term.CodeOf      -> term
      is C.Term.Splice      -> emptyEnv().evalTerm(term)
      is C.Term.Hole        -> unexpectedHole()
    }
  }

  // TODO: laziness
  private fun Env.evalTerm(
    term: C.Term,
  ): C.Term {
    return when (term) {
      is C.Term.BoolOf      -> term
      is C.Term.ByteOf      -> term
      is C.Term.ShortOf     -> term
      is C.Term.IntOf       -> term
      is C.Term.LongOf      -> term
      is C.Term.FloatOf     -> term
      is C.Term.DoubleOf    -> term
      is C.Term.StringOf    -> term
      is C.Term.ByteArrayOf -> C.Term.ByteArrayOf(term.elements.map { evalTerm(it) }, term.type)
      is C.Term.IntArrayOf  -> C.Term.IntArrayOf(term.elements.map { evalTerm(it) }, term.type)
      is C.Term.LongArrayOf -> C.Term.LongArrayOf(term.elements.map { evalTerm(it) }, term.type)
      is C.Term.ListOf      -> C.Term.ListOf(term.elements.map { evalTerm(it) }, term.type)
      is C.Term.CompoundOf  -> C.Term.CompoundOf(term.elements.mapValues { evalTerm(it.value) }, term.type)
      is C.Term.RefOf       -> C.Term.RefOf(evalTerm(term.element), term.type)
      is C.Term.TupleOf     -> C.Term.TupleOf(term.elements.map { evalTerm(it) }, term.type)
      is C.Term.If          ->
        when (val condition = evalTerm(term.condition)) {
          is C.Term.BoolOf -> if (condition.value) evalTerm(term.thenClause) else evalTerm(term.elseClause)
          else             -> C.Term.If(condition, evalTerm(term.thenClause), evalTerm(term.elseClause), term.type)
        }
      is C.Term.Let         ->
        restoring {
          bindTerm(evalTerm(term.init), term.binder)
          evalTerm(term.body)
        }
      is C.Term.Var         -> term
      is C.Term.Run         ->
        restoring {
          val resource = dependencies[term.name] as C.Resource.Function
          bind(evalTerm(term.arg))
          evalTerm(resource.body)
        }
      is C.Term.Is          ->
        restoring {
          C.Term.BoolOf(matchTerm(term.scrutinee, term.scrutineer), term.type)
        }
      is C.Term.Command     -> term
      is C.Term.CodeOf      -> C.Term.CodeOf(evalTerm(term.element), term.type)
      is C.Term.Splice      ->
        when (val element = evalTerm(term.element)) {
          is C.Term.CodeOf -> element.element
          else             -> C.Term.Splice(element, term.type)
        }
      is C.Term.Hole        -> term
    }
  }

  private fun Env.bindTerm(
    term: C.Term,
    binder: C.Pattern,
  ) {
    return when {
      term is C.Term.TupleOf &&
      binder is C.Pattern.TupleOf -> (term.elements zip binder.elements).forEach { (term, binder) -> bindTerm(term, binder) }

      binder is C.Pattern.Var     -> bind(term)

      else                        -> Unit
    }
  }

  private fun matchTerm(
    term: C.Term,
    pattern: C.Pattern,
  ): Boolean {
    return when {
      term is C.Term.IntOf &&
      pattern is C.Pattern.IntOf      -> term.value == pattern.value

      term is C.Term.IntOf &&
      pattern is C.Pattern.IntRangeOf -> term.value in pattern.min..pattern.max

      term is C.Term.TupleOf &&
      pattern is C.Pattern.TupleOf    -> (term.elements zip pattern.elements).all { (term, pattern) -> matchTerm(term, pattern) }

      pattern is C.Pattern.Var        -> true

      pattern is C.Pattern.Drop       -> true

      pattern is C.Pattern.Hole       -> unexpectedHole()

      else                            -> false
    }
  }

  private fun unexpectedHole(): Nothing =
    error("unexpected: hole")

  private class Env private constructor() {
    private val values: MutableList<C.Term> = mutableListOf()
    private var savedSize: Int = 0

    fun bind(
      value: C.Term,
    ) {
      values += value
    }

    inline fun <R> restoring(
      action: () -> R,
    ): R {
      savedSize = values.size
      val result = action()
      repeat(values.size - savedSize) {
        values.removeLast()
      }
      return result
    }

    companion object {
      fun emptyEnv(): Env =
        Env()
    }
  }

  companion object {
    operator fun invoke(
      config: Config,
      dependencies: Map<Location, C.Resource>,
      resource: C.Resource,
    ): C.Resource? =
      Stage(dependencies).stageResource(resource)
  }
}
