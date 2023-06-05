package mcx.cli

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType.greedyString
import com.mojang.brigadier.arguments.StringArgumentType.string
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import kotlinx.coroutines.runBlocking
import mcx.cache.playServer
import mcx.pass.backend.Pack
import mcx.pass.build.Build
import kotlin.io.path.Path

object TestCommands {
  fun register(dispatcher: CommandDispatcher<Unit>) {
    dispatcher.register(
      literal<Unit>("test")
        .then(
          argument("version", string())
            .executes {
              val version: String = it["version"]
              test(version)
            }
            .then(
              argument("args", greedyString())
                .executes {
                  val version: String = it["version"]
                  val args: String = it["args"]
                  test(version, args)
                }
            )
        )
    )
  }

  private fun test(
    version: String,
    args: String? = null,
  ): Int {
    val buildResult = runBlocking {
      Build(Path(""))()
    }
    if (!buildResult.success) {
      return 1
    }

    var success = true
    playServer(version, args) { rcon ->
      rcon.exec("function ${Pack.INIT.namespace}:${Pack.INIT.path}")

      success = buildResult.tests.fold(true) { acc, test ->
        val name = Pack.packDefinitionLocation(test)
        val path = (test.module.parts + test.name).joinToString(".")
        rcon.exec("function ${name.namespace}:${name.path}")
        val message = rcon.exec("data get storage mcx_test: $path")
        acc and when (message.takeLast(2)) {
          "0b" -> {
            println("test $test \u001B[31mfailed\u001B[0m")
            false
          }
          "1b" -> {
            println("test $test \u001B[32mpassed\u001B[0m")
            true
          }
          else -> {
            println("\u001B[31mfatal: $message\u001B[0m")
            false
          }
        }
      }
      rcon.exec("stop")
    }
    return if (success) 0 else 1
  }
}
