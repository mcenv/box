package mcx.phase

import mcx.ast.DefinitionLocation
import mcx.ast.ModuleLocation
import java.util.concurrent.atomic.AtomicInteger

class Context(
  val config: Config,
) {
  private val id: AtomicInteger = AtomicInteger(0)

  fun freshId(): Int =
    id.getAndIncrement()

  companion object {
    val DISPATCH: DefinitionLocation = DefinitionLocation(ModuleLocation(), ":dispatch")
  }
}
