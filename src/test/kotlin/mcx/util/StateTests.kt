package mcx.util

import kotlin.test.Test
import kotlin.test.assertEquals

object StateTests {
  @Test
  fun ops() {
    stateOf(0).run {
      assertEquals(0, get())
      set(1)
      assertEquals(1, get())
      modify { it + 1 }
      assertEquals(2, get())
    }
  }
}
