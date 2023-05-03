package mcx.pass.build

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import mcx.ast.DefinitionLocation
import mcx.ast.ModuleLocation
import mcx.ast.Packed
import mcx.data.DATA_PACK_FORMAT
import mcx.data.PackMetadata
import mcx.data.PackMetadataSection
import mcx.pass.Config
import mcx.pass.Context
import mcx.pass.backend.Generate
import mcx.pass.backend.Lift
import mcx.pass.backend.Pack
import mcx.pass.backend.Stage
import mcx.pass.frontend.Elaborate
import mcx.pass.frontend.Parse
import mcx.pass.frontend.Resolve
import mcx.pass.prelude
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import java.nio.file.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.*

// TODO: redesign
class Build(
  private val root: Path,
  std: Path? = null,
) {
  private val std: Path? = std?.resolve("src") ?: run {
    val uri = Build::class.java.getResource("/std/src")!!.toURI()
    FileSystems.newFileSystem(uri, emptyMap<String, Nothing>())
    Paths.get(uri)
  }
  private val src: Path = root.resolve("src").also { it.createDirectories() }
  private val traces: ConcurrentMap<Key<*>, Trace<*>> = ConcurrentHashMap()
  private val mutexes: ConcurrentMap<Key<*>, Mutex> = ConcurrentHashMap()

  suspend fun changeText(
    location: ModuleLocation,
    text: String,
  ) {
    val key = Key.Read(location)
    mutexes
      .computeIfAbsent(key) { Mutex() }
      .withLock { traces[key] = Trace(text, 0) }
  }

  suspend fun Context.closeText(
    location: ModuleLocation,
  ) {
    suspend fun close(key: Key<*>) {
      mutexes[key]?.withLock {
        traces -= key
        mutexes -= key
      }
    }

    val definitions = fetch(Key.Resolved(location)).value.module.definitions
    return coroutineScope {
      close(Key.Read(location))
      close(Key.Parsed(location))
      close(Key.Resolved(location))
      close(Key.Elaborated(location))
      definitions.forEach { (location, _) ->
        close(Key.Lifted(location))
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  suspend fun <V> Context.fetch(key: Key<V>): Trace<V> {
    return coroutineScope {
      mutexes
        .computeIfAbsent(key) { Mutex() }
        .withLock {
          val trace = traces[key]
          when (key) {
            is Key.Read       -> {
              trace ?: withContext(Dispatchers.IO) {
                src.pathOf(key.location)?.takeIf { it.isRegularFile() }?.let {
                  return@withContext Trace(it.readText(), 0)
                }
                std?.pathOf(key.location)?.takeIf { it.exists() }?.let {
                  return@withContext Trace(it.readText(), 0)
                }
                Trace("", 0)
              }
            }

            is Key.Parsed     -> {
              val read = fetch(Key.Read(key.location))
              val hash = read.value.hashCode()
              if (trace == null || trace.hash != hash) {
                Trace(Parse(this@fetch, key.location, read.value), hash)
              } else {
                trace
              }
            }

            is Key.Resolved   -> {
              val location = key.location
              val surface = fetch(Key.Parsed(location))
              val dependencyHashes = surface.value.module.imports
                .map { async { fetch(Key.Resolved(it.value.module)) } }
                .plus(async { if (location == prelude) null else fetch(Key.Resolved(prelude)) })
                .awaitAll()
                .filterNotNull()
                .map { it.hash }
              val dependencies = surface.value.module.imports
                .map { async { Resolve.Dependency(it.value, fetch(Key.Resolved(it.value.module)).value.module.definitions[it.value], it.range) } }
                .plus(
                  if (location == prelude) emptyList() else fetch(Key.Resolved(prelude)).value.module.definitions.map {
                    async { Resolve.Dependency(it.key, it.value, null) }
                  }
                )
                .awaitAll()
              val hash = Objects.hash(surface.hash, dependencyHashes)
              if (trace == null || trace.hash != hash || key.instruction != null) {
                Trace(Resolve(this@fetch, dependencies, surface.value, key.instruction), hash)
              } else {
                trace
              }
            }

            is Key.Elaborated -> {
              val location = key.location
              val resolved = fetch(Key.Resolved(location))
              val results = resolved.value.module.imports
                .map { async { fetch(Key.Elaborated(it.value.module)) } }
                .plus(async { if (location == prelude) null else fetch(Key.Elaborated(prelude)) })
                .awaitAll()
                .filterNotNull()
              val dependencies = results.map { it.value.module }
              val hash = Objects.hash(resolved.hash, results.map { it.hash })
              if (trace == null || trace.hash != hash || key.instruction != null) {
                Trace(Elaborate(this@fetch, dependencies, resolved.value, key.instruction), hash)
              } else {
                trace
              }
            }

            is Key.Staged     -> {
              val location = key.location
              val elaborated = fetch(Key.Elaborated(location.module))
              val definition = elaborated.value.module.definitions.find { it.name == location }!!
              val results = transitiveImports(fetch(Key.Parsed(location.module)).value.module.name)
                .map { async { fetch(Key.Elaborated(it.module)) } }
                .plus(async { fetch(Key.Elaborated(prelude)) })
                .awaitAll()
              val hash = Objects.hash(elaborated.hash, results.map { it.hash })
              if (trace == null || trace.hash != hash) {
                Trace(Stage(this@fetch, definition), hash)
              } else {
                trace
              }
            }

            is Key.Lifted     -> {
              val staged = fetch(Key.Staged(key.location))
              val hash = staged.hash
              if (trace == null || trace.hash != hash) {
                Trace(staged.value?.let { Lift(this@fetch, it) }, hash)
              } else {
                trace
              }
            }

            is Key.Packed     -> {
              val results = Key.Packed.locations.map { fetch(Key.Lifted(it)) }
              val hash = results.map { it.hash }.hashCode()
              if (trace == null || trace.hash != hash) {
                val definitions = results
                  .flatMap { result -> result.value?.liftedDefinitions?.map { async { Pack(this@fetch, it) } } ?: emptyList() }
                  .plus(async { Pack.packDispatch(results.flatMap { result -> result.value?.dispatchedDefinitions ?: emptyList() }) })
                  .awaitAll()
                Trace(definitions, hash)
              } else {
                trace
              }
            }

            is Key.Generated  -> {
              val packed = fetch(Key.Packed.apply { locations = Key.Generated.locations })
              val hash = packed.hash
              if (trace == null || trace.hash != hash) {
                val (test, main) = packed.value.partition { it.modifiers.contains(Packed.Modifier.TEST) }
                val mainDefinitions = main
                  .map { async { Generate(this@fetch, it) } }
                  .awaitAll()
                  .toMap()
                val testDefinitions = test
                  .map { async { Generate(this@fetch, it) } }
                  .awaitAll()
                  .toMap()
                Trace(Key.Generated.Packs(mainDefinitions, testDefinitions), hash)
              } else {
                trace
              }
            }
          }.also {
            traces[key] = it as Trace<V>
          }
        }
    } as Trace<V>
  }

  private suspend fun Context.transitiveImports(location: ModuleLocation): List<DefinitionLocation> {
    val imports = mutableListOf<DefinitionLocation>()
    suspend fun go(location: ModuleLocation) {
      fetch(Key.Parsed(location)).value.module.imports.forEach { (import) ->
        imports += import
        go(import.module)
      }
    }
    go(location)
    return imports
  }

  private fun Path.pathOf(location: ModuleLocation): Path? {
    return try {
      resolve(location.parts.joinToString("/", postfix = EXTENSION))
    } catch (_: InvalidPathException) {
      null
    }
  }

  private fun Path.toModuleLocation(): ModuleLocation {
    return ModuleLocation(src.relativize(this).invariantSeparatorsPathString.dropLast(EXTENSION.length).split('/'))
  }

  data class Result(
    val success: Boolean,
    val diagnosticsByPath: List<Pair<Path, List<Diagnostic>>>,
  )

  @OptIn(ExperimentalSerializationApi::class, ExperimentalPathApi::class)
  suspend operator fun invoke(): Result {
    val serverProperties = Properties().apply {
      load(root.resolve("server.properties").inputStream().buffered())
    }
    val levelName = serverProperties.getProperty("level-name")
    val datapacks = root.resolve(levelName).resolve("datapacks").also { it.createDirectories() }

    val config = root.resolve("pack.json").inputStream().buffered().use { Json.decodeFromStream<Config>(it) }

    return coroutineScope {
      val context = Context(config)
      val success = AtomicBoolean(true)
      val diagnosticsByPath = Collections.synchronizedList(mutableListOf<Pair<Path, List<Diagnostic>>>())
      val locations = src
        .walk()
        .filter { it.extension == "mcx" }
        .map { path ->
          async {
            val elaborated = context.fetch(Key.Elaborated(path.toModuleLocation()))
            if (elaborated.value.diagnostics.isNotEmpty()) {
              diagnosticsByPath += path to elaborated.value.diagnostics.also { diagnostics ->
                if (success.get() && diagnostics.any { it.severity == DiagnosticSeverity.Error }) {
                  success.set(false)
                }
              }
            }
            elaborated.value.module.definitions.map { it.name }
          }
        }
        .toList()
        .awaitAll()
        .flatten()

      if (!success.get()) {
        return@coroutineScope Result(false, diagnosticsByPath)
      }

      fun generate(suffix: String, definitions: Map<String, String>) {
        val datapackRoot = datapacks.resolve("${config.name}_$suffix").also { it.createDirectories() }
        val outputModules = definitions
          .mapKeys { (name, _) -> datapackRoot.resolve(name) }
          .onEach { (name, definition) ->
            name
              .also { it.parent.createDirectories() }
              .bufferedWriter()
              .use {
                if (config.debug) {
                  println("writing: $name")
                }
                it.write(definition)
              }
          }
        datapackRoot.visitFileTree {
          onVisitFile { file, _ ->
            if (file !in outputModules) {
              if (config.debug) {
                println("deleting: $file")
              }
              file.deleteExisting()
            }
            FileVisitResult.CONTINUE
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
                  packFormat = DATA_PACK_FORMAT,
                )
              ),
              it,
            )
          }
      }

      val packs =  context.fetch(Key.Generated.apply { Key.Generated.locations = locations }).value
      generate("main", packs.main)
      generate("test", packs.test)

      Result(true, diagnosticsByPath)
    }
  }

  companion object {
    const val EXTENSION: String = ".mcx"
  }
}
