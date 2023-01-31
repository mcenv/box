package mcx.script

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder

@Suppress("unused")
object Commands {
  @JvmStatic
  fun register(
    dispatcher: CommandDispatcher<Any>,
  ) {
    dispatcher.register(literal("mcx").executes { 0 })
  }

  @Suppress("NOTHING_TO_INLINE")
  private inline fun literal(name: String): LiteralArgumentBuilder<Any> {
    return LiteralArgumentBuilder.literal(name)
  }

  @Suppress("NOTHING_TO_INLINE")
  private inline fun <T> argument(
    name: String,
    type: ArgumentType<T>,
  ): RequiredArgumentBuilder<Any, T> {
    return RequiredArgumentBuilder.argument(name, type)
  }
}
