package mcx.lsp

import org.eclipse.lsp4j.Position

sealed interface Instruction {
  data class Hover(val position: Position) : Instruction

  data class Definition(val position: Position) : Instruction
}
