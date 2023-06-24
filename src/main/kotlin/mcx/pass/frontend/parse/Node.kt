package mcx.pass.frontend.parse

import mcx.ast.Surface as S

sealed class Node {
  abstract val children: MutableList<Node>?

  data object Root : Node() {
    override val children: MutableList<Node> = mutableListOf()
  }

  data class Lit(val name: String) : Node() {
    override val children: MutableList<Node> = mutableListOf()
  }

  data class Arg(val term: S.Term) : Node() {
    override val children: MutableList<Node> = mutableListOf()
  }

  data object Nil : Node() {
    override val children: MutableList<Node>? = null
  }
}
