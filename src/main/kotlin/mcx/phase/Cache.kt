package mcx.phase

import mcx.ast.Core
import mcx.ast.Surface

class Cache {
  private val texts: HashMap<String, String> = hashMapOf()
  private val surfaces: HashMap<String, Hashed<Surface.Root>> = hashMapOf()
  private val cores: HashMap<String, Hashed<Core.Root>> = hashMapOf()

  fun fetchText(
    name: String,
  ): String =
    texts[name]!!

  fun fetchSurface(
    context: Context,
    name: String,
  ): Surface.Root {
    val surface = surfaces[name]
    val text = fetchText(name)
    val inputHash = text.hashCode()
    return if (surface == null || inputHash != surface.inputHash) {
      Parse(
        context,
        text,
      ).also {
        surfaces[name] = Hashed(
          it,
          inputHash,
        )
      }
    } else {
      surface.value
    }
  }

  fun fetchCore(
    context: Context,
    name: String,
  ): Core.Root {
    val core = cores[name]
    val surface = fetchSurface(
      context,
      name,
    )
    val inputHash = surface.hashCode()
    return if (core == null || inputHash != core.inputHash) {
      Elaborate(
        context,
        surface,
      ).also {
        cores[name] = Hashed(
          it,
          inputHash,
        )
      }
    } else {
      core.value
    }
  }

  fun modifyDocument(
    name: String,
    text: String,
  ) {
    texts[name] = text
  }

  fun closeDocument(
    name: String,
  ) {
    texts -= name
    surfaces -= name
    cores -= name
  }

  data class Hashed<T>(
    val value: T,
    val inputHash: Int,
  )
}
