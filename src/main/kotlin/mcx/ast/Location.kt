package mcx.ast

data class Location(val parts: List<String>) {
  override fun toString(): String =
    parts.joinToString("/")
}
