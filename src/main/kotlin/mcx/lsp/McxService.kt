package mcx.lsp

import mcx.phase.Cache
import mcx.phase.Context
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.CompletableFutures.computeAsync
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

class McxService : TextDocumentService,
                   WorkspaceService,
                   LanguageClientAware {
  private lateinit var client: LanguageClient
  private val cache: Cache = Cache()
  lateinit var workspace: WorkspaceFolder

  override fun connect(client: LanguageClient) {
    this.client = client
  }

  override fun didOpen(
    params: DidOpenTextDocumentParams,
  ) {
    cache.changeText(
      params.textDocument.uri,
      params.textDocument.text,
    )
  }

  override fun didChange(
    params: DidChangeTextDocumentParams,
  ) {
    cache.changeText(
      params.textDocument.uri,
      params.contentChanges.last().text,
    )
  }

  override fun didClose(
    params: DidCloseTextDocumentParams,
  ) {
    cache.closeText(params.textDocument.uri)
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
      val core = cache.fetchCore(
        context,
        params.textDocument.uri,
      )
      if (core.dirty) {
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(context.diagnostics))
      } else {
        DocumentDiagnosticReport(RelatedUnchangedDocumentDiagnosticReport())
      }
    }

  override fun didChangeConfiguration(
    params: DidChangeConfigurationParams,
  ) {
  }

  override fun didChangeWatchedFiles(
    params: DidChangeWatchedFilesParams,
  ) {
  }
}
