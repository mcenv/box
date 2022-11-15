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
import mcx.phase.Build
import mcx.phase.Config
import mcx.phase.Diagnostic
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
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
        .get(levelName, "datapacks")
        .also { it.createDirectories() }

    val root = Paths.get("")
    val src = root.resolve("src")
    val build = Build(src)
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

    val diagnosticsByPath = Collections.synchronizedList(mutableListOf<Pair<Path, List<Diagnostic>>>())

    val datapackRoot = datapacks
      .resolve(config.name)
      .also { it.createDirectories() }

    val outputModules = runBlocking {
      Files
        .walk(src)
        .filter { it.extension == "mcx" }
        .map { path ->
          async {
            val core = build.fetchCore(config, path.toLocation())
            diagnosticsByPath += path to core.diagnostics
            core.module.resources
              .map { async { build.fetchGenerated(config, it.name) } }
              .awaitAll()
          }
        }
        .toList()
        .awaitAll()
        .flatten()
        .map { it.mapKeys { (name, _) -> datapackRoot.resolve(name) } }
        .reduce { acc, map -> acc + map }
        .onEach { (name, module) ->
          name
            .also { it.parent.createDirectories() }
            .bufferedWriter()
            .use { it.write(module) }
        }
    }

    Files
      .walk(datapackRoot)
      .filter { it.isRegularFile() }
      .forEach {
        if (it !in outputModules) {
          it.deleteExisting()
        }
      }

    if (diagnosticsByPath.isNotEmpty()) {
      diagnosticsByPath.forEach { (path, diagnostics) ->
        diagnostics.forEach {
          println("[${it.severity.name.lowercase()}] ${path.invariantSeparatorsPathString}:${it.range.start.line + 1}:${it.range.start.character + 1} ${it.message}")
        }
      }
      exitProcess(1)
    }
  }
}
