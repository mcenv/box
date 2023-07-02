package mcx.pass.build

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import mcx.ast.common.DefinitionLocation
import mcx.ast.common.Modifier
import mcx.ast.common.ModuleLocation
import mcx.data.DATA_PACK_FORMAT
import mcx.data.PackMetadata
import mcx.data.PackMetadataSection
import mcx.pass.Config
import mcx.pass.Context
import mcx.pass.frontend.Read
import mcx.pass.frontend.Resolve
import mcx.pass.frontend.elaborate.Elaborate
import mcx.pass.frontend.parse.Parse
import mcx.pass.prelude
import mcx.util.debug
import mcx.util.decodeFromJson
import mcx.util.encodeToJson
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*

// TODO: redesign
class Build(
  private val root: Path,
  core: Path? = null,
) {
  private val mcx: Path = root / ".mcx"
  private val core: Path? = core?.resolve("src") ?: run {
    val uri = Build::class.java.getResource("/core/src")!!.toURI()
    FileSystems.newFileSystem(uri, emptyMap<String, Nothing>())
    Paths.get(uri)
  }
  private val src: Path = (root / "src").createDirectories()
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

  suspend fun closeText(
    location: ModuleLocation,
  ) {
    suspend fun close(key: Key<*>) {
      mutexes[key]?.withLock {
        traces -= key
        mutexes -= key
      }
    }

    close(Key.Read(location))
    close(Key.Parsed(location))
    close(Key.Resolved(location))
    close(Key.Elaborated(location))
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
                Trace(Read(this@fetch, core, src, key.location), 0)
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

            /*
            is Key.Staged     -> {
              val location = key.location
              val elaborated = fetch(Key.Elaborated(location.module))
              val definition = elaborated.value.module.definitions[location] ?: error("Unknown definition: $location")
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
              val locations = Key.Packed.locations.flatMapTo(mutableSetOf()) {
                transitiveImports(it.module) + it
              }
              val results = locations.map {
                async { fetch(Key.Lifted(it)) }
              }.awaitAll()
              val hash = results.map { it.hash }.hashCode()
              if (trace == null || trace.hash != hash) {
                val definitions = results
                  .flatMap { result -> result.value?.liftedDefinitions?.map { async { Pack(this@fetch, it) } } ?: emptyList() }
                  .plus(async { Pack.packInit() })
                  .plus(async { Pack.packDispatchProcs(results.flatMap { result -> result.value?.dispatchedProcs ?: emptyList() }) })
                  .plus(async { Pack.packDispatchFuncs(results.flatMap { result -> result.value?.dispatchedFuncs ?: emptyList() }) })
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
             */
          }.also {
            traces[key] = it as Trace<V>
          }
        }
    } as Trace<V>
  }

  data class Result(
    val success: Boolean,
    val diagnosticsByPath: List<Pair<Path, List<Diagnostic>>>,
    val tests: List<DefinitionLocation>,
  )

  // TODO: skip build if no changes
  @OptIn(ExperimentalSerializationApi::class, ExperimentalPathApi::class)
  suspend operator fun invoke(): Result {
    val config = (root / "pack.json").decodeFromJson<Config>()
    return coroutineScope {
      val context = Context(config)
      val success = AtomicBoolean(true)
      val diagnosticsByPath = Collections.synchronizedList(mutableListOf<Pair<Path, List<Diagnostic>>>())
      val elaboratedDefinitions = src
        .walk()
        .filter { it.extension == "mcx" }
        .map { path ->
          async {
            val location = ModuleLocation(listOf(context.config.name) + src.relativize(path).invariantSeparatorsPathString.dropLast(".mcx".length).split('/'))
            val elaborated = context.fetch(Key.Elaborated(location))
            val diagnostics = elaborated.value.diagnostics

            diagnostics[null]?.let { moduleDiagnostics ->
              if (success.get() && moduleDiagnostics.any { it.severity == DiagnosticSeverity.Error }) {
                success.set(false)
              }
              diagnosticsByPath += path to moduleDiagnostics
            }

            elaborated.value.module.definitions.values.mapNotNull { definition ->
              if (Modifier.ERROR in definition.modifiers) {
                diagnostics[definition.name]?.takeIf { it.isNotEmpty() }?.let { definitionDiagnostics ->
                  if (success.get() && definitionDiagnostics.none { it.severity == DiagnosticSeverity.Error }) {
                    success.set(false)
                  }
                } ?: success.set(false)
                null
              } else {
                diagnostics[definition.name]?.let { definitionDiagnostics ->
                  if (success.get() && definitionDiagnostics.any { it.severity == DiagnosticSeverity.Error }) {
                    success.set(false)
                  }
                  diagnosticsByPath += path to definitionDiagnostics
                }
                definition.name to definition
              }
            }
          }
        }
        .toList()
        .awaitAll()
        .flatten()
        .toMap()

      if (!success.get()) {
        return@coroutineScope Result(false, diagnosticsByPath, emptyList())
      }

      if (config.output == Config.Output.NONE) {
        return@coroutineScope Result(true, diagnosticsByPath, emptyList())
      }

      val datapacks = (mcx / config.properties.levelName / "datapacks").createDirectories()

      fun generateDatapack(suffix: String, definitions: Map<String, String>) {
        val datapackPath = (datapacks / "${config.name}_$suffix.zip")
        val datapackRoot = (datapacks / "${config.name}_$suffix")

        when (config.output) {
          Config.Output.PATH -> {
            datapackPath.deleteIfExists()
            datapackRoot.createDirectories()

            val outputModules = definitions
              .mapKeys { (name, _) -> datapackRoot / name }
              .onEach { (path, definition) ->
                path.createParentDirectories().bufferedWriter().use {
                  if (config.debug.verbose) {
                    debug("Writing", path.pathString)
                  }
                  it.write(definition)
                }
              }
            datapackRoot.visitFileTree {
              onVisitFile { file, _ ->
                if (file !in outputModules) {
                  if (config.debug.verbose) {
                    debug("Deleting", file.pathString)
                  }
                  file.deleteExisting()
                }
                FileVisitResult.CONTINUE
              }
            }

            (datapackRoot / "pack.mcmeta").encodeToJson(PackMetadata(pack = PackMetadataSection(description = config.description, packFormat = DATA_PACK_FORMAT)))
          }
          Config.Output.FILE -> {
            datapackRoot.deleteRecursively()

            ZipOutputStream(datapackPath.outputStream().buffered()).use { output ->
              output.putNextEntry(ZipEntry("pack.mcmeta"))
              Json.encodeToStream(PackMetadata(pack = PackMetadataSection(description = config.description, packFormat = DATA_PACK_FORMAT)), output)

              definitions.onEach { (name, definition) ->
                if (config.debug.verbose) {
                  debug("Writing", name)
                }
                output.putNextEntry(ZipEntry(name))
                output.write(definition.encodeToByteArray())
              }
            }
          }
          Config.Output.NONE -> {
            error("Unreachable")
          }
        }
      }

      /*
      val packs = context.fetch(Key.Generated.apply { Key.Generated.locations = elaboratedDefinitions }).value
      generateDatapack("main", packs.main)
      generateDatapack("test", packs.test)
       */

      Result(true, diagnosticsByPath, emptyList() /* TODO */)
    }
  }
}
