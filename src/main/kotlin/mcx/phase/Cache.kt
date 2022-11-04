package mcx.phase

import mcx.ast.Core
import mcx.ast.Surface

class Cache {
  private val texts: HashMap<String, Trace<String>> = hashMapOf()
  private val surfaces: HashMap<String, Trace<Surface.Root>> = hashMapOf()
  private val cores: HashMap<String, Trace<Core.Root>> = hashMapOf()

  fun changeText(
    key: String,
    text: String,
  ) {
    texts[key] = Trace(
      text,
      true,
    )
  }

  fun closeText(
    key: String,
  ) {
    texts -= key
    surfaces -= key
    cores -= key
  }

  fun fetchText(
    key: String,
  ): Trace<String> =
    texts[key]!!

  fun fetchSurface(
    context: Context,
    key: String,
  ): Trace<Surface.Root> {
    val text = fetchText(key)
    return if (text.dirty) {
      Trace(
        Parse(
          context,
          text.value,
        ),
        true,
      ).also {
        text.dirty = false
        surfaces[key] = it
      }
    } else {
      surfaces[key]!!.also {
        it.dirty = false
      }
    }
  }

  fun fetchCore(
    context: Context,
    key: String,
  ): Trace<Core.Root> {
    val surface = fetchSurface(
      context,
      key,
    )
    return if (surface.dirty) {
      Trace(
        Elaborate(
          context,
          surface.value,
        ),
        true,
      ).also {
        surface.dirty = false
        cores[key] = it
      }
    } else {
      cores[key]!!.also {
        it.dirty = false
      }
    }
  }

  data class Trace<V>(
    val value: V,
    var dirty: Boolean,
  )
}
