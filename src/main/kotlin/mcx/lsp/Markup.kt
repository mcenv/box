package mcx.lsp

import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind

fun highlight(text: String): MarkupContent {
  return MarkupContent(MarkupKind.MARKDOWN, "```mcx\n$text\n```")
}
