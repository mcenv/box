package mcx.phase

import mcx.ast.Core
import mcx.ast.Location
import mcx.ast.Surface
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class Cache(
  private val src: Path,
) {
  private val texts: HashMap<Location, Trace<String>> = hashMapOf()
  private val surfaces: HashMap<Location, Trace<Surface.Root>> = hashMapOf()
  private val cores: HashMap<Location, Trace<Core.Root>> = hashMapOf()

  fun changeText(
    location: Location,
    text: String,
  ) {
    texts[location] = Trace(
      text,
      true,
    )
  }

  fun closeText(
    location: Location,
  ) {
    texts -= location
    surfaces -= location
    cores -= location
  }

  // TODO: track external modification?
  fun fetchText(
    location: Location,
  ): Trace<String>? {
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

  fun fetchSurface(
    context: Context,
    location: Location,
  ): Trace<Surface.Root>? {
    val text = fetchText(location)
               ?: return null
    return if (text.dirty) {
      Trace(
        Parse(
          context,
          location,
          text.value,
        ),
        true,
      ).also {
        text.dirty = false
        surfaces[location] = it
      }
    } else {
      surfaces[location]!!.also {
        it.dirty = false
      }
    }
  }

  fun fetchCore(
    context: Context,
    location: Location,
  ): Trace<Core.Root>? {
    val surface = fetchSurface(
      context,
      location,
    )
                  ?: return null
    var dirtyImports = false
    val imports = surface.value.imports.map {
      val import = fetchCore(
        Context(),
        it.value,
      )
      dirtyImports = dirtyImports or (import?.dirty
                                      ?: false)
      it to import?.value
    }
    return if (surface.dirty) {
      Trace(
        Elaborate(
          context,
          imports,
          surface.value,
        ),
        true,
      ).also {
        surface.dirty = false
        cores[location] = it
      }
    } else {
      cores[location]!!.also {
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
