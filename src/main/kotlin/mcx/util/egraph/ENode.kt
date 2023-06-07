package mcx.util.egraph

data class ENode(
  val op: String,
  val args: List<EClassId> = emptyList(),
)
