package mcx.lsp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import mcx.ast.Location
import mcx.phase.Cache
import mcx.phase.Config
import mcx.phase.prettyType
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
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
  private val diagnosticsHashes: ConcurrentMap<String, Int> = ConcurrentHashMap()

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
    CoroutineScope(Dispatchers.Default).future {
      val uri = params.textDocument.uri
      val core = cache.fetchCore(
        fetchConfig(),
        uri.toLocation(),
        false,
      )!!
      val newHash = core.diagnostics.hashCode()
      val oldHash = diagnosticsHashes[uri]
      if (oldHash == null || newHash != oldHash) {
        diagnosticsHashes[uri] = newHash
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(core.diagnostics))
      } else {
        DocumentDiagnosticReport(RelatedUnchangedDocumentDiagnosticReport())
      }
    }

  override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> =
    CoroutineScope(Dispatchers.Default).future {
      val core = cache.fetchCore(
        fetchConfig(),
        params.textDocument.uri.toLocation(),
        false,
        params.position,
      )!!
      forLeft(
        core.completionItems
        ?: resourceCompletionItems
      )
    }

  override fun hover(params: HoverParams): CompletableFuture<Hover> =
    CoroutineScope(Dispatchers.Default).future {
      val core = cache.fetchCore(
        fetchConfig(),
        params.textDocument.uri.toLocation(),
        false,
        params.position,
      )!!
      Hover(
        when (val hover = core.hover) {
          null -> throw CancellationException()
          else -> highlight(prettyType(hover))
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
        .relativize(URI(dropLast(".mcx".length)).toPath())
        .map { it.toString() }
    )
}
