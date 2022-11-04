package mcx.phase

import mcx.ast.Core
import mcx.ast.Location
import mcx.ast.Packed
import mcx.ast.Surface
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class Cache(
  private val src: Path,
) {
  private val textMap: HashMap<Location, Trace<String>> = hashMapOf()
  private val surfaceMap: HashMap<Location, Trace<Surface.Root>> = hashMapOf()
  private val coreMap: HashMap<Location, Trace<Core.Root>> = hashMapOf()
  private val packedMap: HashMap<Location, Trace<Packed.Root>> = hashMapOf()
  private val generatedMap: HashMap<Location, Trace<Unit>> = hashMapOf()

  fun changeText(
    location: Location,
    text: String,
  ) {
    textMap[location] = Trace(
      text,
      true,
    )
  }

  fun closeText(
    location: Location,
  ) {
    textMap -= location
    surfaceMap -= location
    coreMap -= location
    packedMap -= location
    generatedMap -= location
  }

  // TODO: track external modification?
  fun fetchText(
    location: Location,
  ): Trace<String>? {
    return when (val text = textMap[location]) {
      null -> {
        val path = location.toPath()
        if (path.exists()) {
          Trace(
            path.readText(),
            true,
          ).also {
            textMap[location] = it
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
    return if (text.dirty || location !in surfaceMap) {
      Trace(
        Parse(
          context,
          location,
          text.value,
        ),
        true,
      ).also {
        text.dirty = false
        surfaceMap[location] = it
      }
    } else {
      surfaceMap[location]!!.also {
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
    return if (surface.dirty || dirtyImports || location !in coreMap) {
      Trace(
        Elaborate(
          context,
          imports,
          surface.value,
        ),
        true,
      ).also {
        surface.dirty = false
        coreMap[location] = it
      }
    } else {
      coreMap[location]!!.also {
        it.dirty = false
      }
    }
  }

  fun fetchPacked(
    context: Context,
    location: Location,
  ): Trace<Packed.Root>? {
    val core = fetchCore(
      context,
      location,
    )
               ?: return null
    if (context.diagnostics.isNotEmpty()) {
      return null
    }
    return if (core.dirty || location !in packedMap) {
      Trace(
        Pack(core.value),
        true,
      ).also {
        core.dirty = false
        packedMap[location] = it
      }
    } else {
      packedMap[location]!!.also {
        it.dirty = false
      }
    }
  }

  fun fetchGenerated(
    pack: String,
    generator: Generate.Generator,
    context: Context,
    location: Location,
  ): Trace<Unit>? {
    val packed = fetchPacked(
      context,
      location,
    )
                 ?: return null
    return if (packed.dirty || location !in generatedMap) {
      Trace(
        Generate(
          pack,
          generator,
          packed.value,
        ),
        true,
      ).also {
        packed.dirty = false
        generatedMap[location] = it
      }
    } else {
      generatedMap[location]!!.also {
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
