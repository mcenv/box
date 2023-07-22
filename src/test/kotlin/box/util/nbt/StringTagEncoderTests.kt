package box.util.nbt

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

object StringTagEncoderTests {
  @Test
  fun `boolean false`() {
    assertEquals(
      "false",
      Nbt.encodeToString(false),
    )
  }

  @Test
  fun `boolean 0b`() {
    assertEquals(
      "0b",
      Nbt { booleanEncodingStrategy = BooleanEncodingStrategy.AS_BYTE }.encodeToString(false),
    )
  }

  @Test
  fun `boolean true`() {
    assertEquals(
      "true",
      Nbt.encodeToString(true),
    )
  }

  @Test
  fun `boolean 1b`() {
    assertEquals(
      "1b",
      Nbt { booleanEncodingStrategy = BooleanEncodingStrategy.AS_BYTE }.encodeToString(true),
    )
  }

  @Test
  fun `byte 0b`() {
    assertEquals(
      "0b",
      Nbt.encodeToString(0.toByte()),
    )
  }

  @Test
  fun `byte 0B`() {
    assertEquals(
      "0B",
      Nbt { byteTagSuffix = ByteTagSuffix.UPPERCASE }.encodeToString(0.toByte()),
    )
  }

  @Test
  fun `short 0s`() {
    assertEquals(
      "0s",
      Nbt.encodeToString(0.toShort()),
    )
  }

  @Test
  fun `short 0S`() {
    assertEquals(
      "0S",
      Nbt { shortTagSuffix = ShortTagSuffix.UPPERCASE }.encodeToString(0.toShort()),
    )
  }

  @Test
  fun `int 0`() {
    assertEquals(
      "0",
      Nbt.encodeToString(0),
    )
  }

  @Test
  fun `long 0L`() {
    assertEquals(
      "0L",
      Nbt.encodeToString(0L),
    )
  }

  @Test
  fun `long 0l`() {
    assertEquals(
      "0l",
      Nbt { longTagSuffix = LongTagSuffix.LOWERCASE }.encodeToString(0L),
    )
  }

  @Test
  fun `float 0f`() {
    assertEquals(
      "0.0f",
      Nbt.encodeToString(0.0f),
    )
  }

  @Test
  fun `float 0F`() {
    assertEquals(
      "0.0F",
      Nbt { floatTagSuffix = FloatTagSuffix.UPPERCASE }.encodeToString(0.0f),
    )
  }

  @Test
  fun `double 0d`() {
    assertEquals(
      "0.0d",
      Nbt.encodeToString(0.0),
    )
  }

  @Test
  fun `double 0D`() {
    assertEquals(
      "0.0D",
      Nbt { doubleTagSuffix = DoubleTagSuffix.UPPERCASE }.encodeToString(0.0),
    )
  }

  @Test
  fun `double 0`() {
    assertEquals(
      "0.0",
      Nbt { doubleTagSuffix = DoubleTagSuffix.NONE }.encodeToString(0.0),
    )
  }

  @Test
  fun `string empty`() {
    assertEquals(
      "\"\"",
      Nbt.encodeToString(""),
    )
  }

  enum class A {
    A
  }

  @Test
  fun `enum 0`() {
    assertEquals(
      "\"A\"",
      Nbt.encodeToString(A.A),
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
      "0",
      Nbt.encodeToString(A1.A),
    )
  }

  @Serializable
  @JvmInline
  value class B(val a: Byte)

  @Test
  fun `inline 0b`() {
    assertEquals(
      "0b",
      Nbt.encodeToString(B(0)),
    )
  }

  @Serializable
  class C

  @Test
  fun `class empty`() {
    assertEquals(
      "{}",
      Nbt.encodeToString(C()),
    )
  }

  @Test
  fun `class empty trailing comma`() {
    assertEquals(
      "{}",
      Nbt { trailingComma = true }.encodeToString(C()),
    )
  }

  @Serializable
  data class D(val a: Byte)

  @Test
  fun `class 0b`() {
    assertEquals(
      """{"a":0b}""",
      Nbt.encodeToString(D(0)),
    )
  }

  @Test
  fun `class 0b trailing comma`() {
    assertEquals(
      """{"a":0b,}""",
      Nbt { trailingComma = true }.encodeToString(D(0)),
    )
  }

  @Serializable
  data class E(val a: Byte, val b: Short)

  @Test
  fun `class 0b 0s`() {
    assertEquals(
      """{"a":0b,"b":0s}""",
      Nbt.encodeToString(E(0, 0)),
    )
  }

  @Test
  fun `class 0b 0s trailing comma`() {
    assertEquals(
      """{"a":0b,"b":0s,}""",
      Nbt { trailingComma = true }.encodeToString(E(0, 0)),
    )
  }

  @Serializable
  data class F(val a: C)

  @Test
  fun `class class empty`() {
    assertEquals(
      """{"a":{}}""",
      Nbt.encodeToString(F(C())),
    )
  }

  @Serializable
  data class G(val a: B)

  @Test
  fun `class inline 0b`() {
    assertEquals(
      """{"a":0b}""",
      Nbt.encodeToString(G(B(0))),
    )
  }

  @Test
  fun `list empty`() {
    assertEquals(
      "[]",
      Nbt.encodeToString(emptyList<Nothing>()),
    )
  }

  @Test
  fun `list empty trailing comma`() {
    assertEquals(
      "[]",
      Nbt { trailingComma = true }.encodeToString(emptyList<Nothing>()),
    )
  }

  @Test
  fun `list 0b`() {
    assertEquals(
      "[0b]",
      Nbt.encodeToString(listOf<Byte>(0)),
    )
  }

  @Test
  fun `list 0b trailing comma`() {
    assertEquals(
      "[0b,]",
      Nbt { trailingComma = true }.encodeToString(listOf<Byte>(0)),
    )
  }

  @Test
  fun `list list empty`() {
    assertEquals(
      "[[]]",
      Nbt.encodeToString(listOf(listOf<Nothing>())),
    )
  }
}
