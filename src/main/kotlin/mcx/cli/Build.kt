package mcx.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import mcx.ast.Location
import mcx.phase.Cache
import mcx.phase.Generate
import java.io.Closeable
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.stream.Stream
import kotlin.io.path.*
import kotlin.system.exitProcess

@OptIn(ExperimentalCli::class)
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
    val datapacks = Paths
      .get(
        levelName,
        "datapacks",
      )
      .also { it.createDirectories() }

    Build::class.java
      .getResourceAsStream("/mcx.zip")!!
      .buffered()
      .use { input ->
        datapacks
          .resolve("mcx.zip")
          .outputStream()
          .buffered()
          .use { output -> input.transferTo(output) }
      }

    val root = Paths.get("")
    val src = root.resolve("src")
    val cache = Cache(src)
    val pack = root
      .toAbsolutePath()
      .last()
      .toString()

    fun Path.toLocation(): Location =
      Location(
        src
          .relativize(this)
          .invariantSeparatorsPathString
          .dropLast(".mcx".length)
          .split('/')
      )

    fun inputs(): Stream<Path> =
      Files
        .walk(src)
        .filter { it.extension == "mcx" }

    var valid = true
    inputs().forEach { path ->
      val core = cache.fetchCore(path.toLocation())!!
      if (core.value.diagnostics.isNotEmpty()) {
        valid = false
        core.value.diagnostics.forEach {
          println("[${it.severity.name.lowercase()}] ${path.invariantSeparatorsPathString} ${it.range.start.line + 1}:${it.range.start.character + 1} ${it.message}")
        }
      }
    }

    if (valid) {
      object : Generate.Generator,
               Closeable {
        private var output: OutputStream? = null

        override fun entry(name: String) {
          output?.close()
          output = datapacks
            .resolve(pack)
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
        inputs().forEach { path ->
          cache.fetchGenerated(
            pack,
            generator,
            path.toLocation(),
          )
        }
      }
    } else {
      exitProcess(-1)
    }
  }
}
