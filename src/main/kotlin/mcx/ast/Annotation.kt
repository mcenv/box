package mcx.ast

sealed interface Annotation {
  object Export : Annotation

  object Tick : Annotation

  object Load : Annotation

  object NoDrop : Annotation

  object Inline : Annotation

  object Builtin : Annotation

  object Hole : Annotation
}
