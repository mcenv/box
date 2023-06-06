package mcx.cli

import com.mojang.brigadier.CommandDispatcher
import mcx.cache.installDependencies
import kotlin.io.path.Path

object DependenciesCommands {
  fun register(dispatcher: CommandDispatcher<Unit>) {
    dispatcher.register(
      literal("dependencies")
        .then(
          literal("install")
            .executes {
              installDependencies(Path(""))
            }
        )
    )
  }
}
