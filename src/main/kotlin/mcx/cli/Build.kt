package mcx.cli

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import mcx.ast.Location
import mcx.phase.Cache
import mcx.phase.Context
import mcx.phase.Generate
import java.io.Closeable
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.*

@OptIn(ExperimentalCli::class)
object Build : Subcommand(
  "build",
  "Build",
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
      Files
        .walk(src)
        .filter { it.extension == "mcx" }
        .forEach { path ->
          val context = Context()
          cache
            .fetchGenerated(
              pack,
              generator,
              context,
              Location(
                src
                  .relativize(path)
                  .invariantSeparatorsPathString
                  .dropLast(".mcx".length)
                  .split('/')
              ),
            )
          context.diagnostics.forEach { println(it) }
        }
    }
  }
}
