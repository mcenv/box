package mcx.phase

import mcx.ast.Location
import mcx.ast.Packed
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class Cache(
  private val src: Path,
) {
  private val texts: HashMap<Location, Trace<String>> = hashMapOf()
  private val parseResults: HashMap<Location, Trace<Parse.Result>> = hashMapOf()
  private val elaborateResults: HashMap<Location, Trace<Elaborate.Result>> = hashMapOf()
  private val packResults: HashMap<Location, Trace<Packed.Root>> = hashMapOf()
  private val generateResults: HashMap<Location, Trace<Unit>> = hashMapOf()

  fun changeText(
    location: Location,
    text: String,
  ) {
    texts[location] = Trace(
      text,
      true,
    )
  }

  fun closeText(location: Location) {
    texts -= location
    parseResults -= location
    elaborateResults -= location
    packResults -= location
    generateResults -= location
  }

  // TODO: track external modification?
  fun fetchText(location: Location): Trace<String>? {
    return when (val text = texts[location]) {
      null -> {
        val path = location.toPath()
        if (path.exists()) {
          Trace(
            path.readText(),
            true,
          ).also {
            texts[location] = it
          }
        } else {
          null
        }
      }
      else -> text
    }
  }

  fun fetchSurface(location: Location): Trace<Parse.Result>? {
    val text = fetchText(location)
               ?: return null
    return if (text.dirty || location !in parseResults) {
      Trace(
        Parse(
          location,
          text.value,
        ),
        true,
      ).also {
        text.dirty = false
        parseResults[location] = it
      }
    } else {
      parseResults[location]!!.also {
        it.dirty = false
      }
    }
  }

  fun fetchCore(location: Location): Trace<Elaborate.Result>? {
    val surface = fetchSurface(location)
                  ?: return null
    var dirtyImports = false
    val imports = surface.value.root.imports.map {
      val import = fetchCore(it.value)
      dirtyImports = dirtyImports or (import?.dirty
                                      ?: false)
      it to import?.value?.root
    }
    return if (surface.dirty || dirtyImports || location !in elaborateResults) {
      Trace(
        Elaborate(
          imports,
          surface.value,
        ),
        true,
      ).also {
        surface.dirty = false
        elaborateResults[location] = it
      }
    } else {
      elaborateResults[location]!!.also {
        it.dirty = false
      }
    }
  }

  fun fetchPacked(location: Location): Trace<Packed.Root>? {
    val core = fetchCore(location)
               ?: return null
    if (core.value.diagnostics.isNotEmpty()) {
      return null
    }
    return if (core.dirty || location !in packResults) {
      Trace(
        Pack(core.value.root),
        true,
      ).also {
        core.dirty = false
        packResults[location] = it
      }
    } else {
      packResults[location]!!.also {
        it.dirty = false
      }
    }
  }

  fun fetchGenerated(
    pack: String,
    generator: Generate.Generator,
    location: Location,
  ): Trace<Unit>? {
    val packed = fetchPacked(location)
                 ?: return null
    return if (packed.dirty || location !in generateResults) {
      Trace(
        Generate(
          pack,
          generator,
          packed.value,
        ),
        true,
      ).also {
        packed.dirty = false
        generateResults[location] = it
      }
    } else {
      generateResults[location]!!.also {
        it.dirty = false
      }
    }
  }

  private fun Location.toPath(): Path =
    src.resolve("${parts.joinToString("/")}.mcx")

  data class Trace<V>(
    val value: V,
    var dirty: Boolean,
  )
}
