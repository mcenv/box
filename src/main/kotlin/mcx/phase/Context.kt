package mcx.phase

class Context {
  private val _diagnostics: MutableList<Diagnostic> = mutableListOf()
  val diagnostics: List<Diagnostic> = _diagnostics

  operator fun plusAssign(diagnostic: Diagnostic) {
    _diagnostics += diagnostic
  }
}
