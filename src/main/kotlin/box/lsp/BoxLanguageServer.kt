package box.lsp

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

class BoxLanguageServer : LanguageServer, LanguageClientAware {
  private val service: BoxService = BoxService()

  override fun connect(client: LanguageClient) {
    service.connect(client)
  }

  override fun initialize(
    params: InitializeParams,
  ): CompletableFuture<InitializeResult> {
    return completedFuture(
      InitializeResult().apply {
        capabilities = ServerCapabilities().apply {
          setTextDocumentSync(TextDocumentSyncKind.Full)
          diagnosticProvider = DiagnosticRegistrationOptions(true, false)
          setHoverProvider(true)
          setDefinitionProvider(true)
          setInlayHintProvider(true)
        }
      },
    )
  }

  override fun shutdown(): CompletableFuture<Any> {
    return completedFuture(null)
  }

  override fun exit() {}

  override fun getTextDocumentService(): TextDocumentService {
    return service
  }

  override fun getWorkspaceService(): WorkspaceService {
    return service
  }
}
