package mcx.util.egraph

class EClass(var uses: MutableList<Pair<ENode, EClassId>>) {
  fun use(node: ENode, id: EClassId) {
    uses += node to id
  }
}
