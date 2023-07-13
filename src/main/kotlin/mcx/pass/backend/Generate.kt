package mcx.pass.backend

import mcx.ast.Packed
import mcx.data.ResourceLocation
import mcx.pass.Context
import mcx.util.collections.forEachSeparated
import mcx.util.nbt.*
import mcx.util.quoted
import mcx.util.unreachable
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
            action = if (context.config.debug.verbose) {
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
      is P.Command.AddScore         -> {
        append("scoreboard players add ")
        generateScoreHolder(command.targets)
        space()
        generateObjective(command.objective)
        space()
        append(command.score.toString())
      }
      is P.Command.RemoveScore      -> {
        append("scoreboard players remove ")
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
        execute.redirect?.let {
          space()
          generateCommandExecute(it)
        }
      }
      is P.Command.Execute.ConditionalScoreMatches -> {
        append(if (execute.conditional) "if score " else "unless score ")
        generateScoreHolder(execute.target)
        space()
        generateObjective(execute.targetObjective)
        append(" matches ")
        generateIntRange(execute.range)
        execute.redirect?.let {
          space()
          generateCommandExecute(it)
        }
      }
      is P.Command.Execute.CheckMatchingData       -> {
        append(if (execute.conditional) "if data " else "unless data ")
        generateDataAccessor(execute.source)
        execute.redirect?.let {
          space()
          generateCommandExecute(it)
        }
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
    append("storage ")
    generateResourceLocation(accessor.target)
    space()
    generateNbtPath(accessor.path)
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
    path: List<P.NbtNode>,
  ) {
    generateNbtNode(path.first())
    if (path.size > 1) {
      path.drop(1).forEach {
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
    fun String.normalized(): String {
      return if (
        any {
          when (it) {
            ' ', '"', '\'', '[', ']', '.', '{', '}' -> true
            else                                    -> false
          }
        }
      ) {
        quoted('"')
      } else {
        this
      }
    }

    when (node) {
      is P.NbtNode.MatchRootObject -> {
        generateNbt(node.pattern)
      }
      is P.NbtNode.MatchElement    -> {
        append('[')
        generateNbt(node.pattern)
        append(']')
      }
      is P.NbtNode.AllElements     -> {
        append("[]")
      }
      is P.NbtNode.IndexedElement  -> {
        append('[')
        append(node.index.toString())
        append(']')
      }
      is P.NbtNode.MatchObject     -> {
        append(node.name.normalized())
        generateNbt(node.pattern)
      }
      is P.NbtNode.CompoundChild   -> {
        append(node.name.normalized())
      }
    }
  }

  private fun StringBuilder.generateNbt(
    tag: Tag,
  ) {
    when (tag) {
      is EndTag       -> {
        unreachable()
      }
      is ByteTag      -> {
        append(tag.value.toString())
        append('b')
      }
      is ShortTag     -> {
        append(tag.value.toString())
        append('s')
      }
      is IntTag       -> append(tag.value.toString())
      is LongTag      -> {
        append(tag.value.toString())
        append('l')
      }
      is FloatTag     -> {
        when (tag.value) {
          Float.POSITIVE_INFINITY -> append(INFINITY_F)
          Float.NEGATIVE_INFINITY -> append("-$INFINITY_F")
          else                    -> append(tag.value.toString())
        }
        append('f')
      }
      is DoubleTag    -> {
        when (tag.value) {
          Double.POSITIVE_INFINITY -> append(INFINITY_D)
          Double.NEGATIVE_INFINITY -> append("-$INFINITY_D")
          else                     -> append(tag.value.toString())
        }
      }
      is ByteArrayTag -> {
        append("[B;")
        tag.forEachSeparated(
          separate = { append(',') },
          action = {
            append(it.toString())
            append('b')
          },
        )
        append(']')
      }
      is IntArrayTag  -> {
        append("[I;")
        tag.forEachSeparated(
          separate = { append(',') },
          action = { append(it.toString()) },
        )
        append(']')
      }
      is LongArrayTag -> {
        append("[L;")
        tag.forEachSeparated(
          separate = { append(',') },
          action = {
            append(it.toString())
            append('l')
          },
        )
        append(']')
      }
      is StringTag    -> {
        // TODO: benchmark to see if it is faster to be parsed when not quoted (not likely due to cascading regex matching)
        append(tag.value.quoted())
      }
      is ListTag      -> {
        append('[')
        tag.forEachSeparated(
          separate = { append(',') },
          action = { generateNbt(it) },
        )
        append(']')
      }
      is CompoundTag  -> {
        append('{')
        tag.entries.forEachSeparated(
          separate = { append(',') },
          action = { (key, element) ->
            append(key.quotedIfNecessary())
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
    /**
     * 40 > [jdk.internal.math.FloatingDecimal.SINGLE_MAX_DECIMAL_EXPONENT] + 1
     */
    private const val INFINITY_F = "1e40f"

    /**
     * 310 > [jdk.internal.math.FloatingDecimal.MAX_DECIMAL_EXPONENT] + 1
     */
    private const val INFINITY_D = "1e310"

    private fun Char.isAllowedInUnquotedString(): Boolean {
      return when (this) {
        in '0'..'9', in 'A'..'Z', in 'a'..'z', '_', '-', '.', '+' -> true
        else                                                      -> false
      }
    }

    private fun String.quotedIfNecessary(): String {
      return if (all { it.isAllowedInUnquotedString() }) {
        this
      } else {
        quoted()
      }
    }

    private fun String.quoted(): String {
      var single = 0
      var double = 0
      forEach {
        when (it) {
          '\'' -> single++
          '"'  -> double++
        }
      }
      return if (single < double) {
        quoted('\'')
      } else {
        quoted('"')
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
