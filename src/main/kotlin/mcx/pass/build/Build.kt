package mcx.pass.build

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import mcx.ast.Packed
import mcx.ast.common.DefinitionLocation
import mcx.ast.common.Modifier
import mcx.ast.common.ModuleLocation
import mcx.data.DATA_PACK_FORMAT
import mcx.data.PackMetadata
import mcx.data.PackMetadataSection
import mcx.pass.Config
import mcx.pass.Context
import mcx.pass.backend.Generate
import mcx.pass.backend.Lift
import mcx.pass.backend.Pack
import mcx.pass.backend.Stage
import mcx.pass.frontend.Read
import mcx.pass.frontend.Resolve
import mcx.pass.frontend.elaborate.Elaborate
import mcx.pass.frontend.parse.Parse
import mcx.pass.prelude
import mcx.util.debug
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
          }.also {
            traces[key] = it as Trace<V>
          }
        }
    } as Trace<V>
  }

  private suspend fun Context.transitiveImports(location: ModuleLocation): List<DefinitionLocation> {
    val imports = mutableSetOf<DefinitionLocation>()
    suspend fun go(location: ModuleLocation) {
      fetch(Key.Parsed(location)).value.module.imports.forEach { (import) ->
        imports += import
        go(import.module)
      }
    }
    go(location)
    return imports.toList()
  }

  private fun Path.toModuleLocation(packName: String): ModuleLocation {
    return ModuleLocation(listOf(packName) + src.relativize(this).invariantSeparatorsPathString.dropLast(".mcx".length).split('/'))
  }

  data class Result(
    val success: Boolean,
    val diagnosticsByPath: List<Pair<Path, List<Diagnostic>>>,
    val tests: List<DefinitionLocation>,
  )

  // TODO: skip build if no changes
  @OptIn(ExperimentalSerializationApi::class, ExperimentalPathApi::class)
  suspend operator fun invoke(): Result {
    val config = (root / "pack.json").inputStream().buffered().use { Json.decodeFromStream<Config>(it) }
    val serverProperties = config.properties
    val levelName = serverProperties.levelName
    val datapacks = (mcx / levelName / "datapacks").also { it.createDirectories() }

    return coroutineScope {
      val context = Context(config)
      val success = AtomicBoolean(true)
      val diagnosticsByPath = Collections.synchronizedList(mutableListOf<Pair<Path, List<Diagnostic>>>())
      val tests = mutableListOf<DefinitionLocation>()
      val locations = src
        .walk()
        .filter { it.extension == "mcx" }
        .map { path ->
          async {
            val elaborated = context.fetch(Key.Elaborated(path.toModuleLocation(context.config.name)))
            val diagnostics = elaborated.value.diagnostics

            diagnostics[null]?.let { topLevelDiagnostics ->
              if (success.get() && topLevelDiagnostics.any { it.severity == DiagnosticSeverity.Error }) {
                success.set(false)
              }
              diagnosticsByPath += path to topLevelDiagnostics
            }

            elaborated.value.module.definitions.values.mapNotNull { definition ->
              if (Modifier.TEST in definition.modifiers) {
                tests += definition.name
              }
              if (Modifier.ERROR in definition.modifiers) {
                diagnostics[definition.name]?.takeIf { it.isNotEmpty() }?.let { diagnostics ->
                  if (success.get() && diagnostics.none { it.severity == DiagnosticSeverity.Error }) {
                    success.set(false)
                  }
                  diagnosticsByPath += path to diagnostics
                } ?: success.set(false)
                null
              } else {
                diagnostics[definition.name]?.let { diagnostics ->
                  if (success.get() && diagnostics.any { it.severity == DiagnosticSeverity.Error }) {
                    success.set(false)
                  }
                  diagnosticsByPath += path to diagnostics
                }
                definition.name
              }
            }
          }
        }
        .toList()
        .awaitAll()
        .flatten()

      if (!success.get()) {
        return@coroutineScope Result(false, diagnosticsByPath, tests)
      }

      if (config.output != Config.Output.NONE) {
        val verbose = config.debug.verbose
        fun generate(suffix: String, definitions: Map<String, String>) {
          datapacks.createDirectories()
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
                    if (verbose) {
                      debug("Writing", path.pathString)
                    }
                    it.write(definition)
                  }
                }
              datapackRoot.visitFileTree {
                onVisitFile { file, _ ->
                  if (file !in outputModules) {
                    if (verbose) {
                      debug("Deleting", file.pathString)
                    }
                    file.deleteExisting()
                  }
                  FileVisitResult.CONTINUE
                }
              }

              (datapackRoot / "pack.mcmeta").outputStream().buffered().use {
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
            Config.Output.FILE -> {
              datapackRoot.deleteRecursively()

              ZipOutputStream(datapackPath.outputStream().buffered()).use { output ->
                output.putNextEntry(ZipEntry("pack.mcmeta"))
                Json.encodeToStream(PackMetadata(pack = PackMetadataSection(description = config.description, packFormat = DATA_PACK_FORMAT)), output)

                definitions.onEach { (name, definition) ->
                  if (verbose) {
                    debug("Writing", name)
                  }
                  output.putNextEntry(ZipEntry(name))
                  output.write(definition.encodeToByteArray())
                }
              }
            }
            Config.Output.NONE -> error("Unreachable")
          }
        }

        val packs = context.fetch(Key.Generated.apply { Key.Generated.locations = locations }).value
        generate("main", packs.main)
        generate("test", packs.test)
      }

      Result(true, diagnosticsByPath, tests)
    }
  }
}
