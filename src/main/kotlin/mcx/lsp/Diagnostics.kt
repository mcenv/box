package mcx.lsp

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Range
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

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
    "mcx",
  )
}

fun diagnosticMessage(
  path: Path,
  diagnostic: Diagnostic,
): String {
  val severity = diagnostic.severity.name.lowercase()
  val location = path.invariantSeparatorsPathString
  val line = diagnostic.range.start.line + 1
  val character = diagnostic.range.start.character + 1
  val message = diagnostic.message
  return "$severity: $location:$line:$character $message"
}
