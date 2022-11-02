package mcx.lsp

import mcx.phase.Context
import mcx.phase.Elaborate
import mcx.phase.Parse
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
  private val texts: HashMap<String, String> = hashMapOf()

  override fun connect(client: LanguageClient) {
    this.client = client
  }

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
      Elaborate(
        context,
        Parse(
          context,
          texts[params.textDocument.uri]!!,
        ),
      )
      DocumentDiagnosticReport(
        RelatedFullDocumentDiagnosticReport(
          context.diagnostics
        )
      )
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
