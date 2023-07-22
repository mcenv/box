package box.util.nbt

import box.util.nbt.internal.*
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertContentEquals

object BinaryTagEncoderTests {
  @Test
  fun `boolean false`() {
    assertContentEquals(
      byteArrayOf(0),
      Nbt.encodeToByteArray(false),
    )
  }

  @Test
  fun `boolean true`() {
    assertContentEquals(
      byteArrayOf(1),
      Nbt.encodeToByteArray(true),
    )
  }

  @Test
  fun `byte 0b`() {
    assertContentEquals(
      byteArrayOf(0),
      Nbt.encodeToByteArray(0.toByte()),
    )
  }

  @Test
  fun `short 0s`() {
    assertContentEquals(
      byteArrayOf(0, 0),
      Nbt.encodeToByteArray(0.toShort()),
    )
  }

  @Test
  fun `int 0`() {
    assertContentEquals(
      byteArrayOf(0, 0, 0, 0),
      Nbt.encodeToByteArray(0),
    )
  }

  @Test
  fun `long 0L`() {
    assertContentEquals(
      byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
      Nbt.encodeToByteArray(0L),
    )
  }

  @Test
  fun `float 0f`() {
    assertContentEquals(
      byteArrayOf(0, 0, 0, 0),
      Nbt.encodeToByteArray(0.0f),
    )
  }

  @Test
  fun `double 0d`() {
    assertContentEquals(
      byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
      Nbt.encodeToByteArray(0.0),
    )
  }

  @Test
  fun `string empty`() {
    assertContentEquals(
      byteArrayOf(0, 0),
      Nbt.encodeToByteArray(""),
    )
  }

  enum class A {
    A
  }

  @Test
  fun `enum 0`() {
    assertContentEquals(
      byteArrayOf(0, 1, 'A'.code.toByte()),
      Nbt.encodeToByteArray(A.A),
    )
  }

  @Serializable
  @AsIntTag
  enum class A1 {
    A
  }

  @Test
  fun `enum 0 as int`() {
    assertContentEquals(
      byteArrayOf(0, 0, 0, 0),
      Nbt.encodeToByteArray(A1.A),
    )
  }


  @Serializable
  @JvmInline
  value class B(val a: Byte)

  @Test
  fun `inline 0b`() {
    assertContentEquals(
      byteArrayOf(0),
      Nbt.encodeToByteArray(B(0)),
    )
  }

  @Serializable
  class C

  @Test
  fun `class empty`() {
    assertContentEquals(
      byteArrayOf(END),
      Nbt.encodeToByteArray(C()),
    )
  }

  @Serializable
  data class D(val a: Byte)

  @Test
  fun `class 0b`() {
    assertContentEquals(
      byteArrayOf(BYTE, 0, 1, 'a'.code.toByte(), 0, END),
      Nbt.encodeToByteArray(D(0)),
    )
  }

  @Serializable
  data class E(val a: Byte, val b: Short)

  @Test
  fun `class 0b 0s`() {
    assertContentEquals(
      byteArrayOf(BYTE, 0, 1, 'a'.code.toByte(), 0, SHORT, 0, 1, 'b'.code.toByte(), 0, 0, END),
      Nbt.encodeToByteArray(E(0, 0)),
    )
  }

  @Serializable
  data class F(val a: C)

  @Test
  fun `class class empty`() {
    assertContentEquals(
      byteArrayOf(COMPOUND, 0, 1, 'a'.code.toByte(), END, END),
      Nbt.encodeToByteArray(F(C())),
    )
  }

  @Serializable
  data class G(val a: B)

  @Test
  fun `class inline 0b`() {
    assertContentEquals(
      byteArrayOf(BYTE, 0, 1, 'a'.code.toByte(), 0, END),
      Nbt.encodeToByteArray(G(B(0))),
    )
  }

  @Test
  fun `list empty`() {
    assertContentEquals(
      byteArrayOf(END, 0, 0, 0, 0),
      Nbt.encodeToByteArray(emptyList<Nothing>()),
    )
  }

  @Test
  fun `list 0b`() {
    assertContentEquals(
      byteArrayOf(BYTE, 0, 0, 0, 1, 0),
      Nbt.encodeToByteArray(listOf<Byte>(0)),
    )
  }

  @Test
  fun `list list empty`() {
    assertContentEquals(
      byteArrayOf(LIST, 0, 0, 0, 1, END, 0, 0, 0, 0),
      Nbt.encodeToByteArray(listOf(listOf<Nothing>())),
    )
  }
}
