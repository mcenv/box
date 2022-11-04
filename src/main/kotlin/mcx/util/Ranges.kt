package mcx.util

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.util.Ranges

@Suppress("NOTHING_TO_INLINE")
inline operator fun Position.rangeTo(end: Position): Range =
  Range(
    this,
    end,
  )

@Suppress("NOTHING_TO_INLINE")
inline operator fun Range.contains(position: Position): Boolean =
  Ranges.containsPosition(
    this,
    position,
  )
