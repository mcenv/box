package mcx.lsp

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

private val definitions: List<CompletionItem> = listOf(
  "def",
).map {
  CompletionItem(it).apply {
    kind = CompletionItemKind.Keyword
  }
}

private val modifiers: List<CompletionItem> = listOf(
  "builtin",
  "export",
  "inline",
  "const",
  "world",
).map {
  CompletionItem(it).apply {
    kind = CompletionItemKind.Keyword
  }
}

val GLOBAL_COMPLETION_ITEMS: List<CompletionItem> = definitions + modifiers
