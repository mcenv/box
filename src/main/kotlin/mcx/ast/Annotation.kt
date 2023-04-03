package mcx.ast

sealed class Annotation {
  // TODO: add [reason] field
  data object Deprecated : Annotation()

  data object Unstable : Annotation()

  data object Hole : Annotation()
}
