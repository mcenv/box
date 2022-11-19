package mcx.ast

// TODO: use different data types for module location and definition location
data class Location(val parts: List<String>) {
  constructor(vararg parts: String) : this(parts.toList())

  operator fun plus(
    part: String,
  ): Location =
    Location(parts + part)

  fun dropLast(): Location =
    Location(parts.dropLast(1))

  override fun toString(): String =
    parts.joinToString(".")
}
