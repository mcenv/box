package mcx.phase

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import mcx.ast.DefinitionLocation
import java.util.*

@Serializable
data class Context(
  val name: String,
  val debug: Boolean = false,
) {
  @Transient
  private val _liftedFunctions: MutableList<DefinitionLocation> = Collections.synchronizedList(mutableListOf())
  val liftedFunctions: List<DefinitionLocation> get() = _liftedFunctions

  fun liftFunction(
    location: DefinitionLocation,
  ): Int {
    _liftedFunctions += location
    return _liftedFunctions.lastIndex
  }
}
