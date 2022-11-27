package mcx.phase.frontend

import mcx.ast.ModuleLocation
import mcx.phase.prettyKind
import mcx.phase.prettyType
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
    token: String,
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

  class ExpectedDefinition(
    range: Range,
  ) : Diagnostic(
    range,
    "expected: definition",
    DiagnosticSeverity.Error,
  )

  class ExpectedAnnotation(
    range: Range,
  ) : Diagnostic(
    range,
    "expected: annotation",
    DiagnosticSeverity.Error,
  )

  class ExpectedType(
    range: Range,
  ) : Diagnostic(
    range,
    "expected: type",
    DiagnosticSeverity.Error,
  )

  class ExpectedTerm(
    range: Range,
  ) : Diagnostic(
    range,
    "expected: term",
    DiagnosticSeverity.Error,
  )

  class ExpectedPattern(
    range: Range,
  ) : Diagnostic(
    range,
    "expected: pattern",
    DiagnosticSeverity.Error,
  )

  class ModuleNotFound(
    location: ModuleLocation,
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

  class TypeVarNotFound(
    name: String,
    range: Range,
  ) : Diagnostic(
    range,
    "type variable not found: '$name'",
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

  class DefinitionNotFound(
    name: String,
    range: Range,
  ) : Diagnostic(
    range,
    "definition not found: '$name'",
    DiagnosticSeverity.Error,
  )

  class AmbiguousDefinition(
    name: String,
    range: Range,
  ) : Diagnostic(
    range,
    "ambiguous definition: '$name'",
    DiagnosticSeverity.Error,
  )

  class ExpectedFunction(
    range: Range,
  ) : Diagnostic(
    range,
    "expected: function",
    DiagnosticSeverity.Error,
  )

  class RequiredInline(
    range: Range,
  ) : Diagnostic(
    range,
    "required: @inline",
    DiagnosticSeverity.Error,
  )

  class StageMismatch(
    expected: Int,
    actual: Int,
    range: Range,
  ) : Diagnostic(
    range,
    """stage mismatch:
      |  expected: $expected
      |  actual  : $actual
    """.trimMargin(),
    DiagnosticSeverity.Error,
  )

  class ArityMismatch(
    expected: Int,
    actual: Int,
    range: Range,
  ) : Diagnostic(
    range,
    """arity mismatch:
      |  expected: $expected
      |  actual  : $actual
    """.trimMargin(),
    DiagnosticSeverity.Error,
  )

  class EmptyRange(
    range: Range,
  ) : Diagnostic(
    range,
    "empty range",
    DiagnosticSeverity.Error,
  )

  class KindMismatch(
    expected: C.Kind,
    actual: C.Kind,
    range: Range,
  ) : Diagnostic(
    range,
    """kind mismatch:
      |  expected: ${prettyKind(expected)}
      |  actual  : ${prettyKind(actual)}
    """.trimMargin(),
    DiagnosticSeverity.Error,
  )

  class TypeMismatch(
    expected: C.Type,
    actual: C.Type,
    range: Range,
  ) : Diagnostic(
    range,
    """type mismatch:
      |  expected: ${prettyType(expected)}
      |  actual  : ${prettyType(actual)}
    """.trimMargin(),
    DiagnosticSeverity.Error,
  )

  class UnsolvedMeta(
    type: C.Type,
    range: Range,
  ) : Diagnostic(
    range,
    "unsolved meta: ${prettyType(type)}",
    DiagnosticSeverity.Error,
  )
}
