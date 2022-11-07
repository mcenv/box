package mcx.phase

import kotlinx.coroutines.*
import mcx.ast.Location
import mcx.ast.Packed
import org.eclipse.lsp4j.Position
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.io.path.exists
import kotlin.io.path.readText

// TODO
class Cache(
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
        val path = location.toPath()
        if (path.exists()) {
          path
            .readText()
            .also {
              texts[location] = it
            }
        } else {
          null
        }
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
    position: Position? = null,
  ): Elaborate.Result? =
    coroutineScope {
      val surface = fetchSurface(
        config,
        location,
      )
                    ?: return@coroutineScope null
      val imports =
        surface.root.imports
          .map {
            async {
              val import = fetchCore(
                config,
                it.value,
              )
              it to import?.root
            }
          }
          .awaitAll()
      Elaborate(
        config,
        imports,
        surface,
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

  private fun Location.toPath(): Path =
    src.resolve("${parts.joinToString("/")}.mcx")
}
