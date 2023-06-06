package mcx.util

data class DependencyTriple(
  val owner: String,
  val repository: String,
  val tag: String,
)

fun String.toDependencyTripleOrNull(): DependencyTriple? {
  val (_, owner, repository, tag) = Regex("""^([^/]+)/([^@]+)@(.+)$""").matchEntire(this)?.groupValues ?: return null
  return DependencyTriple(owner, repository, tag)
}
