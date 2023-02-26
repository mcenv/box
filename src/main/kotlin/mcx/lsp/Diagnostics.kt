package mcx.lsp

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Range

const val SOURCE: String = "mcx"

@Suppress("NOTHING_TO_INLINE")
inline fun diagnostic(
  range: Range,
  message: String,
  severity: DiagnosticSeverity,
): Diagnostic {
  return Diagnostic(
    range,
    message,
    severity,
    SOURCE,
  )
}
