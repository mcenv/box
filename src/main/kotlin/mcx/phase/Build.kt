package mcx.phase

import kotlinx.coroutines.*
import mcx.ast.Location
import mcx.ast.Packed
import org.eclipse.lsp4j.Position
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.io.path.exists
import kotlin.io.path.readText

// TODO
class Build(
  private val src: Path,
) {
  private val texts: ConcurrentMap<Location, String> = ConcurrentHashMap()
  private val parseResults: ConcurrentMap<Location, Parse.Result> = ConcurrentHashMap()

  fun changeText(
    location: Location,
    text: String,
  ) {
    closeText(location)
    texts[location] = text
  }

  fun closeText(
    location: Location,
  ) {
    texts -= location
    parseResults -= location
  }

  // TODO: track external modification?
  suspend fun fetchText(location: Location): String? {
    return when (val text = texts[location]) {
      null -> withContext(Dispatchers.IO) {
        src
          .resolve("$location.mcx")
          .let { path ->
            if (path.exists()) {
              return@withContext path
                .readText()
                .also { texts[location] = it }
            }
          }
        STD_SRC
          .resolve("$location.mcx")
          .let { path ->
            if (path.exists()) {
              return@withContext path
                .readText()
                .also { texts[location] = it }
            }
          }
        null
      }
      else -> text
    }
  }

  suspend fun fetchSurface(
    config: Config,
    location: Location,
  ): Parse.Result? {
    return when (val parseResult = parseResults[location]) {
      null -> {
        val text = fetchText(location)
                   ?: return null
        Parse(
          config,
          location,
          text,
        ).also { parseResults[location] = it }
      }
      else -> parseResult
    }
  }

  suspend fun fetchCore(
    config: Config,
    location: Location,
    signature: Boolean,
    position: Position? = null,
  ): Elaborate.Result? =
    coroutineScope {
      val surface = fetchSurface(
        config,
        location,
      )
                    ?: return@coroutineScope null
      val dependencies =
        if (signature) {
          emptyList()
        } else {
          surface.root.imports
            .map {
              async {
                val dependency = fetchCore(
                  config,
                  it.value,
                  true,
                )
                Elaborate.Dependency(
                  it.value,
                  dependency?.root,
                  it.range,
                )
              }
            }
            .plus(
              async {
                val prelude = fetchCore(
                  config,
                  PRELUDE,
                  true,
                )!!
                Elaborate.Dependency(
                  PRELUDE,
                  prelude.root,
                  null,
                )
              }
            )
            .plus(
              async {
                val self = fetchCore(
                  config,
                  location,
                  true,
                )!!
                Elaborate.Dependency(
                  location,
                  self.root,
                  null,
                )
              }
            )
            .awaitAll()
        }
      Elaborate(
        config,
        dependencies,
        surface,
        signature,
        position,
      )
    }

  suspend fun fetchPacked(
    config: Config,
    location: Location,
  ): Packed.Root? {
    val core = fetchCore(
      config,
      location,
      false,
    )
               ?: return null
    if (core.diagnostics.isNotEmpty()) {
      return null
    }
    return Pack(
      config,
      core.root,
    )
  }

  suspend fun fetchGenerated(
    config: Config,
    generator: Generate.Generator,
    location: Location,
  ) {
    val packed = fetchPacked(
      config,
      location,
    )
                 ?: return
    Generate(
      config,
      generator,
      packed,
    )
  }

  companion object {
    private val STD_SRC: Path
    private val PRELUDE: Location = Location("prelude")

    init {
      val uri =
        Build::class.java
          .getResource("/std/src")!!
          .toURI()
      FileSystems.newFileSystem(
        uri,
        emptyMap<String, Nothing>(),
      )
      STD_SRC = Paths.get(uri)
    }
  }
}
