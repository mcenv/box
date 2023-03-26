package mcx.lsp

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.util.Ranges

val HEADER: Range = Range(Position(0, 0), Position(0, 0))

@Suppress("NOTHING_TO_INLINE")
inline operator fun Position.rangeTo(end: Position): Range =
  Range(this, end)

@Suppress("NOTHING_TO_INLINE")
inline operator fun Range.contains(position: Position): Boolean =
  Ranges.containsPosition(this, position)

data class Ranged<T>(
  val value: T,
  val range: Range,
) {
  inline fun <R> map(transform: (T) -> R): Ranged<R> =
    Ranged(transform(value), range)
}
