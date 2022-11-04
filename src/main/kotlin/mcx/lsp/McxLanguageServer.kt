package mcx.lsp

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft
import org.eclipse.lsp4j.services.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

class McxLanguageServer : LanguageServer,
                          LanguageClientAware {
  private val service: McxService = McxService()

  override fun connect(client: LanguageClient) {
    service.connect(client)
  }

  override fun initialize(
    params: InitializeParams,
  ): CompletableFuture<InitializeResult> {
    service.workspace = params.workspaceFolders.first()
    return completedFuture(
      InitializeResult().apply {
        capabilities = ServerCapabilities().apply {
          textDocumentSync = forLeft(TextDocumentSyncKind.Full)
          diagnosticProvider = DiagnosticRegistrationOptions(
            true,
            false,
          )
        }
      },
    )
  }

  override fun shutdown(): CompletableFuture<Any> =
    completedFuture(null)

  override fun exit() {}

  override fun getTextDocumentService(): TextDocumentService =
    service

  override fun getWorkspaceService(): WorkspaceService =
    service
}
