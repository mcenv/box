package mcx.phase.frontend

import mcx.ast.Core.Definition
import mcx.ast.Core.Module
import mcx.ast.Core.Pattern
import mcx.ast.Core.Term
import mcx.ast.Core.Value
import mcx.lsp.diagnostic
import mcx.phase.Context
import mcx.util.toSubscript
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Range

@Suppress("NAME_SHADOWING")
class Zonk private constructor(
  private val input: Elaborate.Result,
) {
  private val meta: Meta = input.meta
  private val unsolved: MutableSet<Value.Meta> = hashSetOf()

  private fun zonk(): Result {
    val module = zonkModule(input.module)
    return Result(
      module,
      input.diagnostics + unsolved.map { unsolvedMeta(it.index, it.source) },
      input.completionItems,
      input.hover,
    )
  }

  private fun zonkModule(
    module: Module,
  ): Module {
    val definitions = module.definitions.map { zonkDefinition(it) }
    return Module(module.name, definitions)
  }

  private fun zonkDefinition(
    definition: Definition,
  ): Definition {
    return when (definition) {
      is Definition.Def -> {
        val type = definition.type // TODO: zonk
        val body = definition.body?.let { zonkTerm(it) }
        Definition.Def(definition.modifiers, definition.name, type, body)
      }
    }
  }

  private fun zonkTerm(
    term: Term,
  ): Term {
    return term // TODO
  }

  private fun zonkPattern(
    pattern: Pattern,
  ): Pattern {
    return pattern // TODO
  }

  data class Result(
    val module: Module,
    val diagnostics: List<Diagnostic>,
    val completionItems: List<CompletionItem>,
    val hover: (() -> String)?,
  )

  companion object {
    private fun unsolvedMeta(
      index: Int,
      range: Range,
    ): Diagnostic {
      return diagnostic(
        range,
        "unsolved meta: ?${index.toSubscript()}",
        DiagnosticSeverity.Error,
      )
    }

    operator fun invoke(
      context: Context,
      input: Elaborate.Result,
    ): Result =
      Zonk(input).zonk()
  }
}
