package mcx.phase

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import mcx.ast.Location
import mcx.ast.Value
import mcx.phase.Normalize.Env.Companion.emptyEnv
import mcx.ast.Core as C

object Normalize {
  class Env private constructor(
    val resources: Map<Location, C.Resource>,
    private val values: PersistentList<Lazy<Value>>,
  ) : List<Lazy<Value>> by values {
    fun bind(
      values: PersistentList<Lazy<Value>>,
    ): Env =
      Env(resources, this.values + values)

    companion object {
      fun emptyEnv(
        resources: Map<Location, C.Resource>,
      ): Env =
        Env(resources, persistentListOf())
    }
  }

  fun normalizeTerm(
    resources: Map<Location, C.Resource>,
    term: C.Term,
  ): C.Term =
    with(emptyEnv(resources)) {
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
      is C.Term.If          ->
        when (val condition = evalTerm(term.condition)) {
          is Value.BoolOf -> if (condition.value) evalTerm(term.thenClause) else evalTerm(term.elseClause)
          else            -> Value.If(condition, lazy { evalTerm(term.thenClause) }, lazy { evalTerm(term.elseClause) })
        }
      is C.Term.Let         -> bind(bindValue(evalTerm(term.init), term.binder)).evalTerm(term.body)
      is C.Term.Var         -> getOrNull(term.level)?.value ?: Value.Var(term.name, term.level)
      is C.Term.Run         -> {
        val resource = resources[term.name] as C.Resource.Function
        val arg = evalTerm(term.arg)
        if (C.Annotation.Builtin in resource.annotations) {
          val builtin = requireNotNull(BUILTINS[resource.name]) { "builtin not found: '${resource.name}'" }
          builtin.eval(arg) ?: Value.Run(term.name, arg)
        } else {
          bind(bindValue(arg, resource.binder)).evalTerm(resource.body)
        }
      }
      is C.Term.Is          -> {
        val scrutinee = evalTerm(term.scrutinee)
        when (val matched = matchValue(scrutinee, term.scrutineer)) {
          null -> Value.Is(scrutinee, term.scrutineer, term.scrutinee.type)
          else -> Value.BoolOf(matched)
        }
      }
      is C.Term.Command     -> error("unexpected: command") // TODO
      is C.Term.CodeOf      -> Value.CodeOf(lazy { evalTerm(term.element) })
      is C.Term.Splice      ->
        when (val element = evalTerm(term.element)) {
          is Value.CodeOf -> element.element.value
          else            -> Value.Splice(element, term.element.type)
        }
      is C.Term.TypeOf      -> Value.TypeOf(term.value)
      is C.Term.Hole        -> error("unexpected: hole")
    }
  }

  fun bindValue(
    value: Value,
    binder: C.Pattern,
  ): PersistentList<Lazy<Value>> {
    return when {
      value is Value.TupleOf &&
      binder is C.Pattern.TupleOf ->
        (value.elements zip binder.elements).fold(persistentListOf()) { env, (value, binder) ->
          env + bindValue(value.value, binder)
        }

      binder is C.Pattern.Var     -> persistentListOf(lazyOf(value))

      else                        -> persistentListOf()
    }
  }

  fun matchValue(
    value: Value,
    pattern: C.Pattern,
  ): Boolean? {
    return when {
      value is Value.IntOf &&
      pattern is C.Pattern.IntOf      -> value.value == pattern.value

      value is Value.IntOf &&
      pattern is C.Pattern.IntRangeOf -> value.value in pattern.min..pattern.max

      value is Value.TupleOf &&
      pattern is C.Pattern.TupleOf    -> (value.elements zip pattern.elements).all { (value, pattern) -> matchValue(value.value, pattern) == true }

      pattern is C.Pattern.Var        -> true

      pattern is C.Pattern.Drop       -> true

      pattern is C.Pattern.Hole       -> null

      else                            -> null
    }
  }

  fun Env.quoteValue(
    value: Value,
    type: C.Type,
  ): C.Term {
    return when (value) {
      is Value.BoolOf      -> C.Term.BoolOf(value.value, C.Type.Bool)
      is Value.ByteOf      -> C.Term.ByteOf(value.value, C.Type.Byte)
      is Value.ShortOf     -> C.Term.ShortOf(value.value, C.Type.Short)
      is Value.IntOf       -> C.Term.IntOf(value.value, C.Type.Int)
      is Value.LongOf      -> C.Term.LongOf(value.value, C.Type.Long)
      is Value.FloatOf     -> C.Term.FloatOf(value.value, C.Type.Float)
      is Value.DoubleOf    -> C.Term.DoubleOf(value.value, C.Type.Double)
      is Value.StringOf    -> C.Term.StringOf(value.value, C.Type.String)
      is Value.ByteArrayOf -> C.Term.ByteArrayOf(value.elements.map { quoteValue(it.value, C.Type.Byte) }, C.Type.ByteArray)
      is Value.IntArrayOf  -> C.Term.IntArrayOf(value.elements.map { quoteValue(it.value, C.Type.Int) }, C.Type.IntArray)
      is Value.LongArrayOf -> C.Term.LongArrayOf(value.elements.map { quoteValue(it.value, C.Type.Long) }, C.Type.LongArray)
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
      is Value.If          -> C.Term.If(quoteValue(value.condition, C.Type.Bool), quoteValue(value.thenClause.value, type), quoteValue(value.elseClause.value, type), type)
      is Value.Var         -> C.Term.Var(value.name, value.level, type)
      is Value.Run         -> {
        val resource = resources[value.name] as C.Resource.Function
        C.Term.Run(value.name, quoteValue(value.arg, resource.param), resource.result)
      }
      is Value.Is          -> C.Term.Is(quoteValue(value.scrutinee, value.scrutineeType), value.scrutineer, C.Type.Bool)
      is Value.CodeOf      -> {
        type as C.Type.Code
        C.Term.CodeOf(quoteValue(value.element.value, type.element), type)
      }
      is Value.Splice      -> C.Term.Splice(quoteValue(value.element, value.elementType), type)
      is Value.TypeOf      -> C.Term.TypeOf(value.value, C.Type.Type)
    }
  }
}
