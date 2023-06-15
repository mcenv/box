package mcx.ast

sealed class Repr {
  data object End : Repr()

  data object Byte : Repr()

  data object Short : Repr()

  data object Int : Repr()

  data object Long : Repr()

  data object Float : Repr()

  data object Double : Repr()

  data object String : Repr()

  data object ByteArray : Repr()

  data object IntArray : Repr()

  data object LongArray : Repr()

  data object List : Repr()

  data object Compound : Repr()
}
