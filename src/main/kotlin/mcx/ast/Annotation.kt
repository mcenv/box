package mcx.ast

sealed interface Annotation {
  // TODO: add [reason] field
  object Deprecated : Annotation

  object Unstable : Annotation

  object Hole : Annotation
}
