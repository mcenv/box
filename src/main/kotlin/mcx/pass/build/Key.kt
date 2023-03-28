package mcx.pass.build

import mcx.ast.Core
import mcx.ast.DefinitionLocation
import mcx.ast.ModuleLocation
import mcx.lsp.Instruction
import mcx.pass.backend.Lift
import mcx.pass.frontend.Elaborate
import mcx.pass.frontend.Parse
import mcx.pass.frontend.Resolve

sealed interface Key<V> {
  data class Read(
    val location: ModuleLocation,
  ) : Key<String>

  data class Parsed(
    val location: ModuleLocation,
  ) : Key<Parse.Result>

  data class Resolved(
    val location: ModuleLocation,
    val instruction: Instruction? = null
  ) : Key<Resolve.Result>

  data class Elaborated(
    val location: ModuleLocation,
    val instruction: Instruction? = null
  ) : Key<Elaborate.Result>

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