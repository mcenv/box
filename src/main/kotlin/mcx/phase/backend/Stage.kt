package mcx.phase.backend

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mcx.ast.DefinitionLocation
import mcx.ast.Modifier
import mcx.ast.Value
import mcx.phase.BUILTINS
import mcx.phase.Context
import mcx.phase.Normalize
import mcx.phase.Normalize.evalType
import mcx.phase.prettyType
import mcx.ast.Core as C

class Stage private constructor(
  private val dependencies: Map<DefinitionLocation, C.Definition>,
) {
  private val stagedDefinitions: MutableList<C.Definition> = mutableListOf()

  private fun stage(
    definition: C.Definition,
  ): List<C.Definition> {
    Env(dependencies, emptyList(), true, persistentListOf(), false).stageDefinition(definition)
    return stagedDefinitions
  }

  private fun Env.stageDefinition(
    definition: C.Definition,
  ) {
    when (definition) {
      is C.Definition.Function -> {
        if (
          definition.typeParams.isEmpty() &&
          Modifier.INLINE !in definition.modifiers &&
          Modifier.STATIC !in definition.modifiers
        ) {
          val binder = stagePattern(definition.binder)
          val result = evalType(definition.result)
          val body = stageTerm(requireNotNull(definition.body) { "non-static function '${definition.name}' without body" })
          C.Definition.Function(definition.modifiers, definition.name, emptyList(), binder, result, body)
        } else {
          null
        }
      }
      is C.Definition.Type     -> null
      is C.Definition.Class    -> null
      is C.Definition.Test     -> {
        if (Modifier.STATIC !in definition.modifiers) {
          val body = stageTerm(definition.body)
          C.Definition.Test(definition.modifiers, definition.name, body)
        } else {
          null
        }
      }
    }?.also {
      stagedDefinitions += it
    }
  }

  private fun Env.stageTerm(
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
      is C.Term.TupleOf     -> C.Term.TupleOf(term.elements.map { stageTerm(it) }, type)
      is C.Term.FuncOf      -> C.Term.FuncOf(stagePattern(term.binder), stageTerm(term.body), type)
      is C.Term.ClosOf      -> C.Term.ClosOf(stagePattern(term.binder), stageTerm(term.body), type)
      is C.Term.Apply       -> C.Term.Apply(stageTerm(term.operator), stageTerm(term.arg), type)
      is C.Term.If          -> C.Term.If(stageTerm(term.condition), stageTerm(term.thenClause), stageTerm(term.elseClause), type)
      is C.Term.Let         -> C.Term.Let(stagePattern(term.binder), stageTerm(term.init), stageTerm(term.body), type)
      is C.Term.Var         -> C.Term.Var(term.name, term.level, type)
      is C.Term.Run     -> {
        val definition = dependencies[term.name] as C.Definition.Function
        val typeArgs = term.typeArgs.map { evalType(it) }
        if (Modifier.INLINE in definition.modifiers) {
          stageTerm(normalizeTerm(dependencies, typeArgs, term))
        } else if (typeArgs.isEmpty()) {
          val arg = stageTerm(term.arg)
          C.Term.Run(term.name, emptyList(), arg, type)
        } else {
          val mangledName = mangle(term.name, typeArgs)
          val arg = stageTerm(term.arg)
          Env(dependencies, typeArgs, unfold, persistentListOf(), static).stageDefinition(
            C.Definition
              .Function(
                definition.modifiers,
                mangledName,
                emptyList(),
                definition.binder,
                definition.result,
                definition.body,
              )
          )
          C.Term.Run(mangledName, emptyList(), arg, type)
        }
      }
      is C.Term.Is      -> C.Term.Is(stageTerm(term.scrutinee), stagePattern(term.scrutineer), type)
      is C.Term.Command -> C.Term.Command(term.element, type)
      is C.Term.CodeOf  -> C.Term.CodeOf(stageTerm(term.element), type)
      is C.Term.Splice  -> stageTerm(normalizeTerm(dependencies, types, term))
      is C.Term.Hole    -> C.Term.Hole(type)
    }
  }

  private fun Env.stagePattern(
    pattern: C.Pattern,
  ): C.Pattern {
    val type = evalType(pattern.type)
    return when (pattern) {
      is C.Pattern.IntOf      -> pattern
      is C.Pattern.IntRangeOf -> pattern
      is C.Pattern.ListOf     -> C.Pattern.ListOf(pattern.elements.map { stagePattern(it) }, type)
      is C.Pattern.CompoundOf -> C.Pattern.CompoundOf(pattern.elements.mapValues { stagePattern(it.value) }, type)
      is C.Pattern.TupleOf    -> C.Pattern.TupleOf(pattern.elements.map { stagePattern(it) }, type)
      is C.Pattern.Var        -> C.Pattern.Var(pattern.name, pattern.level, type)
      is C.Pattern.Drop       -> C.Pattern.Drop(type)
      is C.Pattern.Hole       -> C.Pattern.Hole(type)
    }
  }

  private fun normalizeTerm(
    definitions: Map<DefinitionLocation, C.Definition>,
    types: List<C.Type>,
    term: C.Term,
  ): C.Term =
    with(Env(definitions, types, true, persistentListOf(), false)) {
      quoteValue(evalTerm(term), term.type)
    }

  private fun Env.evalTerm(
    term: C.Term,
  ): Value {
    return when (term) {
      is C.Term.BoolOf      -> Value.BoolOf(term.value)
      is C.Term.ByteOf      -> Value.ByteOf(term.value)
      is C.Term.ShortOf     -> Value.ShortOf(term.value)
      is C.Term.IntOf       -> Value.IntOf(term.value)
      is C.Term.LongOf      -> Value.LongOf(term.value)
      is C.Term.FloatOf     -> Value.FloatOf(term.value)
      is C.Term.DoubleOf    -> Value.DoubleOf(term.value)
      is C.Term.StringOf    -> Value.StringOf(term.value)
      is C.Term.ByteArrayOf -> Value.ByteArrayOf(term.elements.map { lazy { evalTerm(it) } })
      is C.Term.IntArrayOf  -> Value.IntArrayOf(term.elements.map { lazy { evalTerm(it) } })
      is C.Term.LongArrayOf -> Value.LongArrayOf(term.elements.map { lazy { evalTerm(it) } })
      is C.Term.ListOf      -> Value.ListOf(term.elements.map { lazy { evalTerm(it) } })
      is C.Term.CompoundOf  -> Value.CompoundOf(term.elements.mapValues { lazy { evalTerm(it.value) } })
      is C.Term.TupleOf     -> Value.TupleOf(term.elements.map { lazy { evalTerm(it) } })
      is C.Term.FuncOf      -> Value.FuncOf(term.binder, term.body)
      is C.Term.ClosOf      -> Value.ClosOf(term.binder, term.body)
      is C.Term.Apply       -> {
        val operator = evalTerm(term.operator)
        if (static && operator is Value.ClosOf) {
          bind(bindValue(evalTerm(term.arg), operator.binder)).evalTerm(operator.body)
        } else {
          Value.Apply(operator, lazy { evalTerm(term.arg) }, term.type)
        }
      }
      is C.Term.If          -> {
        val condition = evalTerm(term.condition)
        if (static && condition is Value.BoolOf) {
          if (condition.value) evalTerm(term.thenClause) else evalTerm(term.elseClause)
        } else {
          Value.If(condition, lazy { evalTerm(term.thenClause) }, lazy { evalTerm(term.elseClause) })
        }
      }
      is C.Term.Let         -> {
        if (static) {
          bind(bindValue(evalTerm(term.init), term.binder)).evalTerm(term.body)
        } else {
          Value.Let(evalPattern(term.binder), lazy { evalTerm(term.init) }, lazy { evalTerm(term.body) }, evalType(term.body.type))
        }
      }
      is C.Term.Var         -> values.getOrNull(term.level)?.value ?: Value.Var(term.name, term.level)
      is C.Term.Run     -> {
        val arg = evalTerm(term.arg)
        val typeArgs = term.typeArgs.map { evalType(it) }
        val definition = requireNotNull(definitions[term.name] as? C.Definition.Function) { "definition not found: '${term.name}'" }
        if (static) {
          if (Modifier.BUILTIN in definition.modifiers) {
            val builtin = requireNotNull(BUILTINS[definition.name]) { "builtin not found: '${definition.name}'" }
            builtin.eval(arg, typeArgs) ?: Value.Run(term.name, typeArgs, arg)
          } else {
            Env(definitions, typeArgs, true, persistentListOf(), true)
              .bind(bindValue(arg, definition.binder))
              .evalTerm(requireNotNull(definition.body) { "non-static function '${term.name}' without body" })
          }
        } else {
          Value.Run(term.name, typeArgs, arg)
        }
      }
      is C.Term.Is      -> {
        val scrutinee = evalTerm(term.scrutinee)
        val matched = matchValue(scrutinee, term.scrutineer)
        if (static && matched != null) {
          Value.BoolOf(matched)
        } else {
          Value.Is(scrutinee, term.scrutineer, term.scrutinee.type)
        }
      }
      is C.Term.Command -> Value.CodeOf(lazyOf(Value.Command(lazy { evalTerm(term.element) })))
      is C.Term.CodeOf  -> Value.CodeOf(lazy { withStatic(false).evalTerm(term.element) })
      is C.Term.Splice  -> {
        when (val element = withStatic(true).evalTerm(term.element)) {
          is Value.CodeOf -> element.element.value
          else            -> Value.Splice(element, term.element.type)
        }
      }
      is C.Term.Hole    -> Value.Hole(term.type)
    }
  }

  private fun Env.evalPattern(
    pattern: C.Pattern,
  ): C.Pattern {
    val type = evalType(pattern.type)
    return when (pattern) {
      is C.Pattern.IntOf      -> pattern
      is C.Pattern.IntRangeOf -> pattern
      is C.Pattern.ListOf     -> C.Pattern.ListOf(pattern.elements.map { evalPattern(it) }, type)
      is C.Pattern.CompoundOf -> C.Pattern.CompoundOf(pattern.elements.mapValues { evalPattern(it.value) }, type)
      is C.Pattern.TupleOf    -> C.Pattern.TupleOf(pattern.elements.map { evalPattern(it) }, type)
      is C.Pattern.Var        -> C.Pattern.Var(pattern.name, pattern.level, type)
      is C.Pattern.Drop       -> C.Pattern.Drop(type)
      is C.Pattern.Hole       -> C.Pattern.Hole(type)
    }
  }

  private fun bindValue(
    value: Value,
    binder: C.Pattern,
  ): List<Value> {
    return when {
      value is Value.ListOf &&
      binder is C.Pattern.ListOf &&
      value.elements.size == binder.elements.size -> {
        (value.elements zip binder.elements).fold(mutableListOf()) { acc, (value, binder) ->
          acc.also { it += bindValue(value.value, binder) }
        }
      }

      value is Value.CompoundOf &&
      binder is C.Pattern.CompoundOf              ->
        value.elements.entries.fold(mutableListOf()) { acc, (name, value) ->
          acc.also { it += bindValue(value.value, binder.elements[name]!!) }
        }

      value is Value.TupleOf &&
      binder is C.Pattern.TupleOf                 ->
        (value.elements zip binder.elements).fold(mutableListOf()) { acc, (value, binder) ->
          acc.also { it += bindValue(value.value, binder) }
        }

      binder is C.Pattern.Var                     -> listOf(value)

      else                                        -> emptyList()
    }
  }

  private fun matchValue(
    value: Value,
    pattern: C.Pattern,
  ): Boolean? {
    return when {
      value is Value.IntOf &&
      pattern is C.Pattern.IntOf                   -> value.value == pattern.value

      value is Value.IntOf &&
      pattern is C.Pattern.IntRangeOf              -> value.value in pattern.min..pattern.max

      value is Value.ListOf &&
      pattern is C.Pattern.ListOf &&
      value.elements.size == pattern.elements.size -> (value.elements zip pattern.elements).all { (value, pattern) -> matchValue(value.value, pattern) == true }

      value is Value.CompoundOf &&
      pattern is C.Pattern.CompoundOf              -> value.elements.all { (name, value) -> matchValue(value.value, pattern.elements[name]!!) == true }

      value is Value.TupleOf &&
      pattern is C.Pattern.TupleOf                 -> (value.elements zip pattern.elements).all { (value, pattern) -> matchValue(value.value, pattern) == true }

      pattern is C.Pattern.Var                     -> true

      pattern is C.Pattern.Drop                    -> true

      pattern is C.Pattern.Hole                    -> null

      else                                         -> null
    }
  }

  private fun Env.quoteValue(
    value: Value,
    type: C.Type,
  ): C.Term {
    return when (value) {
      is Value.BoolOf      -> C.Term.BoolOf(value.value, C.Type.Bool(value.value))
      is Value.ByteOf      -> C.Term.ByteOf(value.value, C.Type.Byte(value.value))
      is Value.ShortOf     -> C.Term.ShortOf(value.value, C.Type.Short(value.value))
      is Value.IntOf       -> C.Term.IntOf(value.value, C.Type.Int(value.value))
      is Value.LongOf      -> C.Term.LongOf(value.value, C.Type.Long(value.value))
      is Value.FloatOf     -> C.Term.FloatOf(value.value, C.Type.Float(value.value))
      is Value.DoubleOf    -> C.Term.DoubleOf(value.value, C.Type.Double(value.value))
      is Value.StringOf    -> C.Term.StringOf(value.value, C.Type.String(value.value))
      is Value.ByteArrayOf -> C.Term.ByteArrayOf(value.elements.map { quoteValue(it.value, C.Type.Byte(null)) }, C.Type.ByteArray)
      is Value.IntArrayOf  -> C.Term.IntArrayOf(value.elements.map { quoteValue(it.value, C.Type.Int(null)) }, C.Type.IntArray)
      is Value.LongArrayOf -> C.Term.LongArrayOf(value.elements.map { quoteValue(it.value, C.Type.Long(null)) }, C.Type.LongArray)
      is Value.ListOf      -> {
        type as C.Type.List
        C.Term.ListOf(value.elements.map { quoteValue(it.value, type.element) }, type)
      }
      is Value.CompoundOf  -> {
        type as C.Type.Compound
        C.Term.CompoundOf(value.elements.mapValues { (key, element) -> quoteValue(element.value, type.elements[key]!!) }, type)
      }
      is Value.TupleOf -> {
        type as C.Type.Tuple
        C.Term.TupleOf(value.elements.mapIndexed { index, element -> quoteValue(element.value, type.elements[index]) }, type)
      }
      is Value.FuncOf  -> {
        type as C.Type.Func
        C.Term.FuncOf(value.binder, value.body, type)
      }
      is Value.ClosOf  -> {
        type as C.Type.Clos
        C.Term.ClosOf(value.binder, value.body, type)
      }
      is Value.Apply   -> {
        val (param, result) = run {
          when (val operatorType = value.operatorType) {
            is C.Type.Func -> operatorType.param to operatorType.result
            is C.Type.Clos -> operatorType.param to operatorType.result
            else           -> error("unexpected type: $operatorType")
          }
        }
        C.Term.Apply(quoteValue(value.operator, value.operatorType), quoteValue(value.arg.value, param), result)
      }
      is Value.If      -> C.Term.If(quoteValue(value.condition, C.Type.Bool.SET), quoteValue(value.thenClause.value, type), quoteValue(value.elseClause.value, type), type)
      is Value.Let     -> C.Term.Let(value.binder, quoteValue(value.init.value, value.binder.type), quoteValue(value.body.value, value.type), value.type)
      is Value.Var     -> C.Term.Var(value.name, value.level, type)
      is Value.Run     -> {
        val definition = definitions[value.name] as C.Definition.Function
        C.Term.Run(value.name, value.typeArgs, quoteValue(value.arg, definition.binder.type), definition.result)
      }
      is Value.Is      -> C.Term.Is(quoteValue(value.scrutinee, value.scrutineeType), value.scrutineer, C.Type.Bool.SET)
      is Value.Command -> C.Term.Command(quoteValue(value.element.value, C.Type.String.SET), type)
      is Value.CodeOf  -> {
        type as C.Type.Code
        C.Term.CodeOf(quoteValue(value.element.value, type.element), type)
      }
      is Value.Splice  -> C.Term.Splice(quoteValue(value.element, value.elementType), type)
      is Value.Hole    -> C.Term.Hole(value.type)
    }
  }

  private fun mangle(
    location: DefinitionLocation,
    types: List<C.Type>,
  ): DefinitionLocation =
    location.module / "${location.name}:${types.joinToString(":") { prettyType(it) }}"

  private class Env(
    definitions: Map<DefinitionLocation, C.Definition>,
    types: List<C.Type>,
    unfold: Boolean,
    val values: PersistentList<Lazy<Value>>,
    val static: Boolean,
  ) : Normalize.Env(definitions, types, unfold) {
    fun bind(
      values: List<Value>,
    ): Env =
      Env(definitions, types, unfold, this.values + values.map { lazyOf(it) }, static)

    fun withStatic(
      static: Boolean,
    ): Env =
      Env(definitions, types, unfold, values, static)
  }

  companion object {
    operator fun invoke(
      context: Context,
      dependencies: Map<DefinitionLocation, C.Definition>,
      definition: C.Definition,
    ): List<C.Definition> =
      Stage(dependencies).stage(definition)
  }
}
