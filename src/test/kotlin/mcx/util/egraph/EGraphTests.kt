package mcx.util.egraph

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

object EGraphTests {
  private fun EGraph.add(op: String, args: List<EClassId> = emptyList()): EClassId {
    return add(ENode(op, args))
  }

  @Test
  fun `different nodes`() {
    EGraph().run {
      val a = add("a")
      val b = add("b")
      assertFalse(equals(a, b))

      merge(a, b)
      rebuild()
      assertTrue(equals(a, b))
    }
  }

  @Test
  fun `same nodes`() {
    EGraph().run {
      val a1 = add("a")
      val a2 = add("a")
      assertTrue(equals(a1, a2))

      merge(a1, a2)
      rebuild()
      assertTrue(equals(a1, a2))
    }
  }

  @Test
  fun `merge functions`() {
    EGraph().run {
      val a = add("a")
      val fa = add("f", listOf(a))
      val ga = add("g", listOf(a))
      assertFalse(equals(fa, ga))

      merge(fa, ga)
      rebuild()
      assertTrue(equals(fa, ga))
    }
  }

  @Test
  fun `already equivalent`() {
    EGraph().run {
      val a1 = add("a")
      val b = add("b")
      assertFalse(equals(a1, b))

      merge(a1, b)
      rebuild()
      assertTrue(equals(a1, b))

      val a2 = add("a")
      assertTrue(equals(a1, a2))
    }
  }

  @Test
  fun `upward merging`() {
    EGraph().run {
      val a = add("a")
      val b = add("b")
      val c = add("c")
      val fab = add("f", listOf(a, b))
      val fac = add("f", listOf(a, c))
      val gfab = add("g", listOf(fab))
      val gfac = add("g", listOf(fac))
      assertFalse(equals(b, c))
      assertFalse(equals(fab, fac))
      assertFalse(equals(gfab, gfac))

      merge(b, c)
      rebuild()
      assertTrue(equals(b, c))
      assertTrue(equals(fab, fac))
      assertTrue(equals(gfab, gfac))
    }
  }

  @Test
  fun recursive() {
    EGraph().run {
      val a = add("a")
      val fa = add("f", listOf(a))
      assertFalse(equals(a, fa))

      merge(a, fa)
      rebuild()
      assertTrue(equals(a, fa))

      val ffa = add("f", listOf(fa))
      assertTrue(equals(fa, ffa))

      val fffa = add("f", listOf(ffa))
      assertTrue(equals(ffa, fffa))
    }
  }
}
