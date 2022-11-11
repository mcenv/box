package mcx.ast

data class Location(val parts: List<String>) {
  operator fun plus(
    part: String,
  ): Location =
    Location(parts + part)

  override fun toString(): String =
    parts.joinToString("/")
}
