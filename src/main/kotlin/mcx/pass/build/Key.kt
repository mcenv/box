package mcx.pass.build

import mcx.ast.common.ModuleLocation
import mcx.lsp.Instruction
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
}
