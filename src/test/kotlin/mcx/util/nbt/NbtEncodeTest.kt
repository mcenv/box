package mcx.util.nbt

import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals

@OptIn(ExperimentalUnsignedTypes::class)
object NbtEncodeTest {
  @Test
  fun test1() {
    assertContentEquals(
      ubyteArrayOf(
        /* (compound) */ 10u, /* "": */ 0u, 0u, /* { */
        /*   (byte) */ 1u, /* "a": */ 0u, 1u, 97u, /* 100b */ 100u,
        /*   (compound) */ 10u, /* "b": */ 0u, 1u, 98u, /* { */
        /*     (short) */ 2u, /* "c": */ 0u, 1u, 99u, /* 200s */ 0u, 200u,
        /*   } */ 0u,
        /* } */ 0u,
      ),
      A(100, B(200)).toUByteArray(),
    )
  }

  private inline fun <reified T> T.toUByteArray(): UByteArray {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { output ->
      Nbt.encode(this, output)
    }
    return bytes
      .toByteArray()
      .asUByteArray()
  }

  @Serializable
  data class A(
    val a: Byte,
    val b: B,
  )

  @Serializable
  data class B(
    val c: Short,
  )
}
