package mcx.lsp

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import mcx.ast.Location
import mcx.phase.Cache
import mcx.phase.Config
import mcx.phase.prettyType0
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.CompletableFutures.computeAsync
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.toPath

class McxService : TextDocumentService,
                   WorkspaceService,
                   LanguageClientAware {
  private lateinit var client: LanguageClient
  private lateinit var root: Path
  private lateinit var cache: Cache
  private var config: Config? = null

  override fun connect(client: LanguageClient) {
    this.client = client
  }

  fun setup(folder: WorkspaceFolder) {
    root = URI(folder.uri).toPath()
    cache = Cache(root.resolve("src"))
    updateConfig()
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
      val core = cache.fetchCore(
        fetchConfig(),
        params.textDocument.uri.toLocation(),
      )!!
      if (core.dirty) {
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(core.value.diagnostics))
      } else {
        DocumentDiagnosticReport(RelatedUnchangedDocumentDiagnosticReport())
      }
    }

  override fun hover(params: HoverParams): CompletableFuture<Hover> =
    computeAsync {
      val core = cache.fetchCore(
        fetchConfig(),
        params.textDocument.uri.toLocation(),
        params.position,
      )!!
      Hover(
        when (val hover = core.value.hover) {
          null -> MarkupContent(
            MarkupKind.PLAINTEXT,
            "",
          )
          else -> MarkupContent(
            MarkupKind.MARKDOWN,
            "```mcx\n${prettyType0(hover)}\n```",
          )
        }
      )
    }

  override fun didChangeConfiguration(
    params: DidChangeConfigurationParams,
  ) {
  }

  override fun didChangeWatchedFiles(
    params: DidChangeWatchedFilesParams,
  ) {
    updateConfig()
  }

  private fun fetchConfig(): Config {
    if (config == null) {
      throw CancellationException()
    }
    return config!!
  }

  @OptIn(ExperimentalSerializationApi::class)
  private fun updateConfig() {
    val path = root.resolve("pack.json")
    config = if (path.exists() && path.isRegularFile()) {
      path
        .inputStream()
        .buffered()
        .use {
          Json.decodeFromStream(it)
        }
    } else {
      null
    }
  }

  private fun String.toLocation(): Location =
    Location(
      root
        .resolve("src")
        .relativize(URI(this).toPath())
        .map { it.toString() }
    )
}
