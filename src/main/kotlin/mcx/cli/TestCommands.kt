package mcx.cli

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import kotlinx.coroutines.runBlocking
import mcx.pass.backend.Pack
import mcx.pass.build.Build
import mcx.util.playServer
import kotlin.io.path.Path

object TestCommands {
  fun register(dispatcher: CommandDispatcher<Unit>) {
    dispatcher.register(
      literal<Unit>("test")
        .then(
          argument("version", StringArgumentType.string())
            .executes {
              val version: String = it["version"]
              val buildResult = runBlocking {
                Build(Path(""))()
              }
              if (!buildResult.success) {
                return@executes 1
              }
              var success = true
              playServer(version) { rcon ->
                rcon.exec("function ${Pack.INIT.namespace}:${Pack.INIT.path}")

                success = buildResult.tests.fold(true) { acc, test ->
                  val name = Pack.packDefinitionLocation(test)
                  val path = (test.module.parts + test.name).joinToString(".")
                  rcon.exec("function ${name.namespace}:${name.path}")
                  val message = rcon.exec("data get storage mcx_test: $path")
                  acc and when (val result = message.takeLast(2)) {
                    "0b" -> {
                      println("test $test \u001B[31mfailed\u001B[0m")
                      false
                    }
                    "1b" -> {
                      println("test $test \u001B[32mpassed\u001B[0m")
                      true
                    }
                    else -> {
                      println("\u001B[31munexpected result: '$result'\u001B[0m")
                      false
                    }
                  }
                }
                rcon.exec("stop")
              }
              if (success) 0 else 1
            }
        )
    )
  }
}
