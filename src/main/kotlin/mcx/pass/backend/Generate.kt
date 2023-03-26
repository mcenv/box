package mcx.pass.backend

import mcx.ast.Packed
import mcx.data.Nbt
import mcx.data.ResourceLocation
import mcx.pass.Context
import mcx.util.quoted
import mcx.ast.Packed as P

class Generate private constructor(
  private val context: Context,
) {
  private fun generate(
    definition: P.Definition,
  ): Pair<String, String> {
    return "data/${definition.location.namespace}/functions/${definition.location.path}.mcfunction" to generateDefinition(definition)
  }

  private fun generateDefinition(
    definition: P.Definition,
  ): String {
    return when (definition) {
      is Packed.Definition.Function -> {
        StringBuilder().apply {
          definition.commands.forEachSeparated(
            separate = { append('\n') },
            action = if (context.config.debug) {
              {
                if (it !is P.Command.Raw) {
                  append("  ")
                }
                generateCommand(it)
              }
            } else {
              { generateCommand(it) }
            },
          )
        }
      }
    }.toString()
  }

  private fun StringBuilder.generateCommand(
    command: P.Command,
  ) {
    when (command) {
      is P.Command.Execute          -> {
        append("execute ")
        generateCommandExecute(command)
      }
      is P.Command.ManipulateData   -> {
        append("data modify ")
        generateDataAccessor(command.target)
        space()
        generateDataManipulator(command.manipulator)
      }
      is P.Command.RemoveData       -> {
        append("data remove ")
        generateDataAccessor(command.target)
      }
      is P.Command.GetData          -> {
        append("data get ")
        generateDataAccessor(command.target)
      }
      is P.Command.RunFunction      -> {
        append("function ")
        generateResourceLocation(command.name)
      }
      is P.Command.GetScore         -> {
        append("scoreboard players get ")
        generateScoreHolder(command.target)
        space()
        generateObjective(command.objective)
      }
      is P.Command.SetScore         -> {
        append("scoreboard players set ")
        generateScoreHolder(command.targets)
        space()
        generateObjective(command.objective)
        space()
        append(command.score.toString())
      }
      is P.Command.PerformOperation -> {
        append("scoreboard players operation ")
        generateScoreHolder(command.targets)
        space()
        generateObjective(command.targetObjective)
        space()
        generateOperation(command.operation)
        space()
        generateScoreHolder(command.source)
        space()
        generateObjective(command.sourceObjective)
      }
      is P.Command.Raw              -> append(command.message)
    }
  }

  private fun StringBuilder.generateCommandExecute(
    execute: P.Command.Execute,
  ) {
    when (execute) {
      is P.Command.Execute.Run                     -> {
        append("run ")
        generateCommand(execute.redirect)
      }
      is P.Command.Execute.StoreScore              -> {
        append("store ")
        generateMode(execute.mode)
        append(" score ")
        generateScoreHolder(execute.targets)
        space()
        generateObjective(execute.objective)
        space()
        generateCommandExecute(execute.redirect)
      }
      is P.Command.Execute.StoreStorage            -> {
        append("store ")
        generateMode(execute.mode)
        space()
        generateDataAccessor(execute.target)
        space()
        generateType(execute.type)
        space()
        append(execute.scale.toString())
        space()
        generateCommandExecute(execute.redirect)
      }
      is P.Command.Execute.ConditionalScore        -> {
        append(if (execute.conditional) "if score " else "unless score ")
        generateScoreHolder(execute.target)
        space()
        generateObjective(execute.targetObjective)
        space()
        generateComparator(execute.comparator)
        space()
        generateScoreHolder(execute.source)
        space()
        generateObjective(execute.sourceObjective)
        space()
        generateCommandExecute(execute.redirect)
      }
      is P.Command.Execute.ConditionalScoreMatches -> {
        append(if (execute.conditional) "if score " else "unless score ")
        generateScoreHolder(execute.target)
        space()
        generateObjective(execute.targetObjective)
        append(" matches ")
        generateIntRange(execute.range)
        space()
        generateCommandExecute(execute.redirect)
      }
      is P.Command.Execute.CheckMatchingData       -> {
        append(if (execute.conditional) "if data " else "unless data ")
        generateDataAccessor(execute.source)
      }
    }
  }

  private fun StringBuilder.generateType(
    type: P.Command.Execute.StoreStorage.Type,
  ) {
    when (type) {
      P.Command.Execute.StoreStorage.Type.BYTE   -> append("byte")
      P.Command.Execute.StoreStorage.Type.SHORT  -> append("short")
      P.Command.Execute.StoreStorage.Type.INT    -> append("int")
      P.Command.Execute.StoreStorage.Type.LONG   -> append("long")
      P.Command.Execute.StoreStorage.Type.FLOAT  -> append("float")
      P.Command.Execute.StoreStorage.Type.DOUBLE -> append("double")
    }
  }

  private fun StringBuilder.generateComparator(
    comparator: P.Command.Execute.ConditionalScore.Comparator,
  ) {
    when (comparator) {
      P.Command.Execute.ConditionalScore.Comparator.EQ -> append("=")
      P.Command.Execute.ConditionalScore.Comparator.LT -> append("<")
      P.Command.Execute.ConditionalScore.Comparator.LE -> append("<=")
      P.Command.Execute.ConditionalScore.Comparator.GT -> append(">")
      P.Command.Execute.ConditionalScore.Comparator.GE -> append(">=")
    }
  }

  private fun StringBuilder.generateMode(
    mode: P.Command.Execute.Mode,
  ) {
    when (mode) {
      P.Command.Execute.Mode.RESULT  -> append("result")
      P.Command.Execute.Mode.SUCCESS -> append("success")
    }
  }

  private fun StringBuilder.generateDataAccessor(
    accessor: P.DataAccessor,
  ) {
    when (accessor) {
      is P.DataAccessor.Storage -> {
        append("storage ")
        generateResourceLocation(accessor.target)
        space()
        generateNbtPath(accessor.path)
      }
    }
  }

  private fun StringBuilder.generateDataManipulator(
    manipulator: P.DataManipulator,
  ) {
    when (manipulator) {
      is P.DataManipulator.Append -> append("append ")
      is P.DataManipulator.Set    -> append("set ")
    }
    generateSourceProvider(manipulator.source)
  }

  private fun StringBuilder.generateSourceProvider(
    provider: P.SourceProvider,
  ) {
    when (provider) {
      is P.SourceProvider.Value -> {
        append("value ")
        generateNbt(provider.value)
      }
      is P.SourceProvider.From  -> {
        append("from ")
        generateDataAccessor(provider.source)
      }
    }
  }

  private fun StringBuilder.generateScoreHolder(
    holder: P.ScoreHolder,
  ) {
    append(holder.name)
  }

  private fun StringBuilder.generateObjective(
    objective: P.Objective,
  ) {
    append(objective.name)
  }

  private fun StringBuilder.generateOperation(
    operation: P.Operation,
  ) {
    when (operation) {
      P.Operation.ASSIGN -> append('=')
      P.Operation.ADD    -> append("+=")
      P.Operation.SUB    -> append("-=")
      P.Operation.MUL    -> append("*=")
      P.Operation.DIV    -> append("/=")
      P.Operation.MOD    -> append("%=")
      P.Operation.MIN    -> append("<")
      P.Operation.MAX    -> append(">")
      P.Operation.SWAP   -> append("><")
    }
  }

  private fun StringBuilder.generateNbtPath(
    path: P.NbtPath,
  ) {
    generateNbtNode(path.nodes.first())
    if (path.nodes.size > 1) {
      path.nodes
        .drop(1)
        .forEach {
          when (it) {
            is P.NbtNode.MatchObject, is P.NbtNode.CompoundChild -> append('.')
            else                                                 -> Unit
          }
          generateNbtNode(it)
        }
    }
  }

  private fun StringBuilder.generateNbtNode(
    node: P.NbtNode,
  ) {
    when (node) {
      is P.NbtNode.MatchRootObject -> generateNbt(node.pattern)
      is P.NbtNode.MatchElement    -> {
        append('[')
        generateNbt(node.pattern)
        append(']')
      }
      is P.NbtNode.AllElements     -> append("[]")
      is P.NbtNode.IndexedElement  -> {
        append('[')
        append(node.index.toString())
        append(']')
      }
      is P.NbtNode.MatchObject     -> {
        append(node.name)
        generateNbt(node.pattern)
      }
      is P.NbtNode.CompoundChild   -> append(node.name)
    }
  }

  private fun StringBuilder.generateNbt(
    nbt: Nbt,
  ) {
    when (nbt) {
      is Nbt.End       -> Unit
      is Nbt.Byte      -> {
        append(nbt.value.toString())
        append('b')
      }
      is Nbt.Short     -> {
        append(nbt.value.toString())
        append('s')
      }
      is Nbt.Int       -> append(nbt.value.toString())
      is Nbt.Long      -> {
        append(nbt.value.toString())
        append('l')
      }
      is Nbt.Float     -> {
        append(nbt.value.toString())
        append('f')
      }
      is Nbt.Double    -> append(nbt.value.toString())
      is Nbt.ByteArray -> {
        append("[B;")
        nbt.elements.forEachSeparated(
          separate = { append(',') },
          action = {
            append(it.toString())
            append('b')
          },
        )
        append(']')
      }
      is Nbt.IntArray  -> {
        append("[I;")
        nbt.elements.forEachSeparated(
          separate = { append(',') },
          action = { append(it.toString()) },
        )
        append(']')
      }
      is Nbt.LongArray -> {
        append("[L;")
        nbt.elements.forEachSeparated(
          separate = { append(',') },
          action = {
            append(it.toString())
            append('l')
          },
        )
        append(']')
      }
      is Nbt.String    -> append(nbt.value.quoted('"')) // TODO: optimize
      is Nbt.List<*>   -> {
        append('[')
        nbt.elements.forEachSeparated(
          separate = { append(',') },
          action = { generateNbt(it) },
        )
        append(']')
      }
      is Nbt.Compound  -> {
        append('{')
        nbt.elements.entries.forEachSeparated(
          separate = { append(',') },
          action = { (key, element) ->
            append(key.quoted('"')) // TODO: optimize
            append(':')
            generateNbt(element)
          },
        )
        append('}')
      }
    }
  }

  private fun StringBuilder.generateResourceLocation(
    location: ResourceLocation,
  ) {
    if (location.namespace != "minecraft") {
      append(location.namespace)
      append(':')
    }
    append(location.path)
  }

  private fun StringBuilder.generateIntRange(
    range: IntRange,
  ) {
    if (range.first == Int.MIN_VALUE && range.last == Int.MAX_VALUE) {
      append(Int.MIN_VALUE.toString())
      append("..")
    } else if (range.first == range.last) {
      append(range.first.toString())
    } else {
      if (range.first != Int.MIN_VALUE) {
        append(range.first.toString())
      }
      append("..")
      if (range.last != Int.MAX_VALUE) {
        append(range.last.toString())
      }
    }
  }

  @Suppress("NOTHING_TO_INLINE")
  private inline fun StringBuilder.space() {
    append(' ')
  }

  companion object {
    private inline fun <T> Iterable<T>.forEachSeparated(
      separate: () -> Unit,
      action: (T) -> Unit,
    ) {
      val iterator = iterator()
      if (iterator.hasNext()) {
        action(iterator.next())
      } else {
        return
      }
      while (iterator.hasNext()) {
        separate()
        action(iterator.next())
      }
    }

    operator fun invoke(
      context: Context,
      definition: P.Definition,
    ): Pair<String, String> {
      return Generate(context).generate(definition)
    }
  }
}
