package mcx.phase

enum class Phase(val id: String) {
  WORLD("world"),
  CONST("const");

  override fun toString(): String = id
}
