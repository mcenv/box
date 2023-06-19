package mcx.pass.build

import mcx.ast.Core
import mcx.ast.DefinitionLocation
import mcx.ast.ModuleLocation
import mcx.lsp.Instruction
import mcx.pass.backend.Lift
import mcx.pass.frontend.Resolve
import mcx.pass.frontend.elaborate.Elaborate
import mcx.pass.frontend.parse.Parse

sealed class Key<V> {
  data class Read(
    val location: ModuleLocation,
  ) : Key<String>()

  data class Parsed(
    val location: ModuleLocation,
  ) : Key<Parse.Result>()

  data class Resolved(
    val location: ModuleLocation,
    val instruction: Instruction? = null,
  ) : Key<Resolve.Result>()

  data class Elaborated(
    val location: ModuleLocation,
    val instruction: Instruction? = null,
  ) : Key<Elaborate.Result>()

  data class Staged(
    val location: DefinitionLocation,
  ) : Key<Core.Definition?>()

  data class Lifted(
    val location: DefinitionLocation,
  ) : Key<Lift.Result?>()

  data object Packed : Key<List<mcx.ast.Packed.Definition>>() {
    lateinit var locations: List<DefinitionLocation>
  }

  data object Generated : Key<Generated.Packs>() {
    lateinit var locations: List<DefinitionLocation>

    data class Packs(
      val main: Map<String, String>,
      val test: Map<String, String>,
    )
  }
}
