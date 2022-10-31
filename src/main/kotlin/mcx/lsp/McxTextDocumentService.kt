package mcx.lsp

import mcx.phase.Cache
import mcx.phase.Context
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.CompletableFutures.computeAsync
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture

class McxTextDocumentService : TextDocumentService {
  private val cache: Cache = Cache()

  override fun didOpen(
    params: DidOpenTextDocumentParams,
  ) {
    cache.modifyDocument(
      params.textDocument.uri,
      params.textDocument.text,
    )
  }

  override fun didChange(
    params: DidChangeTextDocumentParams,
  ) {
    cache.modifyDocument(
      params.textDocument.uri,
      params.contentChanges.last().text,
    )
  }

  override fun didClose(
    params: DidCloseTextDocumentParams,
  ) {
    cache.closeDocument(params.textDocument.uri)
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
      cache.fetchCore(
        context,
        params.textDocument.uri,
      )
      DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(context.diagnostics))
    }
}
