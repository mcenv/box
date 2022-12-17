package mcx.ast

enum class Registry(
  val string: String,
  val extension: String,
) {
  FUNCTIONS(
    "functions",
    "mcfunction",
  ),
}
