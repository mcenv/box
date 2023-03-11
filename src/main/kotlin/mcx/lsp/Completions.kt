package mcx.lsp

import mcx.ast.Modifier
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

private val definitions: List<CompletionItem> = listOf(
  "def",
).map {
  CompletionItem(it).apply {
    kind = CompletionItemKind.Keyword
  }
}

private val modifiers: List<CompletionItem> = Modifier.values().map {
  CompletionItem(it.id).apply {
    kind = CompletionItemKind.Keyword
  }
}

val GLOBAL_COMPLETION_ITEMS: List<CompletionItem> = definitions + modifiers
