package mcx.phase.backend

import mcx.ast.Annotation
import mcx.ast.DefinitionLocation
import mcx.phase.Context
import mcx.phase.Normalize.TypeEnv
import mcx.phase.Normalize.evalType
import mcx.phase.Normalize.normalizeTerm
import mcx.phase.prettyType
import mcx.ast.Core as C

class Stage private constructor(
  private val dependencies: Map<DefinitionLocation, C.Definition>,
) {
  private val stagedDefinitions: MutableList<C.Definition> = mutableListOf()

  private fun stage(
    definition: C.Definition,
  ): List<C.Definition> {
    TypeEnv(dependencies, emptyList(), true).stageDefinition(definition)
    return stagedDefinitions
  }

  private fun TypeEnv.stageDefinition(
    definition: C.Definition,
  ) {
    when (definition) {
      is C.Definition.Resource -> definition
      is C.Definition.Function -> {
        if (
          definition.typeParams.isEmpty() &&
          Annotation.INLINE !in definition.annotations &&
          Annotation.STATIC !in definition.annotations
        ) {
          val binder = stagePattern(definition.binder)
          val result = evalType(definition.result)
          val body = stageTerm(definition.body)
          C.Definition
            .Function(definition.annotations, definition.name, emptyList(), binder, result)
            .also {
              it.body = body
            }
        } else {
          null
        }
      }
      is C.Definition.Type     -> null
    }?.also {
      stagedDefinitions += it
    }
  }

  private fun TypeEnv.stageTerm(
    term: C.Term,
  ): C.Term {
    val type = evalType(term.type)
    return when (term) {
      is C.Term.BoolOf      -> term
      is C.Term.ByteOf      -> term
      is C.Term.ShortOf     -> term
      is C.Term.IntOf       -> term
      is C.Term.LongOf      -> term
      is C.Term.FloatOf     -> term
      is C.Term.DoubleOf    -> term
      is C.Term.StringOf    -> term
      is C.Term.ByteArrayOf -> C.Term.ByteArrayOf(term.elements.map { stageTerm(it) }, type)
      is C.Term.IntArrayOf  -> C.Term.IntArrayOf(term.elements.map { stageTerm(it) }, type)
      is C.Term.LongArrayOf -> C.Term.LongArrayOf(term.elements.map { stageTerm(it) }, type)
      is C.Term.ListOf      -> C.Term.ListOf(term.elements.map { stageTerm(it) }, type)
      is C.Term.CompoundOf  -> C.Term.CompoundOf(term.elements.mapValues { stageTerm(it.value) }, type)
      is C.Term.RefOf       -> C.Term.RefOf(stageTerm(term.element), type)
      is C.Term.TupleOf     -> C.Term.TupleOf(term.elements.map { stageTerm(it) }, type)
      is C.Term.FunOf       -> C.Term.FunOf(stagePattern(term.binder), stageTerm(term.body), type)
      is C.Term.Apply       -> C.Term.Apply(stageTerm(term.operator), stageTerm(term.arg), type)
      is C.Term.If          -> C.Term.If(stageTerm(term.condition), stageTerm(term.thenClause), stageTerm(term.elseClause), type)
      is C.Term.Let         -> C.Term.Let(stagePattern(term.binder), stageTerm(term.init), stageTerm(term.body), type)
      is C.Term.Var         -> C.Term.Var(term.name, term.level, type)
      is C.Term.Run         -> {
        val definition = dependencies[term.name] as C.Definition.Function
        if (Annotation.INLINE in definition.annotations) {
          stageTerm(normalizeTerm(dependencies, term))
        } else if (term.typeArgs.isEmpty()) {
          val arg = stageTerm(term.arg)
          C.Term.Run(term.name, emptyList(), arg, type)
        } else {
          val typeArgs = term.typeArgs.map { evalType(it) }
          val mangledName = mangle(term.name, typeArgs)
          val arg = stageTerm(term.arg)
          val typeEnv = TypeEnv(dependencies, typeArgs, true)
          typeEnv.stageDefinition(
            C.Definition
              .Function(
                definition.annotations,
                mangledName,
                emptyList(),
                definition.binder,
                definition.result,
              )
              .also {
                it.body = definition.body
              }
          )
          C.Term.Run(mangledName, emptyList(), arg, type)
        }
      }
      is C.Term.Is          -> C.Term.Is(stageTerm(term.scrutinee), stagePattern(term.scrutineer), type)
      is C.Term.Command     -> C.Term.Command(term.value, type)
      is C.Term.CodeOf      -> C.Term.CodeOf(stageTerm(term.element), type)
      is C.Term.Splice      -> stageTerm(normalizeTerm(dependencies, term))
      is C.Term.Hole        -> C.Term.Hole(type)
    }
  }

  private fun TypeEnv.stagePattern(
    pattern: C.Pattern,
  ): C.Pattern {
    val type = evalType(pattern.type)
    return when (pattern) {
      is C.Pattern.IntOf      -> pattern
      is C.Pattern.IntRangeOf -> pattern
      is C.Pattern.ListOf     -> C.Pattern.ListOf(pattern.elements.map { stagePattern(it) }, pattern.annotations, type)
      is C.Pattern.CompoundOf -> C.Pattern.CompoundOf(pattern.elements.mapValues { stagePattern(it.value) }, pattern.annotations, type)
      is C.Pattern.TupleOf    -> C.Pattern.TupleOf(pattern.elements.map { stagePattern(it) }, pattern.annotations, type)
      is C.Pattern.Var        -> C.Pattern.Var(pattern.name, pattern.level, pattern.annotations, type)
      is C.Pattern.Drop       -> C.Pattern.Drop(pattern.annotations, type)
      is C.Pattern.Hole       -> C.Pattern.Hole(pattern.annotations, type)
    }
  }

  private fun mangle(
    location: DefinitionLocation,
    types: List<C.Type>,
  ): DefinitionLocation =
    location.module / "${location.name}:${types.joinToString(":") { prettyType(it) }}"

  companion object {
    operator fun invoke(
      context: Context,
      dependencies: Map<DefinitionLocation, C.Definition>,
      definition: C.Definition,
    ): List<C.Definition> =
      Stage(dependencies).stage(definition)
  }
}
