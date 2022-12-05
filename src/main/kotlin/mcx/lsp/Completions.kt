package mcx.lsp

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

private val DEFINITION_COMPLETION_ITEMS: List<CompletionItem> = listOf(
  "function",
  "/predicates",
  "/recipes",
  "/loot_tables",
  "/item_modifiers",
  "/advancements",
  "/dimension_type",
  "/worldgen/biome",
  "/dimension",
).map {
  CompletionItem(it).apply {
    kind = CompletionItemKind.Keyword
  }
}

private val ANNOTATION_COMPLETION_ITEMS: List<CompletionItem> = listOf(
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

val GLOBAL_COMPLETION_ITEMS: List<CompletionItem> = DEFINITION_COMPLETION_ITEMS + ANNOTATION_COMPLETION_ITEMS
