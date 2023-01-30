package mcx.phase

import mcx.ast.DefinitionLocation
import mcx.ast.Core as C

object Normalize {
  open class Env(
    val definitions: Map<DefinitionLocation, C.Definition>,
    val types: List<C.Type>,
    val unfold: Boolean,
  )

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
      is C.Type.Proc      -> C.Type.Proc(evalType(type.param), evalType(type.result))
      is C.Type.Func      -> C.Type.Func(evalType(type.param), evalType(type.result))
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
