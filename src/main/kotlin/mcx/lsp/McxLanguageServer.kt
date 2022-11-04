package mcx.lsp

import org.eclipse.lsp4j.*
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
    service.setup(params.workspaceFolders.first())
    return completedFuture(
      InitializeResult().apply {
        capabilities = ServerCapabilities().apply {
          setTextDocumentSync(TextDocumentSyncKind.Full)
          diagnosticProvider = DiagnosticRegistrationOptions(
            true,
            false,
          )
          setHoverProvider(true)
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
