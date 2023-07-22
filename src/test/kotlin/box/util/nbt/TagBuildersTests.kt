package box.util.nbt

import kotlin.test.Test
import kotlin.test.assertEquals

object TagBuildersTests {
  @Test
  fun `compound empty`() {
    assertEquals(
      CompoundTag(emptyMap()),
      buildCompoundTag { }
    )
  }

  @Test
  fun `compound values`() {
    assertEquals(
      CompoundTag(mapOf(
        "a" to StringTag(""),
        "b" to ByteTag(0),
        "c" to ShortTag(0),
        "d" to IntTag(0),
        "e" to LongTag(0),
        "f" to FloatTag(0f),
        "g" to DoubleTag(0.0),
      )),
      buildCompoundTag {
        put("a", "")
        put("b", 0.toByte())
        put("c", 0.toShort())
        put("d", 0)
        put("e", 0L)
        put("f", 0f)
        put("g", 0.0)
      }
    )
  }

  @Test
  fun `compound nested`() {
    assertEquals(
      CompoundTag(mapOf(
        "a" to CompoundTag(emptyMap())
      )),
      buildCompoundTag {
        putCompoundTag("a") {}
      }
    )
  }

  @Test
  fun `list empty`() {
    assertEquals(
      ListTag(emptyList()),
      buildListTag { }
    )
  }

  @Test
  fun `list values`() {
    assertEquals(
      ListTag(listOf(
        StringTag("a"),
        StringTag("b"),
      )),
      buildListTag {
        add(StringTag("a"))
        add(StringTag("b"))
      }
    )
  }
}
