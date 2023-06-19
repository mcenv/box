package mcx.pass.frontend.parse

import mcx.ast.Surface.Term

sealed class Node {
  abstract val children: MutableList<Node>?

  data class Lit(val name: String) : Node() {
    override val children: MutableList<Node> = mutableListOf()
  }

  data class Arg(val term: Term) : Node() {
    override val children: MutableList<Node> = mutableListOf()
  }

  data object Nil : Node() {
    override val children: MutableList<Node>? = null
  }
}
