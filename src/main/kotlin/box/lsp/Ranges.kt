package box.lsp

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.util.Ranges

@Suppress("NOTHING_TO_INLINE")
inline operator fun Position.plus(length: Int): Range {
  return this..Position(line, character + length)
}

@Suppress("NOTHING_TO_INLINE")
inline operator fun Position.rangeTo(end: Position): Range {
  return Range(this, end)
}

@Suppress("NOTHING_TO_INLINE")
inline operator fun Range.contains(position: Position): Boolean {
  return Ranges.containsPosition(this, position)
}

data class Ranged<T>(
  val value: T,
  val range: Range,
) {
  inline fun <R> map(transform: (T) -> R): Ranged<R> {
    return Ranged(transform(value), range)
  }
}
