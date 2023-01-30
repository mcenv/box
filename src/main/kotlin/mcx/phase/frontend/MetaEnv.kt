package mcx.phase.frontend

import org.eclipse.lsp4j.Range
import mcx.ast.Core as C

@Suppress("NAME_SHADOWING")
class MetaEnv {
  private val kinds: MutableList<C.Kind?> = mutableListOf()
  private val types: MutableList<C.Type?> = mutableListOf()

  fun freshKind(): C.Kind =
    C.Kind
      .Meta(kinds.size)
      .also { kinds += null }

  fun freshType(
    range: Range,
    kind: C.Kind? = null,
  ): C.Type =
    C.Type
      .Meta(types.size, range, kind ?: freshKind())
      .also { types += null }

  tailrec fun forceKind(
    kind: C.Kind,
  ): C.Kind {
    return when (kind) {
      is C.Kind.Meta ->
        when (val forced = kinds.getOrNull(kind.index)) {
          null -> kind
          else -> forceKind(forced)
        }
      else           -> kind
    }
  }

  tailrec fun forceType(
    type: C.Type,
  ): C.Type {
    return when (type) {
      is C.Type.Meta ->
        when (val forced = types.getOrNull(type.index)) {
          null -> type
          else -> forceType(forced)
        }
      else           -> type
    }
  }

  fun zonkType(
    type: C.Type,
  ): C.Type {
    return when (val type = forceType(type)) {
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
      is C.Type.List      -> C.Type.List(zonkType(type.element))
      is C.Type.Compound  -> C.Type.Compound(type.elements.mapValues { zonkType(it.value) })
      is C.Type.Ref       -> C.Type.Ref(zonkType(type.element))
      is C.Type.Tuple     -> C.Type.Tuple(type.elements.map { zonkType(it) }, type.kind)
      is C.Type.Union     -> C.Type.Union(type.elements.map { zonkType(it) }, type.kind)
      is C.Type.Func      -> C.Type.Func(zonkType(type.param), zonkType(type.result))
      is C.Type.Code      -> C.Type.Code(zonkType(type.element))
      is C.Type.Var       -> type
      is C.Type.Run       -> type
      is C.Type.Meta      -> type
      is C.Type.Hole      -> type
    }
  }

  fun unifyKinds(
    kind1: C.Kind,
    kind2: C.Kind,
  ): Boolean {
    val kind1 = forceKind(kind1)
    val kind2 = forceKind(kind2)
    return when {
      kind1 is C.Kind.Meta ->
        when (val solution1 = kinds[kind1.index]) {
          null -> {
            kinds[kind1.index] = kind2
            true
          }
          else -> unifyKinds(solution1, kind2)
        }

      kind2 is C.Kind.Meta ->
        when (val solution2 = kinds[kind2.index]) {
          null -> {
            kinds[kind2.index] = kind1
            true
          }
          else -> unifyKinds(kind1, solution2)
        }

      kind1 is C.Kind.Type &&
      kind2 is C.Kind.Type -> kind1.arity == kind2.arity

      kind1 is C.Kind.Hole -> false
      kind2 is C.Kind.Hole -> false

      else                 -> false
    }
  }

  fun unifyTypes(
    type1: C.Type,
    type2: C.Type,
  ): Boolean {
    val type1 = forceType(type1)
    val type2 = forceType(type2)
    return when {
      type1 is C.Type.Meta  ->
        when (val solution1 = types[type1.index]) {
          null -> {
            types[type1.index] = type2
            true
          }
          else -> unifyTypes(solution1, type2)
        }

      type2 is C.Type.Meta  ->
        when (val solution2 = types[type2.index]) {
          null -> {
            types[type2.index] = type1
            true
          }
          else -> unifyTypes(type1, solution2)
        }

      type1 is C.Type.Bool &&
      type2 is C.Type.Bool  -> true

      type1 is C.Type.Byte &&
      type2 is C.Type.Byte  -> true

      type1 is C.Type.Short &&
      type2 is C.Type.Short -> true

      type1 is C.Type.Int &&
      type2 is C.Type.Int   -> true

      type1 is C.Type.Long &&
      type2 is C.Type.Long  -> true

      type1 is C.Type.Float &&
      type2 is C.Type.Float -> true

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
      type2 is C.Type.List      -> unifyTypes(type1.element, type2.element)

      type1 is C.Type.Compound &&
      type2 is C.Type.Compound  -> type1.elements.size == type2.elements.size &&
                                   type1.elements.all { (key1, element1) ->
                                     when (val element2 = type2.elements[key1]) {
                                       null -> false
                                       else -> unifyTypes(element1, element2)
                                     }
                                   }

      type1 is C.Type.Ref &&
      type2 is C.Type.Ref       -> unifyTypes(type1.element, type2.element)

      type1 is C.Type.Tuple &&
      type2 is C.Type.Tuple     -> type1.elements.size == type2.elements.size &&
                                   (type1.elements zip type2.elements).all { (element1, element2) -> unifyTypes(element1, element2) }

      type1 is C.Type.Tuple &&
      type1.elements.size == 1  -> unifyTypes(type1.elements.first(), type2)

      type2 is C.Type.Tuple &&
      type2.elements.size == 1  -> unifyTypes(type1, type2.elements.first())

      type1 is C.Type.Func &&
      type2 is C.Type.Func      -> unifyTypes(type1.param, type2.param) &&
                                   unifyTypes(type1.result, type2.result)

      type1 is C.Type.Union     -> false // TODO
      type2 is C.Type.Union     -> false // TODO

      type1 is C.Type.Code &&
      type2 is C.Type.Code      -> unifyTypes(type1.element, type2.element)

      type1 is C.Type.Var &&
      type2 is C.Type.Var       -> type1.level == type2.level

      type1 is C.Type.Run &&
      type2 is C.Type.Run      -> type1.name == type2.name

      type1 is C.Type.Hole      -> false
      type2 is C.Type.Hole      -> false

      else                      -> false
    }
  }
}
