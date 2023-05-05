package mcx.ast

import dev.mcenv.nbt.CompoundTag
import dev.mcenv.nbt.Tag
import mcx.data.ResourceLocation

object Packed {
  sealed class Definition {
    abstract val modifiers: List<Modifier>
    abstract val location: ResourceLocation

    data class Function(
      override val modifiers: List<Modifier>,
      override val location: ResourceLocation,
      val commands: List<Command>,
    ) : Definition()
  }

  enum class Modifier {
    TEST,
  }

  sealed class Command {
    sealed class Execute : Command() {
      data class Run(
        val redirect: Command,
      ) : Execute()

      data class StoreScore(
        val mode: Mode,
        val targets: ScoreHolder,
        val objective: Objective,
        val redirect: Execute,
      ) : Execute()

      data class StoreStorage(
        val mode: Mode,
        val target: DataAccessor,
        val type: Type,
        val scale: Double,
        val redirect: Execute,
      ) : Execute() {
        enum class Type {
          BYTE,
          SHORT,
          INT,
          LONG,
          FLOAT,
          DOUBLE,
        }
      }

      data class ConditionalScore(
        val conditional: Boolean,
        val target: ScoreHolder,
        val targetObjective: Objective,
        val comparator: Comparator,
        val source: ScoreHolder,
        val sourceObjective: Objective,
        val redirect: Execute,
      ) : Execute() {
        enum class Comparator {
          EQ,
          LT,
          LE,
          GT,
          GE,
        }
      }

      data class ConditionalScoreMatches(
        val conditional: Boolean,
        val target: ScoreHolder,
        val targetObjective: Objective,
        val range: IntRange,
        val redirect: Execute,
      ) : Execute()

      data class CheckMatchingData(
        val conditional: Boolean,
        val source: DataAccessor,
      ) : Execute()

      enum class Mode {
        RESULT,
        SUCCESS,
      }
    }

    data class ManipulateData(
      val target: DataAccessor,
      val manipulator: DataManipulator,
    ) : Command()

    data class RemoveData(
      val target: DataAccessor,
    ) : Command()

    data class GetData(
      val target: DataAccessor,
    ) : Command()

    data class RunFunction(
      val name: ResourceLocation,
    ) : Command()

    data class GetScore(
      val target: ScoreHolder,
      val objective: Objective,
    ) : Command()

    data class SetScore(
      val targets: ScoreHolder,
      val objective: Objective,
      val score: Int,
    ) : Command()

    data class PerformOperation(
      val targets: ScoreHolder,
      val targetObjective: Objective,
      val operation: Operation,
      val source: ScoreHolder,
      val sourceObjective: Objective,
    ) : Command()

    data class Raw(
      val message: String,
    ) : Command()
  }

  data class DataAccessor(
    val target: ResourceLocation,
    val path: NbtPath,
  )

  sealed class DataManipulator {
    abstract val source: SourceProvider

    data class Append(override val source: SourceProvider) : DataManipulator()
    data class Set(override val source: SourceProvider) : DataManipulator()
  }

  sealed class SourceProvider {
    data class Value(val value: Tag) : SourceProvider()
    data class From(val source: DataAccessor) : SourceProvider()
  }

  data class ScoreHolder(val name: String)

  data class Objective(val name: String)

  enum class Operation {
    ASSIGN,
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    MIN,
    MAX,
    SWAP,
  }

  data class NbtPath(val nodes: List<NbtNode>)

  sealed class NbtNode {
    data class MatchRootObject(val pattern: CompoundTag) : NbtNode()

    data class MatchElement(val pattern: CompoundTag) : NbtNode()

    data object AllElements : NbtNode()

    data class IndexedElement(val index: Int) : NbtNode()

    data class MatchObject(
      val name: String,
      val pattern: CompoundTag,
    ) : NbtNode()

    data class CompoundChild(val name: String) : NbtNode()
  }
}
