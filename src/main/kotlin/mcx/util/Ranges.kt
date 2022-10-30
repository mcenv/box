package mcx.util

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

@Suppress("NOTHING_TO_INLINE")
inline operator fun Position.rangeTo(end: Position): Range =
  Range(
    this,
    end,
  )
