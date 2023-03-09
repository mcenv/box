package mcx.phase.frontend

import mcx.ast.Core.Term
import mcx.ast.Core.Value
import org.eclipse.lsp4j.Range

@Suppress("NAME_SHADOWING")
class Meta {
  private val values: MutableList<Value?> = mutableListOf()

  fun fresh(
    source: Range,
    type: Value,
  ): Value {
    return Value.Meta(values.size, source, type).also { values += null }
  }

  fun freshType(
    source: Range,
  ): Value {
    val tag = lazy { fresh(source, Value.Tag) }
    return fresh(source, Value.Type(tag))
  }

  tailrec fun force(
    value: Value,
  ): Value {
    return when (value) {
      is Value.Meta ->
        when (val forced = values.getOrNull(value.index)) {
          null -> value
          else -> force(forced)
        }
      else          -> value
    }
  }

  fun zonk(
    term: Term,
  ): Term {
    TODO()
  }

  fun Int.unify(
    value1: Value,
    value2: Value,
  ): Boolean {
    val value1 = force(value1)
    val value2 = force(value2)
    return when {
      value1 is Value.Tag && value2 is Value.Tag                 -> true
      value1 is Value.TagOf && value2 is Value.TagOf             -> value1.value == value2.value
      value1 is Value.Type && value2 is Value.Type               -> unify(value1.tag.value, value2.tag.value)
      value1 is Value.Bool && value2 is Value.Bool               -> true
      value1 is Value.BoolOf && value2 is Value.BoolOf           -> value1.value == value2.value
      value1 is Value.If && value2 is Value.If                   -> {
        unify(value1.condition, value2.condition) &&
        unify(value1.thenBranch.value, value2.elseBranch.value) &&
        unify(value1.elseBranch.value, value2.elseBranch.value)
      }
      value1 is Value.Is && value2 is Value.Is                   -> {
        unify(value1.scrutinee.value, value2.scrutinee.value) &&
        value1.scrutineer == value2.scrutineer // TODO: unify patterns
      }
      value1 is Value.Byte && value2 is Value.Byte               -> true
      value1 is Value.ByteOf && value2 is Value.ByteOf           -> value1.value == value2.value
      value1 is Value.Short && value2 is Value.Short             -> true
      value1 is Value.ShortOf && value2 is Value.ShortOf         -> value1.value == value2.value
      value1 is Value.Int && value2 is Value.Int                 -> true
      value1 is Value.IntOf && value2 is Value.IntOf             -> value1.value == value2.value
      value1 is Value.Long && value2 is Value.Long               -> true
      value1 is Value.LongOf && value2 is Value.LongOf           -> value1.value == value2.value
      value1 is Value.Float && value2 is Value.Float             -> true
      value1 is Value.FloatOf && value2 is Value.FloatOf         -> value1.value == value2.value
      value1 is Value.Double && value2 is Value.Double           -> true
      value1 is Value.DoubleOf && value2 is Value.DoubleOf       -> value1.value == value2.value
      value1 is Value.String && value2 is Value.String           -> true
      value1 is Value.StringOf && value2 is Value.StringOf       -> value1.value == value2.value
      value1 is Value.ByteArray && value2 is Value.ByteArray     -> true
      value1 is Value.ByteArrayOf && value2 is Value.ByteArrayOf -> {
        value1.elements.size == value2.elements.size &&
        (value1.elements zip value2.elements).all { (element1, element2) -> unify(element1.value, element2.value) }
      }
      value1 is Value.IntArray && value2 is Value.IntArray       -> true
      value1 is Value.IntArrayOf && value2 is Value.IntArrayOf   -> {
        value1.elements.size == value2.elements.size &&
        (value1.elements zip value2.elements).all { (element1, element2) -> unify(element1.value, element2.value) }
      }
      value1 is Value.LongArray && value2 is Value.LongArray     -> true
      value1 is Value.LongArrayOf && value2 is Value.LongArrayOf -> {
        value1.elements.size == value2.elements.size &&
        (value1.elements zip value2.elements).all { (element1, element2) -> unify(element1.value, element2.value) }
      }
      value1 is Value.List && value2 is Value.List               -> unify(value1.element.value, value2.element.value)
      value1 is Value.ListOf && value2 is Value.ListOf           -> {
        value1.elements.size == value2.elements.size &&
        (value1.elements zip value2.elements).all { (element1, element2) -> unify(element1.value, element2.value) }
      }
      value1 is Value.Compound && value2 is Value.Compound       -> {
        value1.elements.size == value2.elements.size &&
        value1.elements.all { (key1, element1) ->
          when (val element2 = value2.elements[key1]) {
            null -> false
            else -> unify(element1.value, element2.value)
          }
        }
      }
      value1 is Value.Union && value2 is Value.Union             -> false // TODO
      value1 is Value.Func && value2 is Value.Func               -> false // TODO
      value1 is Value.FuncOf && value2 is Value.FuncOf           -> false // TODO
      value1 is Value.Apply && value2 is Value.Apply             -> {
        unify(value1.func, value2.func) &&
        value1.args.size == value2.args.size &&
        (value1.args zip value2.args).all { (arg1, arg2) -> unify(arg1.value, arg2.value) }
      }
      value1 is Value.Code && value2 is Value.Code               -> unify(value1.element.value, value2.element.value)
      value1 is Value.CodeOf && value2 is Value.CodeOf           -> unify(value1.element.value, value2.element.value)
      value1 is Value.Splice && value2 is Value.Splice           -> unify(value1.element.value, value2.element.value)
      value1 is Value.Var && value2 is Value.Var                 -> value1.level == value2.level
      value1 is Value.Meta                                       -> {
        when (val solution1 = values[value1.index]) {
          null -> {
            values[value1.index] = value2
            true
          }
          else -> unify(solution1, value2)
        }
      }
      value2 is Value.Meta                                       -> {
        when (val solution2 = values[value2.index]) {
          null -> {
            values[value2.index] = value1
            true
          }
          else -> unify(value1, solution2)
        }
      }
      value1 is Value.Hole                                       -> false
      value2 is Value.Hole                                       -> false
      else                                                       -> false
    }
  }
}
