package mcx.lsp

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

private val definitions: List<CompletionItem> = listOf(
  "function",
).map {
  CompletionItem(it).apply {
    kind = CompletionItemKind.Keyword
  }
}

private val annotations: List<CompletionItem> = listOf(
  "@tick",
  "@load",
  "@no_drop",
  "@leaf",
  "@builtin",
  "@export",
  "@inline",
  "@static",
).map {
  CompletionItem(it).apply {
    kind = CompletionItemKind.Keyword
  }
}

val GLOBAL_COMPLETION_ITEMS: List<CompletionItem> = definitions + annotations
