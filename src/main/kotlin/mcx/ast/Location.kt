package mcx.ast

data class Location(val parts: List<String>) {
  constructor(vararg parts: String) : this(parts.toList())

  operator fun plus(
    part: String,
  ): Location =
    Location(parts + part)

  override fun toString(): String =
    parts.joinToString(".")
}
