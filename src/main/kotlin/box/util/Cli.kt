package box.util

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import box.cache.*
import box.lsp.BoxLanguageServer
import box.lsp.diagnosticMessage
import box.pass.Build
import box.pass.Config
import box.pass.backend.Pack
import org.eclipse.lsp4j.launch.LSPLauncher
import kotlin.io.path.*
import kotlin.system.exitProcess
import kotlin.time.measureTime

fun main(args: Array<String>) {
  try {
    when (args.getOrNull(0)) {
      "build"         -> build()
      "dependencies"  -> dependencies()
      "help"          -> help()
      "init"          -> init()
      "installations" -> when (args.getOrNull(1)) {
        "play"   -> `installations play`(args[2], args.copyOfRange(3, args.size))
        "create" -> `installations create`(args[2])
        "delete" -> `installations delete`(args[2])
        else     -> null
      }
      "lsp"           -> lsp()
      "test"          -> test(args[1], args.copyOfRange(2, args.size))
      "version"       -> version()
      else            -> null
    } ?: error("Unknown command: ${args.joinToString(" ")}")
  } catch (t: Throwable) {
    System.err.println(red(t.message ?: ""))
    t.printStackTrace()
    exitProcess(1)
  }
}

private fun build() {
  measureTime {
    val result = runBlocking {
      Build(Path(""))()
    }
    result.diagnosticsByPath.forEach { (path, diagnostics) ->
      diagnostics.forEach {
        println(diagnosticMessage(path, it))
      }
    }
    if (!result.success) {
      error("Build failed")
    }
  }.also {
    info("Finished", "in $it")
  }
}

fun dependencies() {
  measureTime {
    installDependencies(Path(""))
  }.also {
    info("Finished", "in $it")
  }
}

fun help() {
  TODO()
}

@OptIn(ExperimentalSerializationApi::class)
fun init() {
  val json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
  }

  val name = Path("").toAbsolutePath().last().toString()
  Path("pack.json").encodeToJson(Config(name = name, description = ""), json)
  Path("src").createDirectories()
}

@Suppress("FunctionName")
fun `installations play`(version: String, args: Array<String>) {
  playServer(version, args)
}

@Suppress("FunctionName")
fun `installations create`(version: String) {
  var manifest: VersionManifest? = null
  val id = when (version) {
    "release"  -> {
      manifest = fetchVersionManifest()
      manifest.latest.release
    }
    "snapshot" -> {
      manifest = fetchVersionManifest()
      manifest.latest.snapshot
    }
    else       -> {
      version
    }
  }

  if (getServerPath(id).exists()) {
    return
  }

  (manifest ?: fetchVersionManifest())
    .versions
    .first { it.id == id }
    .url
    .openStream()
    .use { @OptIn(ExperimentalSerializationApi::class) json.decodeFromStream<Package>(it) }
    .downloads
    .let { downloads -> downloads.server.url.openStream().use { getServerPath(id).saveFromStream(it) } }
}

@Suppress("FunctionName")
fun `installations delete`(version: String) {
  @OptIn(ExperimentalPathApi::class)
  getOrCreateServerRootPath(version).deleteRecursively()
}

fun lsp() {
  val server = BoxLanguageServer()
  val launcher = LSPLauncher.createServerLauncher(server, System.`in`, System.out)
  server.connect(launcher.remoteProxy)
  launcher.startListening().get()
}

fun test(version: String, args: Array<String>) {
  measureTime {
    val buildResult = runBlocking {
      Build(Path(""))()
    }
    if (!buildResult.success) {
      error("Build failed")
    }

    var success = true
    playServer(version, args) { rcon ->
      rcon.exec("function ${Pack.INIT.namespace}:${Pack.INIT.path}")

      success = buildResult.tests.fold(true) { acc, test ->
        print("Testing $test ... ")
        val name = Pack.packDefinitionLocation(test)
        rcon.exec("function ${name.namespace}:${name.path}")
        val message = rcon.exec("data get storage box_test: test")
        acc and when (message.takeLast(4)) {
          "[0b]" -> {
            println(red("failed"))
            false
          }
          "[1b]" -> {
            println(green("passed"))
            true
          }
          else   -> {
            println(red(message))
            false
          }
        }
      }
      rcon.exec("stop")
    }
    if (!success) {
      error("Test failed")
    }
  }.also {
    info("Finished", "in $it")
  }
}

fun version() {
  val version = ::version::class.java.getResource("/version")!!.readText()
  print(version)
}
