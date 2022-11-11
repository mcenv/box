package mcx.phase

import mcx.ast.Location
import mcx.util.rangeTo
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import mcx.ast.Core as C

sealed class Diagnostic(
  range: Range,
  message: String,
  severity: DiagnosticSeverity,
) : org.eclipse.lsp4j.Diagnostic(
  range,
  message,
  severity,
  "mcx",
) {
  class InvalidEscape(
    escape: Char,
    range: Range,
  ) : Diagnostic(
    range,
    "invalid escape: \\$escape",
    DiagnosticSeverity.Error,
  )

  class ExpectedToken(
    token: Char,
    position: Position,
  ) : Diagnostic(
    position..position,
    "expected: '$token'",
    DiagnosticSeverity.Error,
  )

  class ExpectedEndOfFile(
    position: Position,
  ) : Diagnostic(
    position..position,
    "expected: end of file",
    DiagnosticSeverity.Error,
  )

  class ExpectedResource0(
    range: Range,
  ) : Diagnostic(
    range,
    "expected: resource",
    DiagnosticSeverity.Error,
  )

  class ExpectedAnnotation(
    range: Range,
  ) : Diagnostic(
    range,
    "expected: annotation",
    DiagnosticSeverity.Error,
  )

  class ExpectedType0(
    range: Range,
  ) : Diagnostic(
    range,
    "expected: type",
    DiagnosticSeverity.Error,
  )

  class ExpectedTerm0(
    range: Range,
  ) : Diagnostic(
    range,
    "expected: term",
    DiagnosticSeverity.Error,
  )

  class ModuleNotFound(
    location: Location,
    range: Range,
  ) : Diagnostic(
    range,
    "module not found: '$location'",
    DiagnosticSeverity.Error,
  )

  class InappropriateAnnotation(
    name: String,
    range: Range,
  ) : Diagnostic(
    range,
    "inappropriate annotation: '$name'",
    DiagnosticSeverity.Error,
  )

  class KeyNotFound(
    key: String,
    range: Range,
  ) : Diagnostic(
    range,
    "key not found: '$key'",
    DiagnosticSeverity.Error,
  )

  class ExtraKey(
    key: String,
    range: Range,
  ) : Diagnostic(
    range,
    "extra key: '$key'",
    DiagnosticSeverity.Error,
  )

  class VarNotFound(
    name: String,
    range: Range,
  ) : Diagnostic(
    range,
    "variable not found: '$name'",
    DiagnosticSeverity.Error,
  )

  class VarAlreadyUsed(
    name: String,
    range: Range,
  ) : Diagnostic(
    range,
    "variable already used: '$name'",
    DiagnosticSeverity.Error,
  )

  class ResourceNotFound(
    name: String,
    range: Range,
  ) : Diagnostic(
    range,
    "resource not found: '$name'",
    DiagnosticSeverity.Error,
  )

  class ExpectedFunction(
    range: Range,
  ) : Diagnostic(
    range,
    "expected: function",
    DiagnosticSeverity.Error,
  )

  class MismatchedArity(
    expected: Int,
    actual: Int,
    range: Range,
  ) : Diagnostic(
    range,
    """mismatched arity:
      |  expected: $expected
      |  actual  : $actual
    """.trimMargin(),
    DiagnosticSeverity.Error,
  )

  class NotConvertible(
    expected: C.Type0,
    actual: C.Type0,
    range: Range,
  ) : Diagnostic(
    range,
    """not convertible:
      |  expected: ${prettyType0(expected)}
      |  actual  : ${prettyType0(actual)}
    """.trimMargin(),
    DiagnosticSeverity.Error,
  )
}
