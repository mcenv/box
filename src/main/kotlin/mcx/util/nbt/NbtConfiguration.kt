package mcx.util.nbt

data class NbtConfiguration internal constructor(
  val root: Boolean = false,
  val compressed: Boolean = false,
  val booleanEncodingStrategy: BooleanEncodingStrategy = BooleanEncodingStrategy.AS_BOOLEAN,
  val byteTagSuffix: ByteTagSuffix = ByteTagSuffix.LOWERCASE,
  val shortTagSuffix: ShortTagSuffix = ShortTagSuffix.LOWERCASE,
  val longTagSuffix: LongTagSuffix = LongTagSuffix.UPPERCASE,
  val floatTagSuffix: FloatTagSuffix = FloatTagSuffix.LOWERCASE,
  val doubleTagSuffix: DoubleTagSuffix = DoubleTagSuffix.LOWERCASE,
  val trailingComma: Boolean = false,
)

enum class BooleanEncodingStrategy {
  AS_BOOLEAN,
  AS_BYTE,
  // AS_QUOTED_BOOLEAN?
}

enum class ByteTagSuffix(val value: Char) {
  LOWERCASE('b'),
  UPPERCASE('B'),
}

enum class ShortTagSuffix(val value: Char) {
  LOWERCASE('s'),
  UPPERCASE('S'),
}

enum class LongTagSuffix(val value: Char) {
  UPPERCASE('L'),
  LOWERCASE('l'),
}

enum class FloatTagSuffix(val value: Char) {
  LOWERCASE('f'),
  UPPERCASE('F'),
}

enum class DoubleTagSuffix(val value: String) {
  LOWERCASE("d"),
  UPPERCASE("D"),
  NONE(""),
}
