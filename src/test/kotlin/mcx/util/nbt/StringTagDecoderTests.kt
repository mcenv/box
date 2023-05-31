package mcx.util.nbt

import kotlinx.serialization.Serializable
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

object StringTagDecoderTests {
  @Test
  fun `boolean false`() {
    assertEquals(
      false,
      Nbt.decodeFromString("false"),
    )
  }

  @Test
  fun `boolean true`() {
    assertEquals(
      true,
      Nbt.decodeFromString("true"),
    )
  }

  @Test
  fun `byte 0b`() {
    assertEquals(
      0.toByte(),
      Nbt.decodeFromString("0b"),
    )
  }

  @Test
  fun `short 0s`() {
    assertEquals(
      0.toShort(),
      Nbt.decodeFromString("0s"),
    )
  }

  @Test
  fun `int 0`() {
    assertEquals(
      0,
      Nbt.decodeFromString("0"),
    )
  }

  @Test
  fun `long 0L`() {
    assertEquals(
      0L,
      Nbt.decodeFromString("0L"),
    )
  }

  @Test
  fun `float 0f`() {
    assertEquals(
      0.0f,
      Nbt.decodeFromString("0.0f"),
    )
  }

  @Test
  fun `double 0d`() {
    assertEquals(
      0.0,
      Nbt.decodeFromString("0.0d"),
    )
  }

  @Test
  fun `string empty`() {
    assertEquals(
      "",
      Nbt.decodeFromString("\"\""),
    )
  }

  enum class A {
    A
  }

  @Test
  fun `enum 0`() {
    assertEquals(
      A.A,
      Nbt.decodeFromString("A"),
    )
  }

  @Serializable
  @AsIntTag
  enum class A1 {
    A
  }

  @Test
  fun `enum 0 as int`() {
    assertEquals(
      A1.A,
      Nbt.decodeFromString("0"),
    )
  }

  @Serializable
  @JvmInline
  value class B(val a: Byte)

  @Test
  fun `inline 0b`() {
    assertEquals(
      B(0),
      Nbt.decodeFromString("0b"),
    )
  }

  @Serializable
  class C {
    override fun equals(other: Any?): Boolean {
      return other is C
    }
  }

  @Test
  fun `class empty`() {
    assertEquals(
      C(),
      Nbt.decodeFromString<C>("{}"),
    )
  }

  @Serializable
  data class D(val a: Byte)

  @Test
  fun `class 0b`() {
    assertEquals(
      D(0),
      Nbt.decodeFromString("{a:0b}"),
    )
  }

  @Test
  fun `class 0b trailing comma`() {
    assertEquals(
      D(0),
      Nbt.decodeFromString("{a:0b,}"),
    )
  }

  @Serializable
  data class E(val a: Byte, val b: Short)

  @Test
  fun `class 0b 0s`() {
    assertEquals(
      E(0, 0),
      Nbt.decodeFromString("{a:0b,b:0s}"),
    )
  }

  @Serializable
  data class F(val a: C)

  @Test
  fun `class class empty`() {
    assertEquals(
      F(C()),
      Nbt.decodeFromString("{a:{}}"),
    )
  }

  @Serializable
  data class G(val a: B)

  @Test
  fun `class inline 0b`() {
    assertEquals(
      G(B(0)),
      Nbt.decodeFromString("{a:0b}"),
    )
  }

  @Test
  fun `list empty`() {
    assertEquals(
      emptyList<Nothing>(),
      Nbt.decodeFromString("[]"),
    )
  }

  @Test
  fun `list 0b`() {
    assertEquals(
      listOf<Byte>(0),
      Nbt.decodeFromString("[0b]"),
    )
  }

  @Test
  fun `list 0b trailing comma`() {
    assertEquals(
      listOf<Byte>(0),
      Nbt.decodeFromString("[0b,]"),
    )
  }

  @Test
  fun `list 0b 0b`() {
    assertEquals(
      listOf<Byte>(0, 0),
      Nbt.decodeFromString("[0b,0b]"),
    )
  }

  @Test
  fun `list list empty`() {
    assertEquals(
      listOf(listOf<Nothing>()),
      Nbt.decodeFromString("[[]]"),
    )
  }

  @Test
  fun `boolean false redundant`() {
    assertThrows<IllegalArgumentException> {
      Nbt.decodeFromString<Boolean>("false,")
    }
  }
}
