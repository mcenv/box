package mcx.cli

import com.sun.tools.attach.VirtualMachine
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import mcx.util.cache.BUNDLER_REPO_DIR
import mcx.util.cache.getServerPath
import mcx.util.cache.root
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.net.URLClassLoader
import kotlin.io.path.absolutePathString
import kotlin.io.path.notExists
import kotlin.io.path.toPath

@OptIn(ExperimentalCli::class)
object Launch : Subcommand(
  "launch",
  "Launch the Minecraft server",
) {
  private val version: String by argument(ArgType.String)

  override fun execute() {
    val serverPath = getServerPath(version)
    if (serverPath.notExists()) {
      error("not installed: '$version'")
    }

    val pid = ProcessHandle.current().pid()
    val machine = VirtualMachine.attach(pid.toString())
    val agent = Launch::class.java.protectionDomain.codeSource.location.toURI().toPath()
    machine.loadAgent(agent.absolutePathString())

    System.setProperty(BUNDLER_REPO_DIR, root.absolutePathString())

    val classLoader = URLClassLoader(arrayOf(serverPath.toUri().toURL()), null)
    val mainClass = Class.forName("net.minecraft.bundler.Main", true, classLoader)
    val mainHandle = MethodHandles.lookup().findStatic(mainClass, "main", MethodType.methodType(Void.TYPE, Array<String>::class.java)).asFixedArity()
    mainHandle(emptyArray<String>())

    machine.detach()
  }
}
