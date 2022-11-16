package mcx.phase

import mcx.ast.Core as C

class Stage private constructor() {
  private fun stageResource(
    resource: C.Resource,
  ): C.Resource {
    return when (resource) {
      is C.Resource.JsonResource -> resource
      is C.Resource.Function     ->
        C.Resource
          .Function(resource.annotations, resource.name, resource.binder, resource.param, resource.result)
          .also {
            it.body = stageTerm(resource.body)
          }
      is C.Resource.Hole         -> unexpectedHole()
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
      is C.Term.If          -> C.Term.If(stageTerm(term.condition), stageTerm(term.thenClause), stageTerm(term.elseClause), term.type) // TODO: eval
      is C.Term.Let         -> C.Term.Let(term.binder, stageTerm(term.init), stageTerm(term.body), term.type) // TODO: eval
      is C.Term.Var         -> term
      is C.Term.Run         -> C.Term.Run(term.name, stageTerm(term.arg), term.type) // TODO: eval
      is C.Term.Is          -> C.Term.Is(stageTerm(term.scrutinee), term.scrutineer, term.type) // TODO: eval
      is C.Term.Command     -> term
      is C.Term.CodeOf      -> term
      is C.Term.Splice      ->
        when (val element = stageTerm(term.element)) {
          is C.Term.CodeOf -> element.element
          else             -> error("expected: code_of")
        }
      is C.Term.Hole        -> unexpectedHole()
    }
  }

  private fun unexpectedHole(): Nothing =
    error("unexpected: hole")

  companion object {
    operator fun invoke(
      config: Config,
      resource: C.Resource,
    ): C.Resource =
      Stage().stageResource(resource)
  }
}
