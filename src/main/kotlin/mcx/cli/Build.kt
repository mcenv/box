package mcx.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import mcx.ast.Location
import mcx.phase.Cache
import mcx.phase.Config
import mcx.phase.Generate
import java.io.Closeable
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.*
import kotlin.system.exitProcess

@OptIn(
  ExperimentalCli::class,
  ExperimentalSerializationApi::class,
)
object Build : Subcommand(
  "build",
  "Build the current pack",
) {
  override fun execute() {
    val serverProperties = Properties().apply {
      load(
        Paths
          .get("server.properties")
          .inputStream()
          .buffered()
      )
    }
    val levelName = serverProperties.getProperty("level-name")
    val datapacks =
      Paths
        .get(
          levelName,
          "datapacks",
        )
        .also { it.createDirectories() }

    val root = Paths.get("")
    val src = root.resolve("src")
    val cache = Cache(src)
    val config =
      Paths
        .get("pack.json")
        .inputStream()
        .buffered()
        .use {
          Json.decodeFromStream<Config>(it)
        }

    fun Path.toLocation(): Location =
      Location(
        src
          .relativize(this)
          .invariantSeparatorsPathString
          .dropLast(".mcx".length)
          .split('/')
      )

    val inputs: List<Path> =
      Files
        .walk(src)
        .filter { it.extension == "mcx" }
        .toList()

    val valid = AtomicBoolean(true)
    val diagnostics = runBlocking {
      inputs
        .map { path ->
          async {
            val core = cache.fetchCore(
              config,
              path.toLocation(),
            )!!
            if (core.diagnostics.isNotEmpty()) {
              valid.set(false)
            }
            path to core.diagnostics
          }
        }
        .awaitAll()
    }

    if (valid.get()) {
      object : Generate.Generator,
               Closeable {
        private var output: OutputStream? = null

        override fun entry(name: String) {
          output?.close()
          output =
            datapacks
              .resolve(config.name)
              .resolve(name)
              .also { it.parent.createDirectories() }
              .outputStream()
              .buffered()
        }

        override fun write(string: String) {
          output!!.write(string.toByteArray())
        }

        override fun close() {
          output?.close()
        }
      }.use { generator ->
        runBlocking {
          inputs
            .map { path ->
              async {
                cache.fetchGenerated(
                  config,
                  generator,
                  path.toLocation(),
                )
              }
            }
            .awaitAll()
        }
      }
    } else {
      diagnostics.forEach { (path, diagnostics) ->
        diagnostics.forEach {
          println("[${it.severity.name.lowercase()}] ${path.invariantSeparatorsPathString} ${it.range.start.line + 1}:${it.range.start.character + 1} ${it.message}")
        }
      }
      exitProcess(1)
    }
  }
}
