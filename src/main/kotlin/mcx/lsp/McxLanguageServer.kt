package mcx.lsp

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

class McxLanguageServer : LanguageServer {
  private val textDocumentService: McxTextDocumentService = McxTextDocumentService()
  private val workspaceService: McxWorkspaceService = McxWorkspaceService()

  override fun initialize(
    params: InitializeParams,
  ): CompletableFuture<InitializeResult> =
    completedFuture(
      InitializeResult().apply {
        capabilities = ServerCapabilities().apply {
          textDocumentSync = forLeft(TextDocumentSyncKind.Full)
          diagnosticProvider = DiagnosticRegistrationOptions()
        }
      },
    )

  override fun shutdown(): CompletableFuture<Any> =
    completedFuture(null)

  override fun exit() {}

  override fun getTextDocumentService(): TextDocumentService =
    textDocumentService

  override fun getWorkspaceService(): WorkspaceService =
    workspaceService
}
