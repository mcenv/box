package mcx.cli

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import kotlinx.coroutines.runBlocking
import mcx.lsp.diagnosticMessage
import mcx.pass.build.Build
import kotlin.io.path.Path

object Build {
  fun register(dispatcher: CommandDispatcher<Unit>) {
    dispatcher.register(
      literal<Unit>("build")
        .executes {
          val result = runBlocking {
            Build(Path(""))()
          }
          result.diagnosticsByPath.forEach { (path, diagnostics) ->
            diagnostics.forEach {
              println(diagnosticMessage(path, it))
            }
          }
          if (result.success) 0 else 1
        }
    )
  }
}
