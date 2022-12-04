package mcx.lsp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import mcx.ast.ModuleLocation
import mcx.phase.Build
import mcx.phase.Config
import mcx.phase.Context
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
  private lateinit var build: Build
  private var context: Context? = null
  private val diagnosticsHashes: ConcurrentMap<String, Int> = ConcurrentHashMap()

  override fun connect(client: LanguageClient) {
    this.client = client
  }

  fun setup(folder: WorkspaceFolder) {
    root = URI(folder.uri).toPath()
    build = Build(root)
    updateContext()
  }

  override fun didOpen(
    params: DidOpenTextDocumentParams,
  ): Unit =
    runBlocking {
      build.changeText(params.textDocument.uri.toModuleLocation(), params.textDocument.text)
    }

  override fun didChange(
    params: DidChangeTextDocumentParams,
  ): Unit =
    runBlocking {
      build.changeText(params.textDocument.uri.toModuleLocation(), params.contentChanges.last().text)
    }

  override fun didClose(
    params: DidCloseTextDocumentParams,
  ): Unit =
    runBlocking {
      build.closeText(params.textDocument.uri.toModuleLocation())
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
      val zonked = with(build) { fetchContext().fetch(Build.Key.ZonkResult(uri.toModuleLocation())) }
      val newHash = zonked.value.diagnostics.hashCode()
      val oldHash = diagnosticsHashes[uri]
      if (oldHash == null || newHash != oldHash) {
        diagnosticsHashes[uri] = newHash
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(zonked.value.diagnostics))
      } else {
        DocumentDiagnosticReport(RelatedUnchangedDocumentDiagnosticReport())
      }
    }

  override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> =
    CoroutineScope(Dispatchers.Default).future {
      val zonked = with(build) {
        fetchContext().fetch(
          Build.Key
            .ZonkResult(params.textDocument.uri.toModuleLocation())
            .apply { position = params.position }
        )
      }
      forLeft(zonked.value.completionItems + GLOBAL_COMPLETION_ITEMS)
    }

  override fun hover(params: HoverParams): CompletableFuture<Hover> =
    CoroutineScope(Dispatchers.Default).future {
      val zonked = with(build) {
        fetchContext().fetch(
          Build.Key
            .ZonkResult(params.textDocument.uri.toModuleLocation())
            .apply { position = params.position }
        )
      }
      Hover(
        when (val hover = zonked.value.hover) {
          null -> throw CancellationException()
          else -> highlight(hover())
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
    updateContext()
  }

  private fun fetchContext(): Context =
    context ?: throw CancellationException()

  @OptIn(ExperimentalSerializationApi::class)
  private fun updateContext() {
    val path = root.resolve("pack.json")
    context =
      if (path.exists() && path.isRegularFile()) {
        try {
          val config =
            path
              .inputStream()
              .buffered()
              .use { Json.decodeFromStream<Config>(it) }
          Context(config)
        } catch (_: SerializationException) {
          null
        }
      } else {
        null
      }
  }

  private fun String.toModuleLocation(): ModuleLocation =
    ModuleLocation(
      root
        .resolve("src")
        .relativize(URI(dropLast(".mcx".length)).toPath())
        .map { it.toString() }
    )
}
