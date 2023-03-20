package mcx.phase.backend

import mcx.ast.Core.Definition
import mcx.ast.Core.Term
import mcx.phase.Context

class Stage private constructor() {
  private fun stageDefinition(
    definition: Definition,
  ): Definition? {
    return when (definition) {
      is Definition.Def -> {
        val type = stageTerm(definition.type)
        val body = definition.body?.let { stageTerm(it) }
        Definition.Def(definition.modifiers, definition.name, type, body)
      }
    }
  }

  private fun stageTerm(
    term: Term,
  ): Term {
    return term // TODO
  }

  companion object {
    operator fun invoke(
      context: Context,
      definition: Definition,
    ): Definition? {
      return Stage().stageDefinition(definition)
    }
  }
}
