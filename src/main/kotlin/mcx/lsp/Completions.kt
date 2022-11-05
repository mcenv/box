package mcx.lsp

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

val resourceCompletionItems: List<CompletionItem> by lazy {
  listOf(
    "predicate",
    "recipe",
    "loot_table",
    "item_modifier",
    "advancement",
    "function",
  ).map {
    CompletionItem(it).apply {
      kind = CompletionItemKind.Keyword
    }
  }
}
