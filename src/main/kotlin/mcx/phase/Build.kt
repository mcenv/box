package mcx.phase

import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import mcx.ast.*
import mcx.phase.backend.Generate
import mcx.phase.backend.Lift
import mcx.phase.backend.Pack
import mcx.phase.backend.Stage
import mcx.phase.frontend.*
import org.eclipse.lsp4j.Position
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.io.path.*
import kotlin.system.exitProcess

// TODO: redesign
class Build(
  private val root: Path,
) {
  private val src: Path = root.resolve("src")
  private val texts: ConcurrentMap<ModuleLocation, String> = ConcurrentHashMap()

  fun changeText(
    location: ModuleLocation,
    text: String,
  ) {
    texts[location] = text
  }

  fun closeText(
    location: ModuleLocation,
  ) {
    texts -= location
  }

  // TODO: track external modification?
  suspend fun fetchText(
    location: ModuleLocation,
  ): String? {
    return when (val text = texts[location]) {
      null -> withContext(Dispatchers.IO) {
        src
          .resolve(location.parts.joinToString("/", postfix = ".mcx"))
          .let { path ->
            if (path.exists()) {
              return@withContext path
                .readText()
                .also {
                  texts[location] = it
                }
            }
          }
        STD_SRC
          .resolve(location.parts.joinToString("/", postfix = ".mcx"))
          .let { path ->
            if (path.exists()) {
              return@withContext path
                .readText()
                .also {
                  texts[location] = it
                }
            }
          }
        null
      }
      else -> text
    }
  }

  suspend fun fetchSurface(
    context: Context,
    location: ModuleLocation,
  ): Parse.Result? =
    coroutineScope {
      val text = fetchText(location) ?: return@coroutineScope null
      Parse(context, location, text)
    }

  suspend fun fetchResolved(
    context: Context,
    location: ModuleLocation,
  ): Resolve.Result? =
    coroutineScope {
      val surface = fetchSurface(context, location) ?: return@coroutineScope null
      val dependencies =
        surface.module.imports
          .map { async { Resolve.Dependency(it.value, fetchResolved(context, it.value)?.module, it.range) } }
          .plus(async { if (location == PRELUDE) null else Resolve.Dependency(PRELUDE, fetchResolved(context, PRELUDE)!!.module, null) })
          .awaitAll()
          .filterNotNull()
      Resolve(context, dependencies, surface)
    }

  suspend fun fetchSignature(
    context: Context,
    location: ModuleLocation,
  ): Elaborate.Result? =
    coroutineScope {
      val resolved = fetchResolved(context, location) ?: return@coroutineScope null
      Elaborate(context, emptyList(), resolved, true)
    }

  suspend fun fetchCore(
    context: Context,
    location: ModuleLocation,
    position: Position? = null,
  ): Elaborate.Result =
    coroutineScope {
      val resolved = fetchResolved(context, location)!!
      val signature = fetchSignature(context, location)!!
      val dependencies =
        resolved.module.imports
          .map { async { fetchSignature(context, it.value)?.module } }
          .plus(async { fetchSignature(context, PRELUDE)!!.module })
          .awaitAll()
          .filterNotNull()
          .plus(signature.module)
      Elaborate(context, dependencies, Resolve.Result(resolved.module, signature.diagnostics), false, position)
    }

  suspend fun fetchZonked(
    context: Context,
    location: ModuleLocation,
    position: Position? = null,
  ): Zonk.Result =
    coroutineScope {
      val core = fetchCore(context, location, position)
      Zonk(context, core)
    }

  suspend fun fetchStaged(
    context: Context,
    location: DefinitionLocation,
  ): List<Core.Definition> =
    coroutineScope {
      val zonked = fetchZonked(context, location.module)
      val definition = zonked.module.definitions.find { it.name == location }!!
      val dependencies =
        fetchSurface(context, location.module)!!.module.imports
          .map { async { fetchZonked(context, it.value).module.definitions } }
          .plus(async { fetchZonked(context, PRELUDE).module.definitions })
          .awaitAll()
          .flatten()
          .plus(zonked.module.definitions)
          .associateBy { it.name }
      Stage(context, dependencies, definition)
    }

  suspend fun fetchLifted(
    context: Context,
    location: DefinitionLocation,
  ): List<Lifted.Definition> =
    coroutineScope {
      val staged = fetchStaged(context, location)
      staged.flatMap { Lift(context, it) }
    }

  suspend fun fetchPacked(
    context: Context,
    location: DefinitionLocation,
  ): List<Packed.Definition> =
    coroutineScope {
      val lifted = fetchLifted(context, location)
      lifted
        .map { async { Pack(context, it) } }
        .awaitAll()
    }

  suspend fun fetchGenerated(
    context: Context,
    location: DefinitionLocation,
  ): Map<String, String> =
    coroutineScope {
      val packed = fetchPacked(context, location)
      packed
        .map { async { Generate(context, it) } }
        .awaitAll()
        .toMap()
    }

  @OptIn(ExperimentalSerializationApi::class)
  suspend operator fun invoke() {
    val serverProperties = Properties().apply {
      load(
        root
          .resolve("server.properties")
          .inputStream()
          .buffered()
      )
    }
    val levelName = serverProperties.getProperty("level-name")
    val datapacks =
      root
        .resolve(levelName)
        .resolve("datapacks")
        .also { it.createDirectories() }

    val context =
      root
        .resolve("pack.json")
        .inputStream()
        .buffered()
        .use {
          Json.decodeFromStream<Context>(it)
        }

    fun Path.toModuleLocation(): ModuleLocation =
      ModuleLocation(
        src
          .relativize(this)
          .invariantSeparatorsPathString
          .dropLast(".mcx".length)
          .split('/')
      )

    val datapackRoot = datapacks
      .resolve(context.name)
      .also { it.createDirectories() }

    // TODO: generate dispatcher
    val outputModules = runBlocking {
      val diagnosticsByPath = Collections.synchronizedList(mutableListOf<Pair<Path, List<Diagnostic>>>())
      val inputs = Files
        .walk(src)
        .filter { it.extension == "mcx" }
        .map { path ->
          async {
            val zonked = fetchZonked(context, path.toModuleLocation())
            diagnosticsByPath += path to zonked.diagnostics
            zonked.module.definitions
              .map { async { fetchGenerated(context, it.name) } }
              .awaitAll()
          }
        }
        .toList()
        .awaitAll()

      if (diagnosticsByPath.isNotEmpty()) {
        diagnosticsByPath.forEach { (path, diagnostics) ->
          diagnostics.forEach {
            println("[${it.severity.name.lowercase()}] ${path.invariantSeparatorsPathString}:${it.range.start.line + 1}:${it.range.start.character + 1} ${it.message}")
          }
        }
        exitProcess(1)
      }

      inputs
        .asSequence()
        .flatten()
        .plus(mapOf(Generate(context, Pack.packDispatch(context.liftedFunctions))))
        .map { it.mapKeys { (name, _) -> datapackRoot.resolve(name) } }
        .reduce { acc, map -> acc + map }
        .onEach { (name, definition) ->
          name
            .also { it.parent.createDirectories() }
            .bufferedWriter()
            .use { it.write(definition) }
        }
        .toMap()
    }

    Files
      .walk(datapackRoot)
      .filter { it.isRegularFile() }
      .forEach {
        if (it !in outputModules) {
          it.deleteExisting()
        }
      }
  }

  companion object {
    private val STD_SRC: Path

    init {
      val uri =
        Build::class.java
          .getResource("/std/src")!!
          .toURI()
      FileSystems.newFileSystem(uri, emptyMap<String, Nothing>())
      STD_SRC = Paths.get(uri)
    }
  }
}
