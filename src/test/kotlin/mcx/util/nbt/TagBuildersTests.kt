package mcx.util.nbt

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
  fun `list string empty`() {
    assertEquals(
      StringListTag(emptyList()),
      buildStringListTag { }
    )
  }

  @Test
  fun `list string values`() {
    assertEquals(
      StringListTag(listOf(
        StringTag("a"),
        StringTag("b"),
      )),
      buildStringListTag {
        add("a")
        add("b")
      }
    )
  }

  @Test
  fun `list compound empty`() {
    assertEquals(
      CompoundListTag(emptyList()),
      buildCompoundListTag { }
    )
  }

  @Test
  fun `list compound values`() {
    assertEquals(
      CompoundListTag(listOf(
        CompoundTag(emptyMap()),
        CompoundTag(emptyMap()),
      )),
      buildCompoundListTag {
        addCompoundTag { }
        addCompoundTag { }
      }
    )
  }

  @Test
  fun `list byte empty`() {
    assertEquals(
      ByteListTag(emptyList()),
      buildByteListTag { }
    )
  }

  @Test
  fun `list byte values`() {
    assertEquals(
      ByteListTag(listOf(
        ByteTag(0),
        ByteTag(1),
      )),
      buildByteListTag {
        add(0)
        add(1)
      }
    )
  }

  @Test
  fun `list short empty`() {
    assertEquals(
      ShortListTag(emptyList()),
      buildShortListTag { }
    )
  }

  @Test
  fun `list short values`() {
    assertEquals(
      ShortListTag(listOf(
        ShortTag(0),
        ShortTag(1),
      )),
      buildShortListTag {
        add(0)
        add(1)
      }
    )
  }

  @Test
  fun `list int empty`() {
    assertEquals(
      IntListTag(emptyList()),
      buildIntListTag { }
    )
  }

  @Test
  fun `list int values`() {
    assertEquals(
      IntListTag(listOf(
        IntTag(0),
        IntTag(1),
      )),
      buildIntListTag {
        add(0)
        add(1)
      }
    )
  }

  @Test
  fun `list long empty`() {
    assertEquals(
      LongListTag(emptyList()),
      buildLongListTag { }
    )
  }

  @Test
  fun `list long values`() {
    assertEquals(
      LongListTag(listOf(
        LongTag(0),
        LongTag(1),
      )),
      buildLongListTag {
        add(0)
        add(1)
      }
    )
  }

  @Test
  fun `list float empty`() {
    assertEquals(
      FloatListTag(emptyList()),
      buildFloatListTag { }
    )
  }

  @Test
  fun `list float values`() {
    assertEquals(
      FloatListTag(listOf(
        FloatTag(0f),
        FloatTag(1f),
      )),
      buildFloatListTag {
        add(0f)
        add(1f)
      }
    )
  }

  @Test
  fun `list double empty`() {
    assertEquals(
      DoubleListTag(emptyList()),
      buildDoubleListTag { }
    )
  }

  @Test
  fun `list double values`() {
    assertEquals(
      DoubleListTag(listOf(
        DoubleTag(0.0),
        DoubleTag(1.0),
      )),
      buildDoubleListTag {
        add(0.0)
        add(1.0)
      }
    )
  }

  @Test
  fun `list byte_array empty`() {
    assertEquals(
      ByteArrayListTag(emptyList()),
      buildByteArrayListTag { }
    )
  }

  @Test
  fun `list byte_array values`() {
    assertEquals(
      ByteArrayListTag(listOf(
        ByteArrayTag(emptyList()),
        ByteArrayTag(listOf(0, 1)),
      )),
      buildByteArrayListTag {
        add()
        add(0, 1)
      }
    )
  }

  @Test
  fun `list int_array empty`() {
    assertEquals(
      IntArrayListTag(emptyList()),
      buildIntArrayListTag { }
    )
  }

  @Test
  fun `list int_array values`() {
    assertEquals(
      IntArrayListTag(listOf(
        IntArrayTag(emptyList()),
        IntArrayTag(listOf(0, 1)),
      )),
      buildIntArrayListTag {
        add()
        add(0, 1)
      }
    )
  }

  @Test
  fun `list long_array empty`() {
    assertEquals(
      LongArrayListTag(emptyList()),
      buildLongArrayListTag { }
    )
  }

  @Test
  fun `list long_array values`() {
    assertEquals(
      LongArrayListTag(listOf(
        LongArrayTag(emptyList()),
        LongArrayTag(listOf(0, 1)),
      )),
      buildLongArrayListTag {
        add()
        add(0, 1)
      }
    )
  }

  @Test
  fun `list list empty`() {
    assertEquals(
      ListListTag(emptyList()),
      buildListListTag { }
    )
  }

  @Test
  fun `list list nested`() {
    assertEquals(
      ListListTag(listOf(
        ListListTag(emptyList()),
      )),
      buildListListTag {
        addListListTag { }
      }
    )
  }

  @Test
  fun `list list values monomorphic`() {
    assertEquals(
      ListListTag(listOf(
        StringListTag(emptyList()),
        StringListTag(emptyList()),
      )),
      buildListListTag {
        addStringListTag { }
        addStringListTag { }
      }
    )
  }

  @Test
  fun `list list values polymorphic`() {
    assertEquals(
      ListListTag(listOf(
        StringListTag(emptyList()),
        CompoundListTag(emptyList()),
      )),
      buildListListTag {
        addStringListTag { }
        addCompoundListTag { }
      }
    )
  }
}
