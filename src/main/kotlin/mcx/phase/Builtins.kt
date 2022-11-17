package mcx.phase

import mcx.ast.Location
import mcx.ast.Value

val BUILTINS: Map<Location, Builtin> = TODO()

sealed interface Builtin {
  fun eval(arg: Value): Value
}
