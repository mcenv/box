package mcx.util.nbt.internal

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.*
import mcx.util.nbt.AsIntTag
import mcx.util.nbt.has

internal const val END: Byte = 0
internal const val BYTE: Byte = 1
internal const val SHORT: Byte = 2
internal const val INT: Byte = 3
internal const val LONG: Byte = 4
internal const val FLOAT: Byte = 5
internal const val DOUBLE: Byte = 6
internal const val BYTE_ARRAY: Byte = 7
internal const val STRING: Byte = 8
internal const val LIST: Byte = 9
internal const val COMPOUND: Byte = 10
internal const val INT_ARRAY: Byte = 11
internal const val LONG_ARRAY: Byte = 12

@OptIn(ExperimentalSerializationApi::class)
internal fun SerialDescriptor.toTagType(): Byte {
  return when (kind) {
    SerialKind.ENUM        -> when {
      annotations.has<AsIntTag>() -> INT
      else                        -> STRING
    }
    SerialKind.CONTEXTUAL  -> TODO()
    PrimitiveKind.BOOLEAN  -> BYTE
    PrimitiveKind.BYTE     -> BYTE
    PrimitiveKind.CHAR     -> TODO()
    PrimitiveKind.SHORT    -> SHORT
    PrimitiveKind.INT      -> INT
    PrimitiveKind.LONG     -> LONG
    PrimitiveKind.FLOAT    -> FLOAT
    PrimitiveKind.DOUBLE   -> DOUBLE
    PrimitiveKind.STRING   -> STRING
    StructureKind.CLASS    -> COMPOUND
    StructureKind.LIST     -> LIST
    StructureKind.MAP      -> TODO()
    StructureKind.OBJECT   -> TODO()
    PolymorphicKind.SEALED -> TODO()
    PolymorphicKind.OPEN   -> TODO()
  }
}
