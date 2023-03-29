package mcx.lsp

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

sealed interface Instruction {
  data class Hover(val position: Position) : Instruction

  data class Definition(val position: Position) : Instruction

  data class InlayHint(val range: Range) : Instruction
}
