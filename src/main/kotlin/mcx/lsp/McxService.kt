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
import mcx.pass.Context
import mcx.pass.build.Build
import mcx.pass.build.Build.Companion.EXTENSION
import mcx.pass.build.Key
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.*

@JsonSegment("mcx")
@Suppress("unused")
class McxService : TextDocumentService,
                   WorkspaceService,
                   LanguageClientAware {
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
  private lateinit var client: LanguageClient
  private val build: Build = Build(Path(""))
  private var context: Context? = null
  private val diagnosticsHashes: ConcurrentMap<String, Int> = ConcurrentHashMap()

  override fun connect(client: LanguageClient) {
    this.client = client
    updateContext()
  }

  override fun didOpen(
    params: DidOpenTextDocumentParams,
  ) {
    runBlocking {
      build.changeText(params.textDocument.uri.toModuleLocation(), params.textDocument.text)
    }
  }

  override fun didChange(
    params: DidChangeTextDocumentParams,
  ) {
    runBlocking {
      build.changeText(params.textDocument.uri.toModuleLocation(), params.contentChanges.last().text)
    }
  }

  override fun didClose(
    params: DidCloseTextDocumentParams,
  ) {
    runBlocking {
      diagnosticsHashes -= params.textDocument.uri
      with(build) { fetchContext().closeText(params.textDocument.uri.toModuleLocation()) }
    }
  }

  override fun didSave(
    params: DidSaveTextDocumentParams,
  ) {
  }

  override fun diagnostic(
    params: DocumentDiagnosticParams,
  ): CompletableFuture<DocumentDiagnosticReport> {
    return scope.future {
      val uri = params.textDocument.uri
      // TODO: use [Key.Diagnostic]
      val elaborated = with(build) { fetchContext().fetch(Key.Elaborated(uri.toModuleLocation())) }
      val newHash = elaborated.value.diagnostics.hashCode()
      val oldHash = diagnosticsHashes[uri]
      if (oldHash == null || newHash != oldHash) {
        diagnosticsHashes[uri] = newHash
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(elaborated.value.diagnostics))
      } else {
        DocumentDiagnosticReport(RelatedUnchangedDocumentDiagnosticReport())
      }
    }
  }

  override fun hover(params: HoverParams): CompletableFuture<Hover> {
    return scope.future {
      val elaborated = with(build) {
        fetchContext().fetch(Key.Elaborated(params.textDocument.uri.toModuleLocation(), Instruction.Hover(params.position)))
      }
      Hover(
        when (val hover = elaborated.value.hover) {
          null -> throw CancellationException()
          else -> highlight(hover())
        }
      )
    }
  }

  override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
    return scope.future {
      val resolved = with(build) {
        fetchContext().fetch(Key.Resolved(params.textDocument.uri.toModuleLocation(), Instruction.Definition(params.position)))
      }
      forLeft(resolved.value.definition?.let { listOf(it) } ?: emptyList())
    }
  }

  override fun inlayHint(params: InlayHintParams): CompletableFuture<List<InlayHint>> {
    return scope.future {
      val elaborated = with(build) {
        fetchContext().fetch(Key.Elaborated(params.textDocument.uri.toModuleLocation(), Instruction.InlayHint(params.range)))
      }
      elaborated.value.inlayHints
    }
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

  @JsonRequest
  private fun build(): CompletableFuture<Boolean> {
    return scope.future {
      build.invoke().success
    }
  }

  private fun fetchContext(): Context {
    return context ?: throw CancellationException()
  }

  private fun updateContext() {
    val path = Path("pack.json")
    context = if (path.isRegularFile()) {
      try {
        Context(path.inputStream().buffered().use { @OptIn(ExperimentalSerializationApi::class) Json.decodeFromStream(it) })
      } catch (_: SerializationException) {
        null
      }
    } else {
      null
    }
  }

  private fun String.toModuleLocation(): ModuleLocation {
    return ModuleLocation(Path("src").absolute().relativize(URI(dropLast(EXTENSION.length)).toPath()).map { it.toString() })
  }
}
