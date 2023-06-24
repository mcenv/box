package mcx.ast.common

sealed class Annotation {
  // TODO: add [reason] field
  /**
   * `@deprecated` indicates that the annotated definition is deprecated and should not be used.
   */
  data object Deprecated : Annotation()

  /**
   * `@unstable` indicates that the annotated definition is unstable and may be changed in the future.
   */
  data object Unstable : Annotation()

  /**
   * `@delicate` indicates that the annotated definition needs to be handled with care.
   */
  data object Delicate : Annotation()

  data object Hole : Annotation()
}
