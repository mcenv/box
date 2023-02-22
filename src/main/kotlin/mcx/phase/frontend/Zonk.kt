package mcx.phase.frontend

import mcx.phase.Context
import mcx.phase.prettyType
import mcx.util.diagnostic
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Range
import mcx.ast.Core as C

@Suppress("NAME_SHADOWING")
class Zonk private constructor(
  private val input: Elaborate.Result,
) {
  private val metaEnv: MetaEnv = input.metaEnv
  private val unsolved: MutableSet<C.Type.Meta> = hashSetOf()

  private fun zonk(): Result {
    val module = zonkModule(input.module)
    return Result(
      module,
      input.diagnostics + unsolved.map { unsolvedMeta(it, it.source) },
      input.completionItems,
      input.hover,
    )
  }

  private fun zonkModule(
    module: C.Module,
  ): C.Module {
    val definitions = module.definitions.map { zonkDefinition(it) }
    return C.Module(module.name, definitions)
  }

  private fun zonkDefinition(
    definition: C.Definition,
  ): C.Definition {
    return when (definition) {
      is C.Definition.Function -> {
        val binder = zonkPattern(definition.binder)
        val result = zonkType(definition.result)
        val body = definition.body?.let { zonkTerm(it) }
        C.Definition.Function(definition.modifiers, definition.name, definition.typeParams, binder, result, body)
      }
      is C.Definition.Type     -> {
        val body = zonkType(definition.body)
        C.Definition.Type(definition.modifiers, definition.name, body)
      }
      is C.Definition.Test     -> {
        val body = zonkTerm(definition.body)
        C.Definition.Test(definition.modifiers, definition.name, body)
      }
    }
  }

  private fun zonkType(
    type: C.Type,
  ): C.Type {
    return when (val type = metaEnv.forceType(type)) {
      is C.Type.Bool      -> type
      is C.Type.Byte      -> type
      is C.Type.Short     -> type
      is C.Type.Int       -> type
      is C.Type.Long      -> type
      is C.Type.Float     -> type
      is C.Type.Double    -> type
      is C.Type.String    -> type
      is C.Type.ByteArray -> type
      is C.Type.IntArray  -> type
      is C.Type.LongArray -> type
      is C.Type.List      -> C.Type.List(zonkType(type.element))
      is C.Type.Compound  -> C.Type.Compound(type.elements.mapValues { zonkType(it.value) })
      is C.Type.Tuple     -> C.Type.Tuple(type.elements.map { zonkType(it) }, type.kind)
      is C.Type.Union     -> C.Type.Union(type.elements.map { zonkType(it) }, type.kind)
      is C.Type.Func      -> C.Type.Func(zonkType(type.param), zonkType(type.result))
      is C.Type.Clos      -> C.Type.Clos(zonkType(type.param), zonkType(type.result))
      is C.Type.Code      -> C.Type.Code(zonkType(type.element))
      is C.Type.Var       -> type
      is C.Type.Run       -> type
      is C.Type.Meta      -> {
        unsolved += type
        type
      }
      is C.Type.Hole      -> type
    }
  }

  private fun zonkTerm(
    term: C.Term,
  ): C.Term {
    val type = zonkType(term.type)
    return when (term) {
      is C.Term.BoolOf      -> term
      is C.Term.ByteOf      -> term
      is C.Term.ShortOf     -> term
      is C.Term.IntOf       -> term
      is C.Term.LongOf      -> term
      is C.Term.FloatOf     -> term
      is C.Term.DoubleOf    -> term
      is C.Term.StringOf    -> term
      is C.Term.ByteArrayOf -> C.Term.ByteArrayOf(term.elements.map { zonkTerm(it) }, type)
      is C.Term.IntArrayOf  -> C.Term.IntArrayOf(term.elements.map { zonkTerm(it) }, type)
      is C.Term.LongArrayOf -> C.Term.LongArrayOf(term.elements.map { zonkTerm(it) }, type)
      is C.Term.ListOf      -> C.Term.ListOf(term.elements.map { zonkTerm(it) }, type)
      is C.Term.CompoundOf -> C.Term.CompoundOf(term.elements.mapValues { zonkTerm(it.value) }, type)
      is C.Term.TupleOf    -> C.Term.TupleOf(term.elements.map { zonkTerm(it) }, type)
      is C.Term.FuncOf     -> C.Term.FuncOf(zonkPattern(term.binder), zonkTerm(term.body), type)
      is C.Term.ClosOf     -> C.Term.ClosOf(zonkPattern(term.binder), zonkTerm(term.body), type)
      is C.Term.Apply      -> C.Term.Apply(zonkTerm(term.operator), zonkTerm(term.arg), type)
      is C.Term.If         -> C.Term.If(zonkTerm(term.condition), zonkTerm(term.thenClause), zonkTerm(term.elseClause), type)
      is C.Term.Let        -> C.Term.Let(zonkPattern(term.binder), zonkTerm(term.init), zonkTerm(term.body), type)
      is C.Term.Var        -> C.Term.Var(term.name, term.level, type)
      is C.Term.Run        -> C.Term.Run(term.name, term.typeArgs.map { zonkType(it) }, zonkTerm(term.arg), type)
      is C.Term.Is         -> C.Term.Is(zonkTerm(term.scrutinee), zonkPattern(term.scrutineer), type)
      is C.Term.Command    -> C.Term.Command(term.element, type)
      is C.Term.CodeOf     -> C.Term.CodeOf(zonkTerm(term.element), type)
      is C.Term.Splice     -> C.Term.Splice(zonkTerm(term.element), type)
      is C.Term.Hole       -> C.Term.Hole(type)
    }
  }

  private fun zonkPattern(
    pattern: C.Pattern,
  ): C.Pattern {
    val type = zonkType(pattern.type)
    return when (pattern) {
      is C.Pattern.IntOf      -> pattern
      is C.Pattern.IntRangeOf -> pattern
      is C.Pattern.ListOf     -> C.Pattern.ListOf(pattern.elements.map { zonkPattern(it) }, type)
      is C.Pattern.CompoundOf -> C.Pattern.CompoundOf(pattern.elements.mapValues { zonkPattern(it.value) }, type)
      is C.Pattern.TupleOf    -> C.Pattern.TupleOf(pattern.elements.map { zonkPattern(it) }, type)
      is C.Pattern.Var        -> C.Pattern.Var(pattern.name, pattern.level, type)
      is C.Pattern.Drop       -> C.Pattern.Drop(type)
      is C.Pattern.Hole       -> C.Pattern.Hole(type)
    }
  }

  private fun unsolvedMeta(
    type: C.Type,
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "unsolved meta: ${prettyType(type)}",
      DiagnosticSeverity.Error,
    )
  }

  data class Result(
    val module: C.Module,
    val diagnostics: List<Diagnostic>,
    val completionItems: List<CompletionItem>,
    val hover: (() -> String)?,
  )

  companion object {
    operator fun invoke(
      context: Context,
      input: Elaborate.Result,
    ): Result =
      Zonk(input).zonk()
  }
}
