package box.lsp

import box.ast.common.ModuleLocation
import box.pass.Build
import box.pass.Config
import box.pass.Context
import box.pass.Key
import box.util.decodeFromJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
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
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.isRegularFile
import kotlin.io.path.toPath

@JsonSegment("box")
@Suppress("unused")
class BoxService : TextDocumentService, WorkspaceService, LanguageClientAware {
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
  private lateinit var client: LanguageClient
  private val build: Build = Build(Path(""))
  private var context: Context? = null
  private val diagnosticsHashes: ConcurrentMap<String, Int> = ConcurrentHashMap()

  override fun connect(client: LanguageClient) {
    this.client = client
    updateContext()
  }

  override fun didOpen(params: DidOpenTextDocumentParams) {
    runBlocking {
      build.changeText(params.textDocument.uri.toModuleLocation(fetchContext()), params.textDocument.text)
    }
  }

  override fun didChange(params: DidChangeTextDocumentParams) {
    runBlocking {
      build.changeText(params.textDocument.uri.toModuleLocation(fetchContext()), params.contentChanges.last().text)
    }
  }

  override fun didClose(params: DidCloseTextDocumentParams) {
    runBlocking {
      diagnosticsHashes -= params.textDocument.uri
      with(build) {
        val context = fetchContext()
        closeText(params.textDocument.uri.toModuleLocation(context))
      }
    }
  }

  override fun didSave(params: DidSaveTextDocumentParams) {}

  override fun diagnostic(params: DocumentDiagnosticParams): CompletableFuture<DocumentDiagnosticReport> {
    return scope.future {
      val context = fetchContext()
      val uri = params.textDocument.uri
      // TODO: use [Key.Diagnostic]
      val elaborated = with(build) { context.fetch(Key.Elaborated(uri.toModuleLocation(context))) }
      val newHash = elaborated.value.diagnostics.hashCode()
      val oldHash = diagnosticsHashes[uri]
      if (oldHash == null || newHash != oldHash) {
        diagnosticsHashes[uri] = newHash
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(elaborated.value.diagnostics.values.flatten()))
      } else {
        DocumentDiagnosticReport(RelatedUnchangedDocumentDiagnosticReport())
      }
    }
  }

  override fun hover(params: HoverParams): CompletableFuture<Hover> {
    return scope.future {
      val elaborated = with(build) {
        val context = fetchContext()
        context.fetch(Key.Elaborated(params.textDocument.uri.toModuleLocation(context), Instruction.Hover(params.position)))
      }
      when (val hover = elaborated.value.hover) {
        null -> throw CancellationException()
        else -> hover()
      }
    }
  }

  override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
    return scope.future {
      val resolved = with(build) {
        val context = fetchContext()
        context.fetch(Key.Resolved(params.textDocument.uri.toModuleLocation(context), Instruction.Definition(params.position)))
      }
      forLeft(resolved.value.definition?.let { listOf(it) } ?: emptyList())
    }
  }

  override fun inlayHint(params: InlayHintParams): CompletableFuture<List<InlayHint>> {
    return scope.future {
      val elaborated = with(build) {
        val context = fetchContext()
        context.fetch(Key.Elaborated(params.textDocument.uri.toModuleLocation(context), Instruction.InlayHint(params.range)))
      }
      elaborated.value.inlayHints
    }
  }

  override fun didChangeConfiguration(params: DidChangeConfigurationParams) {}

  override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
    updateContext()
  }

  @JsonRequest
  private fun build(): CompletableFuture<Boolean> {
    return scope.future {
      val result = build.invoke()
      result.diagnosticsByPath.forEach { (path, diagnostics) ->
        diagnostics.forEach {
          client.logMessage(MessageParams(MessageType.Info, diagnosticMessage(path, it)))
        }
      }
      result.success
    }
  }

  private fun fetchContext(): Context {
    return context ?: throw CancellationException()
  }

  private fun updateContext() {
    val path = Path("pack.json")
    context = if (path.isRegularFile()) {
      try {
        Context(path.decodeFromJson<Config>())
      } catch (_: SerializationException) {
        null
      }
    } else {
      null
    }
  }

  private fun String.toModuleLocation(context: Context): ModuleLocation {
    return ModuleLocation(listOf(context.config.name) + Path("src").absolute().relativize(URI(dropLast(".box".length)).toPath()).map { it.toString() })
  }
}
