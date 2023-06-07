package mcx.util.egraph

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.plus
import mcx.util.collections.UnionFind

@Suppress("NAME_SHADOWING")
class EGraph {
  private val unionFind: UnionFind = UnionFind()
  private val classes: MutableMap<EClassId, EClass> = hashMapOf()
  private val hashcons: MutableMap<ENode, EClassId> = hashMapOf()
  private var worklist: MutableList<EClassId> = mutableListOf()

  fun add(node: ENode): EClassId {
    val node = canonicalize(node)
    val id = hashcons.computeIfAbsent(node) {
      val id = make()
      classes[id] = EClass(mutableListOf())
      node.args.forEach { arg ->
        getEClass(arg).use(node, id)
      }
      id
    }
    return id
  }

  private fun make(): EClassId {
    return EClassId(unionFind.make())
  }

  fun union(a: EClassId, b: EClassId): EClassId {
    val a = find(a)
    val b = find(b)
    return if (a == b) {
      a
    } else {
      val id = EClassId(unionFind.union(a.value, b.value))
      val cl = getEClass(id)

      classes -= if (id == a) {
        getEClass(b).uses.forEach { (node, id) -> cl.use(node, id) }
        b
      } else {
        getEClass(a).uses.forEach { (node, id) -> cl.use(node, id) }
        a
      }

      worklist.add(id)
      id
    }
  }

  fun rebuild() {
    while (worklist.isNotEmpty()) {
      val todo = worklist.mapTo(hashSetOf()) { find(it) }
      worklist = mutableListOf()
      todo.forEach { repair(getEClass(it)) }
    }
  }

  private fun repair(cl: EClass) {
    val oldUses = cl.uses
    cl.uses = mutableListOf() // ?
    oldUses.forEach { (node, id) ->
      hashcons -= node
      hashcons[canonicalize(node)] = find(id)
    }
    val newUses = hashMapOf<ENode, EClassId>()
    oldUses.forEach { (node, id) ->
      val node = canonicalize(node)
      newUses[node]?.let { union(id, it) }
      newUses[node] = find(id)
    }
    cl.uses = newUses.mapTo(mutableListOf()) { (node, id) -> node to id }
  }

  fun equals(a: EClassId, b: EClassId): Boolean {
    return find(a) == find(b)
  }

  private fun canonicalize(node: ENode): ENode {
    return ENode(node.op, node.args.map { find(it) })
  }

  private fun find(id: EClassId): EClassId {
    return EClassId(unionFind.find(id.value))
  }

  private fun getEClass(id: EClassId): EClass {
    return classes[id]!!
  }

  fun match(pattern: Pattern): List<Pair<Map<String, EClassId>, EClassId>> {
    val nodes: Map<EClassId, List<ENode>> = mutableMapOf<EClassId, MutableList<ENode>>().also { nodes ->
      hashcons.forEach { (node, id) ->
        nodes.computeIfAbsent(find(id)) { mutableListOf() } += node
      }
    }

    fun match(pattern: Pattern, id: EClassId): PersistentMap<String, EClassId>? {
      return when (pattern) {
        is Pattern.Var   -> {
          persistentHashMapOf(pattern.name to id)
        }
        is Pattern.Apply -> {
          nodes[id]!!.forEach { node ->
            if (pattern.op != node.op || pattern.args.size != node.args.size) {
              return null
            }
            return (pattern.args zip node.args).fold(persistentHashMapOf()) { acc, (pattern, id) ->
              acc + (match(pattern, id) ?: return null)
            }
          }
          null
        }
      }
    }

    val matched = mutableListOf<Pair<Map<String, EClassId>, EClassId>>()
    nodes.keys.forEach { id ->
      match(pattern, id)?.let {
        matched += it to id
      }
    }
    return matched
  }
}
