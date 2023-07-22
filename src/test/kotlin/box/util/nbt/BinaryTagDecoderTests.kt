package box.util.nbt

import box.util.nbt.internal.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

object BinaryTagDecoderTests {
  @Test
  fun `boolean false`() {
    assertEquals(
      false,
      Nbt.decodeFromByteArray(byteArrayOf(0)),
    )
  }

  @Test
  fun `boolean true`() {
    assertEquals(
      true,
      Nbt.decodeFromByteArray(byteArrayOf(1)),
    )
  }

  @Test
  fun `byte 0b`() {
    assertEquals(
      0.toByte(),
      Nbt.decodeFromByteArray(byteArrayOf(0)),
    )
  }

  @Test
  fun `short 0s`() {
    assertEquals(
      0.toShort(),
      Nbt.decodeFromByteArray(byteArrayOf(0, 0)),
    )
  }

  @Test
  fun `int 0`() {
    assertEquals(
      0,
      Nbt.decodeFromByteArray(byteArrayOf(0, 0, 0, 0)),
    )
  }

  @Test
  fun `long 0L`() {
    assertEquals(
      0L,
      Nbt.decodeFromByteArray(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)),
    )
  }

  @Test
  fun `float 0f`() {
    assertEquals(
      0.0f,
      Nbt.decodeFromByteArray(byteArrayOf(0, 0, 0, 0)),
    )
  }

  @Test
  fun `double 0d`() {
    assertEquals(
      0.0,
      Nbt.decodeFromByteArray(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)),
    )
  }

  @Test
  fun `string empty`() {
    assertEquals(
      "",
      Nbt.decodeFromByteArray(byteArrayOf(0, 0)),
    )
  }

  enum class A {
    A
  }

  @Test
  fun `enum 0`() {
    assertEquals(
      A.A,
      Nbt.decodeFromByteArray(byteArrayOf(0, 1, 'A'.code.toByte())),
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
      Nbt.decodeFromByteArray(byteArrayOf(0, 0, 0, 0)),
    )
  }

  @Serializable
  @JvmInline
  value class B(val a: Byte)

  @Test
  fun `inline 0b`() {
    assertEquals(
      B(0),
      Nbt.decodeFromByteArray(byteArrayOf(0)),
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
      Nbt.decodeFromByteArray<C>(byteArrayOf(END)),
    )
  }

  @Serializable
  data class D(val a: Byte)

  @Test
  fun `class 0b`() {
    assertEquals(
      D(0),
      Nbt.decodeFromByteArray(byteArrayOf(BYTE, 0, 1, 'a'.code.toByte(), 0, END)),
    )
  }

  @Serializable
  data class E(val a: Byte, val b: Short)

  @Test
  fun `class 0b 0s`() {
    assertEquals(
      E(0, 0),
      Nbt.decodeFromByteArray(byteArrayOf(BYTE, 0, 1, 'a'.code.toByte(), 0, SHORT, 0, 1, 'b'.code.toByte(), 0, 0, END)),
    )
  }

  @Serializable
  data class F(val a: C)

  @Test
  fun `class class empty`() {
    assertEquals(
      F(C()),
      Nbt.decodeFromByteArray(byteArrayOf(COMPOUND, 0, 1, 'a'.code.toByte(), END, END)),
    )
  }

  @Serializable
  data class G(val a: B)

  @Test
  fun `class inline 0b`() {
    assertEquals(
      G(B(0)),
      Nbt.decodeFromByteArray(byteArrayOf(BYTE, 0, 1, 'a'.code.toByte(), 0, END)),
    )
  }

  @Test
  fun `list empty`() {
    assertEquals(
      emptyList<Nothing>(),
      Nbt.decodeFromByteArray(byteArrayOf(END, 0, 0, 0, 0)),
    )
  }

  @Test
  fun `list 0b`() {
    assertEquals(
      listOf<Byte>(0),
      Nbt.decodeFromByteArray(byteArrayOf(BYTE, 0, 0, 0, 1, 0)),
    )
  }

  @Test
  fun `list list empty`() {
    assertEquals(
      listOf(listOf<Nothing>()),
      Nbt.decodeFromByteArray(byteArrayOf(LIST, 0, 0, 0, 1, END, 0, 0, 0, 0)),
    )
  }

  @Test
  fun `boolean false redundant`() {
    assertThrows<IllegalArgumentException> {
      Nbt.decodeFromByteArray<Boolean>(byteArrayOf(0, 0))
    }
  }

  @Serializable
  data class Storage(
    val data: Data,
    @SerialName("DataVersion") val dataVersion: Int,
  ) {
    @Serializable
    data class Data(
      val contents: Contents,
    ) {
      @Serializable
      data class Contents(
        val a: Content,
      ) {
        @Serializable
        data class Content(
          val a: Byte,
        )
      }
    }
  }
}
