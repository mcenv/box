package mcx.ast

import org.eclipse.lsp4j.Range

object Surface {
  data class Root(
    val resources: List<Resource0>,
  )

  sealed interface Resource0 {
    val name: String
    val range: Range

    data class Function(
      override val name: String,
      val params: List<Pair<String, Type0>>,
      val result: Type0,
      val body: Term0,
      override val range: Range,
    ) : Resource0

    data class Hole(
      override val range: Range,
    ) : Resource0 {
      override val name: String get() = throw IllegalStateException()
    }
  }

  sealed interface Type0 {
    val range: Range

    data class Int(
      override val range: Range,
    ) : Type0

    data class String(
      override val range: Range,
    ) : Type0

    data class Ref(
      val element: Type0,
      override val range: Range,
    ) : Type0

    data class Hole(
      override val range: Range,
    ) : Type0
  }

  sealed interface Term0 {
    val range: Range

    data class IntOf(
      val value: Int,
      override val range: Range,
    ) : Term0

    data class StringOf(
      val value: String,
      override val range: Range,
    ) : Term0

    data class RefOf(
      val value: Term0,
      override val range: Range,
    ) : Term0

    data class Let(
      val name: String,
      val init: Term0,
      val body: Term0,
      override val range: Range,
    ) : Term0

    data class Var(
      val name: String,
      override val range: Range,
    ) : Term0

    data class Run(
      val module: List<String>,
      val name: String,
      val args: List<Term0>,
      override val range: Range,
    ) : Term0

    data class Hole(
      override val range: Range,
    ) : Term0
  }
}
