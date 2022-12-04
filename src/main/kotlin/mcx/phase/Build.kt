package mcx.phase

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import mcx.ast.*
import mcx.data.PackMetadata
import mcx.data.PackMetadataSection
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
  private val values: ConcurrentMap<Key<*>, Value<*>> = ConcurrentHashMap()
  private val mutexes: ConcurrentMap<Key<*>, Mutex> = ConcurrentHashMap()

  sealed interface Key<V> {
    data class Text(
      val location: ModuleLocation,
    ) : Key<String?>

    data class ParseResult(
      val location: ModuleLocation,
    ) : Key<Parse.Result?>

    data class ResolveResult(
      val location: ModuleLocation,
    ) : Key<Resolve.Result?>

    data class Signature(
      val location: ModuleLocation,
    ) : Key<Elaborate.Result?>

    data class ElaborateResult(
      val location: ModuleLocation,
    ) : Key<Elaborate.Result> {
      var position: Position? = null
    }

    data class ZonkResult(
      val location: ModuleLocation,
    ) : Key<Zonk.Result> {
      var position: Position? = null
    }

    data class StageResult(
      val location: DefinitionLocation,
    ) : Key<List<Core.Definition>>

    data class LiftResult(
      val location: DefinitionLocation,
    ) : Key<List<Lifted.Definition>>

    data class PackResult(
      val locations: List<DefinitionLocation>,
    ) : Key<List<Packed.Definition>>

    data class GenerateResult(
      val locations: List<DefinitionLocation>,
    ) : Key<Map<String, String>>
  }

  data class Value<V>(
    val value: V,
    val hash: Int,
  ) {
    companion object {
      val NULL: Value<*> = Value(null, 0)
    }
  }

  suspend fun changeText(
    location: ModuleLocation,
    text: String,
  ) {
    val key = Key.Text(location)
    mutexes
      .computeIfAbsent(key) { Mutex() }
      .withLock { values[key] = Value(text, 0) }
  }

  suspend fun closeText(
    location: ModuleLocation,
  ) {
    suspend fun close(key: Key<*>) {
      mutexes[key]?.withLock {
        values -= key
        mutexes -= key
      }
    }

    return coroutineScope {
      close(Key.Text(location))
      close(Key.ParseResult(location))
      close(Key.ResolveResult(location))
      close(Key.Signature(location))
      close(Key.ElaborateResult(location))
      close(Key.ZonkResult(location))
    }
  }

  @Suppress("UNCHECKED_CAST")
  suspend fun <V> Context.fetch(
    key: Key<V>,
  ): Value<V> =
    coroutineScope {
      val value = values[key]
      mutexes
        .computeIfAbsent(key) { Mutex() }
        .withLock {
          when (key) {
            is Key.Text            -> {
              value ?: withContext(Dispatchers.IO) {
                src
                  .resolve(key.location.parts.joinToString("/", postfix = ".mcx"))
                  .let { path ->
                    if (path.exists() && path.isRegularFile()) {
                      return@withContext Value(path.readText(), 0)
                    }
                  }
                STD_SRC
                  .resolve(key.location.parts.joinToString("/", postfix = ".mcx"))
                  .let { path ->
                    if (path.exists()) {
                      return@withContext Value(path.readText(), 0)
                    }
                  }
                Value.NULL
              }
            }

            is Key.ParseResult     -> {
              val text = fetch(Key.Text(key.location))
              if (text.value == null) {
                return@coroutineScope Value.NULL
              }
              val hash = text.value.hashCode()
              if (value == null || value.hash != hash) {
                Value(Parse(this@fetch, key.location, text.value), hash)
              } else {
                value
              }
            }

            is Key.ResolveResult   -> {
              val location = key.location
              val surface = fetch(Key.ParseResult(location))
              if (surface.value == null) {
                return@coroutineScope Value.NULL
              }
              val dependencyHashes = surface.value.module.imports
                .map { async { fetch(Key.ResolveResult(it.value)) } }
                .plus(async { if (location == PRELUDE) null else fetch(Key.ResolveResult(PRELUDE)) })
                .awaitAll()
                .filterNotNull()
                .map { it.hash }
              val dependencies = surface.value.module.imports
                .map { async { Resolve.Dependency(it.value, fetch(Key.ResolveResult(it.value)).value?.module, it.range) } }
                .plus(async { if (location == PRELUDE) null else Resolve.Dependency(PRELUDE, fetch(Key.ResolveResult(PRELUDE)).value!!.module, null) })
                .awaitAll()
                .filterNotNull()
              val hash = Objects.hash(surface.hash, dependencyHashes)
              if (value == null || value.hash != hash) {
                Value(Resolve(this@fetch, dependencies, surface.value), hash)
              } else {
                value
              }
            }

            is Key.Signature       -> {
              val resolved = fetch(Key.ResolveResult(key.location))
              if (resolved.value == null) {
                return@coroutineScope Value.NULL
              }
              val hash = resolved.hash
              if (value == null || value.hash != hash) {
                Value(Elaborate(this@fetch, emptyList(), resolved.value, true), hash)
              } else {
                value
              }
            }

            is Key.ElaborateResult -> {
              val location = key.location
              val resolved = fetch(Key.ResolveResult(location)) as Value<Resolve.Result>
              val signature = fetch(Key.Signature(location)) as Value<Elaborate.Result>
              val results = resolved.value.module.imports
                .map { async { fetch(Key.Signature(it.value)) } }
                .plus(async { fetch(Key.Signature(PRELUDE)) })
                .awaitAll()
              val dependencies = results
                .mapNotNull { it.value?.module }
                .plus(signature.value.module)
              val hash = Objects.hash(resolved.hash, signature.hash, results.map { it.hash })
              if (value == null || value.hash != hash || key.position != null) {
                Value(Elaborate(this@fetch, dependencies, Resolve.Result(resolved.value.module, signature.value.diagnostics), false, key.position), hash)
              } else {
                value
              }
            }

            is Key.ZonkResult      -> {
              val core = fetch(
                Key
                  .ElaborateResult(key.location)
                  .apply { position = key.position }
              )
              val hash = core.hash
              if (value == null || value.hash != hash || key.position != null) {
                Value(Zonk(this@fetch, core.value), hash)
              } else {
                value
              }
            }

            is Key.StageResult     -> {
              val location = key.location
              val zonked = fetch(Key.ZonkResult(location.module))
              val definition = zonked.value.module.definitions.find { it.name == location }!!
              val results =
                fetch(Key.ParseResult(location.module)).value!!.module.imports
                  .map { async { fetch(Key.ZonkResult(it.value)) } }
                  .plus(async { fetch(Key.ZonkResult(PRELUDE)) })
                  .awaitAll()
              val dependencies =
                results
                  .flatMap { it.value.module.definitions }
                  .plus(zonked.value.module.definitions)
                  .associateBy { it.name }
              val hash = Objects.hash(zonked.hash, results.map { it.hash })
              if (value == null || value.hash != hash) {
                Value(Stage(this@fetch, dependencies, definition), hash)
              } else {
                value
              }
            }

            is Key.LiftResult      -> {
              val staged = fetch(Key.StageResult(key.location))
              val hash = staged.hash
              if (value == null || value.hash != hash) {
                Value(staged.value.flatMap { Lift(this@fetch, it) }, hash)
              } else {
                value
              }
            }

            is Key.PackResult      -> {
              val results = key.locations.map { fetch(Key.LiftResult(it)) }
              val hash = results
                .map { it.hash }
                .hashCode()
              if (value == null || value.hash != hash) {
                val definitions = results
                  .flatMap { result ->
                    result.value.map { async { Pack(this@fetch, it) } }
                  }
                  .awaitAll()
                Value(definitions, hash)
              } else {
                value
              }
            }

            is Key.GenerateResult  -> {
              val packed = fetch(Key.PackResult(key.locations))
              val hash = packed.hash
              if (value == null || value.hash != hash) {
                val definitions = packed.value
                  .map { async { Generate(this@fetch, it) } }
                  .awaitAll()
                  .toMap()
                Value(definitions, hash)
              } else {
                value
              }
            }
          }.also {
            values[key] = it as Value<V>
          }
        }
    } as Value<V>

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

    val config =
      root
        .resolve("pack.json")
        .inputStream()
        .buffered()
        .use { Json.decodeFromStream<Config>(it) }

    fun Path.toModuleLocation(): ModuleLocation =
      ModuleLocation(
        src
          .relativize(this)
          .invariantSeparatorsPathString
          .dropLast(".mcx".length)
          .split('/')
      )

    val datapackRoot = datapacks
      .resolve(config.name)
      .also { it.createDirectories() }

    // TODO: generate dispatcher
    val outputModules = runBlocking {
      val context = Context(config)
      val diagnosticsByPath = Collections.synchronizedList(mutableListOf<Pair<Path, List<Diagnostic>>>())
      val inputs = Files
        .walk(src)
        .filter { it.extension == "mcx" }
        .map { path ->
          async {
            val zonked = context.fetch(Key.ZonkResult(path.toModuleLocation()))
            if (zonked.value.diagnostics.isNotEmpty()) {
              diagnosticsByPath += path to zonked.value.diagnostics
            }
            context.fetch(Key.GenerateResult(zonked.value.module.definitions.map { it.name })).value
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

    datapackRoot
      .resolve("pack.mcmeta")
      .outputStream()
      .buffered()
      .use {
        Json.encodeToStream(
          PackMetadata(
            pack = PackMetadataSection(
              description = config.description,
              packFormat = 10,
            )
          ),
          it,
        )
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
