package mcx.lsp

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

val resourceCompletionItems: List<CompletionItem> by lazy {
  listOf(
    "function",
    "/predicates",
    "/recipes",
    "/loot_tables",
    "/item_modifiers",
    "/advancements",
    "/dimension_type",
    "/worldgen/biome",
    "/dimension",
  )
    .map {
      CompletionItem(it).apply {
        kind = CompletionItemKind.Keyword
      }
    }
}
