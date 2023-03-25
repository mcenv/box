package mcx.phase.build

import mcx.ast.Core
import mcx.ast.DefinitionLocation
import mcx.ast.ModuleLocation
import mcx.phase.backend.Lift
import mcx.phase.frontend.Elaborate
import mcx.phase.frontend.Parse
import mcx.phase.frontend.Resolve
import org.eclipse.lsp4j.Position

sealed interface Key<V> {
  data class Read(
    val location: ModuleLocation,
  ) : Key<String>

  data class Parsed(
    val location: ModuleLocation,
  ) : Key<Parse.Result>

  data class Resolved(
    val location: ModuleLocation,
  ) : Key<Resolve.Result>

  data class Elaborated(
    val location: ModuleLocation,
  ) : Key<Elaborate.Result> {
    var position: Position? = null
  }

  data class Staged(
    val location: DefinitionLocation,
  ) : Key<Core.Definition?>

  data class Lifted(
    val location: DefinitionLocation,
  ) : Key<Lift.Result?>

  object Packed : Key<List<mcx.ast.Packed.Definition>> {
    lateinit var locations: List<DefinitionLocation>
  }

  object Generated : Key<Map<String, String>> {
    lateinit var locations: List<DefinitionLocation>
  }
}
