package box.util.collections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

object UnionFindTests {
  @Test
  fun basic() {
    UnionFind().run {
      val a = make()
      val b = make()
      val c = make()

      // a{a} b{b} c{c}

      assertEquals(a, find(a))
      assertEquals(b, find(b))
      assertEquals(c, find(c))

      union(a, b)

      // a{a, b} c{c}

      assertTrue(equals(a, b))
      assertFalse(equals(a, c))
      assertFalse(equals(b, c))

      assertEquals(a, find(a))
      assertEquals(a, find(b))
      assertEquals(c, find(c))

      union(b, c)

      // a{a, b, c}

      assertTrue(equals(c, b))
      assertTrue(equals(b, a))
      assertTrue(equals(c, a))

      assertEquals(a, find(a))
      assertEquals(a, find(b))
      assertEquals(a, find(c))
    }
  }
}
