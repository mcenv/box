package mcx.cli

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext

fun registerCommands(dispatcher: CommandDispatcher<Unit>) {
  BuildCommands.register(dispatcher)
  HelpCommands.register(dispatcher)
  InitCommands.register(dispatcher)
  InstallationsCommands.register(dispatcher)
  LspCommands.register(dispatcher)
  VersionCommands.register(dispatcher)
}

@Suppress("NOTHING_TO_INLINE")
inline fun literal(name: String): LiteralArgumentBuilder<Unit> {
  return LiteralArgumentBuilder.literal(name)
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T> argument(
  name: String,
  type: ArgumentType<T>,
): RequiredArgumentBuilder<Unit, T> {
  return RequiredArgumentBuilder.argument(name, type)
}

inline operator fun <reified V> CommandContext<*>.get(name: String): V {
  return getArgument(name, V::class.java)
}
