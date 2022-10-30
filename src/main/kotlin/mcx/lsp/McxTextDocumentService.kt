package mcx.lsp

import mcx.phase.*
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.CompletableFutures.computeAsync
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture
import mcx.ast.Core as C

class McxTextDocumentService : TextDocumentService {
  private val texts: HashMap<String, String> = hashMapOf()

  override fun didOpen(
    params: DidOpenTextDocumentParams,
  ) {
    texts[params.textDocument.uri] = params.textDocument.text
  }

  override fun didChange(
    params: DidChangeTextDocumentParams,
  ) {
    texts[params.textDocument.uri] = params.contentChanges.last().text
  }

  override fun didClose(
    params: DidCloseTextDocumentParams,
  ) {
    texts -= params.textDocument.uri
  }

  override fun didSave(
    params: DidSaveTextDocumentParams,
  ) {
  }

  override fun diagnostic(
    params: DocumentDiagnosticParams,
  ): CompletableFuture<DocumentDiagnosticReport> =
    computeAsync {
      val context = Context()
      phase(
        context,
        texts[params.textDocument.uri]!!,
      )
      DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(context.diagnostics))
    }

  companion object {
    private val phase: Phase<String, C.Root> = Parse..Elaborate
  }
}
