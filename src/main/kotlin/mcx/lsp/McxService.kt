package mcx.lsp

import mcx.ast.Location
import mcx.phase.Cache
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.CompletableFutures.computeAsync
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.toPath

class McxService : TextDocumentService,
                   WorkspaceService,
                   LanguageClientAware {
  private lateinit var client: LanguageClient
  private lateinit var src: Path
  private lateinit var cache: Cache

  override fun connect(client: LanguageClient) {
    this.client = client
  }

  fun setup(folder: WorkspaceFolder) {
    src = URI(folder.uri)
      .toPath()
      .resolve("src")
    cache = Cache(src)
  }

  override fun didOpen(
    params: DidOpenTextDocumentParams,
  ) {
    cache.changeText(
      params.textDocument.uri.toLocation(),
      params.textDocument.text,
    )
  }

  override fun didChange(
    params: DidChangeTextDocumentParams,
  ) {
    cache.changeText(
      params.textDocument.uri.toLocation(),
      params.contentChanges.last().text,
    )
  }

  override fun didClose(
    params: DidCloseTextDocumentParams,
  ) {
    cache.closeText(params.textDocument.uri.toLocation())
  }

  override fun didSave(
    params: DidSaveTextDocumentParams,
  ) {
  }

  override fun diagnostic(
    params: DocumentDiagnosticParams,
  ): CompletableFuture<DocumentDiagnosticReport> =
    computeAsync {
      val core = cache.fetchCore(params.textDocument.uri.toLocation())!!
      if (core.dirty) {
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(core.value.diagnostics))
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

  private fun String.toLocation(): Location =
    Location(
      src
        .relativize(URI(this).toPath())
        .map { it.toString() }
    )
}
