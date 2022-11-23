package mcx.phase

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import mcx.ast.DefinitionLocation
import mcx.ast.Lifted
import mcx.ast.ModuleLocation
import java.util.*

@Serializable
data class Context(
  val name: String,
  val debug: Boolean = false,
) {
  @Transient
  private val _liftedFunctions: MutableList<Lifted.Definition.Function> = Collections.synchronizedList(mutableListOf())
  val liftedFunctions: List<Lifted.Definition.Function> get() = _liftedFunctions

  fun liftFunction(
    function: Lifted.Definition.Function,
  ) {
    _liftedFunctions += function
  }

  companion object {
    val DISPATCH: DefinitionLocation = DefinitionLocation(ModuleLocation(), ":dispatch")
  }
}
