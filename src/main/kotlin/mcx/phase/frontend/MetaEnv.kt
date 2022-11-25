package mcx.phase.frontend

import mcx.ast.Core as C

class MetaEnv {
  private val solutions: MutableList<C.Type?> = mutableListOf()

  fun fresh(
    kind: C.Kind,
  ): C.Type =
    C.Type
      .Meta(solutions.size, kind)
      .also { solutions += null }

  tailrec fun force(
    type: C.Type,
  ): C.Type {
    return when (type) {
      is C.Type.Meta ->
        when (val forced = solutions.getOrNull(type.index)) {
          null -> type
          else -> force(forced)
        }
      else           -> type
    }
  }

  fun unify(
    type1: C.Type,
    type2: C.Type,
  ): Boolean {
    return when {
      type1 is C.Type.Bool &&
      type2 is C.Type.Bool      -> true

      type1 is C.Type.Byte &&
      type2 is C.Type.Byte      -> true

      type1 is C.Type.Short &&
      type2 is C.Type.Short     -> true

      type1 is C.Type.Int &&
      type2 is C.Type.Int       -> true

      type1 is C.Type.Long &&
      type2 is C.Type.Long      -> true

      type1 is C.Type.Float &&
      type2 is C.Type.Float     -> true

      type1 is C.Type.Double &&
      type2 is C.Type.Double    -> true

      type1 is C.Type.String &&
      type2 is C.Type.String    -> true

      type1 is C.Type.ByteArray &&
      type2 is C.Type.ByteArray -> true

      type1 is C.Type.IntArray &&
      type2 is C.Type.IntArray  -> true

      type1 is C.Type.LongArray &&
      type2 is C.Type.LongArray -> true

      type1 is C.Type.List &&
      type2 is C.Type.List      -> unify(type1.element, type2.element)

      type1 is C.Type.Compound &&
      type2 is C.Type.Compound  -> type1.elements.size == type2.elements.size &&
                                   type1.elements.all { (key1, element1) ->
                                     when (val element2 = type2.elements[key1]) {
                                       null -> false
                                       else -> unify(element1, element2)
                                     }
                                   }

      type1 is C.Type.Ref &&
      type2 is C.Type.Ref       -> unify(type1.element, type2.element)

      type1 is C.Type.Tuple &&
      type2 is C.Type.Tuple     -> type1.elements.size == type2.elements.size &&
                                   (type1.elements zip type2.elements).all { (element1, element2) -> unify(element1, element2) }

      type1 is C.Type.Tuple &&
      type1.elements.size == 1  -> unify(type1.elements.first(), type2)

      type2 is C.Type.Tuple &&
      type2.elements.size == 1  -> unify(type1, type2.elements.first())

      type1 is C.Type.Fun &&
      type2 is C.Type.Fun       -> unify(type1.param, type2.param) &&
                                   unify(type1.result, type2.result)

      type1 is C.Type.Union     -> false // TODO
      type2 is C.Type.Union     -> false // TODO

      type1 is C.Type.Code &&
      type2 is C.Type.Code      -> unify(type1.element, type2.element)

      type1 is C.Type.Var &&
      type2 is C.Type.Var       -> type1.level == type2.level

      type1 is C.Type.Meta      ->
        when (val solution1 = solutions[type1.index]) {
          null -> {
            solutions[type1.index] = type2
            true
          }
          else -> unify(solution1, type2)
        }

      type2 is C.Type.Meta      ->
        when (val solution2 = solutions[type2.index]) {
          null -> {
            solutions[type2.index] = type1
            true
          }
          else -> unify(type1, solution2)
        }

      type1 is C.Type.Hole      -> false
      type2 is C.Type.Hole      -> false

      else                      -> false
    }
  }
}
