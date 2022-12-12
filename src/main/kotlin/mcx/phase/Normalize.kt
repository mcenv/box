package mcx.phase

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mcx.ast.Annotation
import mcx.ast.DefinitionLocation
import mcx.ast.Value
import mcx.phase.Normalize.Env.Companion.emptyEnv
import mcx.ast.Core as C

object Normalize {
  class Env private constructor(
    val definitions: Map<DefinitionLocation, C.Definition>,
    private val values: PersistentList<Lazy<Value>>,
    val types: List<C.Type>,
    val unfold: Boolean,
    val static: Boolean,
  ) : List<Lazy<Value>> by values {
    fun bind(
      values: List<Value>,
    ): Env =
      Env(definitions, this.values + values.map { lazyOf(it) }, types, unfold, static)

    fun withStatic(
      static: Boolean,
    ): Env =
      Env(definitions, values, types, unfold, static)

    companion object {
      fun emptyEnv(
        definitions: Map<DefinitionLocation, C.Definition>,
        types: List<C.Type>,
        unfold: Boolean,
        static: Boolean,
      ): Env =
        Env(definitions, persistentListOf(), types, unfold, static)
    }
  }

  // TODO: move this to stage phase
  fun normalizeTerm(
    definitions: Map<DefinitionLocation, C.Definition>,
    types: List<C.Type>,
    term: C.Term,
  ): C.Term =
    with(emptyEnv(definitions, types, unfold = true, static = false)) {
      quoteValue(evalTerm(term), term.type)
    }

  fun Env.evalTerm(
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
      is C.Term.RefOf       -> Value.RefOf(lazy { evalTerm(term.element) })
      is C.Term.TupleOf     -> Value.TupleOf(term.elements.map { lazy { evalTerm(it) } })
      is C.Term.FunOf       -> Value.FunOf(term.binder, term.body)
      is C.Term.Apply       -> {
        val operator = evalTerm(term.operator)
        if (static && operator is Value.FunOf) {
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
      is C.Term.Var         -> getOrNull(term.level)?.value ?: Value.Var(term.name, term.level)
      is C.Term.Run         -> {
        val arg = evalTerm(term.arg)
        val typeArgs = term.typeArgs.map { evalType(it) }
        val definition = definitions[term.name] as? C.Definition.Function
        if (static && definition != null) {
          if (Annotation.BUILTIN in definition.annotations) {
            val builtin = requireNotNull(BUILTINS[definition.name]) { "builtin not found: '${definition.name}'" }
            builtin.eval(arg, typeArgs) ?: Value.Run(term.name, typeArgs, arg)
          } else {
            emptyEnv(definitions, typeArgs, unfold = true, static = true)
              .bind(bindValue(arg, definition.binder))
              .evalTerm(definition.body)
          }
        } else {
          Value.Run(term.name, typeArgs, arg)
        }
      }
      is C.Term.Is          -> {
        val scrutinee = evalTerm(term.scrutinee)
        val matched = matchValue(scrutinee, term.scrutineer)
        if (static && matched != null) {
          Value.BoolOf(matched)
        } else {
          Value.Is(scrutinee, term.scrutineer, term.scrutinee.type)
        }
      }
      is C.Term.Command     -> error("unexpected: command") // TODO
      is C.Term.CodeOf      -> Value.CodeOf(lazy { withStatic(false).evalTerm(term.element) })
      is C.Term.Splice      -> {
        when (val element = withStatic(true).evalTerm(term.element)) {
          is Value.CodeOf -> element.element.value
          else            -> Value.Splice(element, term.element.type)
        }
      }
      is C.Term.Hole        -> Value.Hole(term.type)
    }
  }

  private fun Env.evalPattern(
    pattern: C.Pattern,
  ): C.Pattern {
    val type = evalType(pattern.type)
    return when (pattern) {
      is C.Pattern.IntOf      -> pattern
      is C.Pattern.IntRangeOf -> pattern
      is C.Pattern.ListOf     -> C.Pattern.ListOf(pattern.elements.map { evalPattern(it) }, pattern.annotations, type)
      is C.Pattern.CompoundOf -> C.Pattern.CompoundOf(pattern.elements.mapValues { evalPattern(it.value) }, pattern.annotations, type)
      is C.Pattern.TupleOf    -> C.Pattern.TupleOf(pattern.elements.map { evalPattern(it) }, pattern.annotations, type)
      is C.Pattern.Var        -> C.Pattern.Var(pattern.name, pattern.level, pattern.annotations, type)
      is C.Pattern.Drop       -> C.Pattern.Drop(pattern.annotations, type)
      is C.Pattern.Hole       -> C.Pattern.Hole(pattern.annotations, type)
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
      is Value.RefOf       -> {
        type as C.Type.Ref
        C.Term.RefOf(quoteValue(value.element.value, type.element), type)
      }
      is Value.TupleOf     -> {
        type as C.Type.Tuple
        C.Term.TupleOf(value.elements.mapIndexed { index, element -> quoteValue(element.value, type.elements[index]) }, type)
      }
      is Value.FunOf       -> {
        type as C.Type.Fun
        C.Term.FunOf(value.binder, value.body, type)
      }
      is Value.Apply       -> {
        value.operatorType as C.Type.Fun
        C.Term.Apply(quoteValue(value.operator, value.operatorType), quoteValue(value.arg.value, value.operatorType.param), value.operatorType.result)
      }
      is Value.If          -> C.Term.If(quoteValue(value.condition, C.Type.Bool(null)), quoteValue(value.thenClause.value, type), quoteValue(value.elseClause.value, type), type)
      is Value.Let         -> C.Term.Let(value.binder, quoteValue(value.init.value, value.binder.type), quoteValue(value.body.value, value.type), value.type)
      is Value.Var         -> C.Term.Var(value.name, value.level, type)
      is Value.Run         -> {
        val definition = definitions[value.name] as C.Definition.Function
        C.Term.Run(value.name, value.typeArgs, quoteValue(value.arg, definition.binder.type), definition.result)
      }
      is Value.Is          -> C.Term.Is(quoteValue(value.scrutinee, value.scrutineeType), value.scrutineer, C.Type.Bool(null))
      is Value.Command     -> C.Term.Command(value.value, C.Type.Union.END)
      is Value.CodeOf      -> {
        type as C.Type.Code
        C.Term.CodeOf(quoteValue(value.element.value, type.element), type)
      }
      is Value.Splice      -> C.Term.Splice(quoteValue(value.element, value.elementType), type)
      is Value.Hole        -> C.Term.Hole(value.type)
    }
  }

  fun Env.evalType(
    type: C.Type,
  ): C.Type {
    return when (type) {
      is C.Type.Bool      -> type
      is C.Type.Byte      -> type
      is C.Type.Short     -> type
      is C.Type.Int       -> type
      is C.Type.Long      -> type
      is C.Type.Float     -> type
      is C.Type.Double    -> type
      is C.Type.String    -> type
      is C.Type.ByteArray -> type
      is C.Type.IntArray  -> type
      is C.Type.LongArray -> type
      is C.Type.List      -> C.Type.List(evalType(type.element))
      is C.Type.Compound  -> C.Type.Compound(type.elements.mapValues { evalType(it.value) })
      is C.Type.Ref       -> C.Type.Ref(evalType(type.element))
      is C.Type.Tuple     -> C.Type.Tuple(type.elements.map { evalType(it) }, type.kind)
      is C.Type.Union     -> C.Type.Union(type.elements.map { evalType(it) }, type.kind)
      is C.Type.Fun       -> C.Type.Fun(evalType(type.param), evalType(type.result))
      is C.Type.Code      -> C.Type.Code(evalType(type.element))
      is C.Type.Var       -> types[type.level]
      is C.Type.Run       -> {
        if (unfold) {
          val definition = definitions[type.name] as C.Definition.Type
          definition.body
        } else {
          type
        }
      }
      is C.Type.Meta      -> type
      is C.Type.Hole      -> type
    }
  }
}
