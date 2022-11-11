package mcx.lsp

import mcx.ast.Registry
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

val resourceCompletionItems: List<CompletionItem> by lazy {
  Registry
    .values()
    .map {
      CompletionItem(it.string).apply {
        kind = CompletionItemKind.Keyword
      }
    }
}
