package mcx.util.egraph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

object EGraphTests {
  private fun EGraph.add(op: String, args: List<EClassId> = emptyList()): EClassId {
    return add(ENode(op, args))
  }

  private operator fun String.not(): Pattern.Var {
    return Pattern.Var(this)
  }

  private operator fun String.invoke(vararg args: Pattern): Pattern.Apply {
    return Pattern.Apply(this, args.toList())
  }

  @Test
  fun `different nodes`() {
    EGraph().run {
      val a = add("a")
      val b = add("b")
      assertFalse(equals(a, b))

      union(a, b)
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

      union(a1, a2)
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

      union(fa, ga)
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

      union(a1, b)
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

      union(b, c)
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

      union(a, fa)
      rebuild()
      assertTrue(equals(a, fa))

      val ffa = add("f", listOf(fa))
      assertTrue(equals(fa, ffa))

      val fffa = add("f", listOf(ffa))
      assertTrue(equals(ffa, fffa))
    }
  }

  @Test
  fun `match nothing`() {
    EGraph().run {
      add("a")
      assertEquals(
        emptyList(),
        match("b"()),
      )
    }
  }

  @Test
  fun `match one`() {
    EGraph().run {
      val a = add("a")
      assertEquals(
        listOf(emptyMap<String, EClassId>() to a),
        match("a"()),
      )
    }
  }

  @Test
  fun `match var`() {
    EGraph().run {
      val a = add("a")
      assertEquals(
        listOf(mapOf("x" to a) to a),
        match(!"x"),
      )
    }
  }

  @Test
  fun `match nested`() {
    EGraph().run {
      val a = add("a")
      val b = add("b", listOf(a))
      assertEquals(
        listOf(mapOf("x" to a) to a, mapOf("x" to b) to b),
        match(!"x"),
      )
    }
  }

  @Test
  fun `match apply`() {
    EGraph().run {
      val a = add("a")
      val b = add("b", listOf(a))
      assertEquals(
        listOf(mapOf("x" to a) to b),
        match("b"(!"x")),
      )
    }
  }

  @Test
  fun `match apply binary`() {
    EGraph().run {
      val a = add("a")
      val b = add("b")
      val c = add("c", listOf(a, b))
      assertEquals(
        listOf(mapOf("x" to a, "y" to b) to c),
        match("c"(!"x", !"y")),
      )
    }
  }

  @Test
  fun `match arity mismatch`() {
    EGraph().run {
      val a = add("a")
      val b = add("b", listOf(a))
      assertEquals(
        emptyList(),
        match("b"(!"x", !"y")),
      )
    }
  }

  @Test
  fun `match arithmetic`() {
    EGraph().run {
      val `2` = add("2")
      val a = add("a")
      val `a mul 2` = add("*", listOf(a, `2`))
      val `a mul 2 div 2` = add("/", listOf(`a mul 2`, `2`))
      assertEquals(
        listOf(mapOf("x" to a, "y" to `2`, "z" to `2`) to `a mul 2 div 2`),
        match("/"("*"(!"x", !"y"), !"z")),
      )
    }
  }
}
