package mcx.phase.frontend

import mcx.ast.DefinitionLocation
import mcx.ast.Modifier
import mcx.ast.ModuleLocation
import mcx.lsp.diagnostic
import mcx.phase.Context
import mcx.phase.frontend.Resolve.Env.Companion.emptyEnv
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Range
import mcx.ast.Resolved as R
import mcx.ast.Surface as S

class Resolve private constructor(
  dependencies: List<Dependency>,
  private val input: Parse.Result,
) {
  private val diagnostics: MutableList<Diagnostic> = mutableListOf()
  private val locations: List<DefinitionLocation> =
    input.module.definitions.mapNotNull { definition ->
      if (definition is S.Definition.Hole) {
        null
      } else {
        input.module.name / definition.name.value
      }
    } +
    dependencies.mapNotNull { dependency ->
      when (val definition = dependency.module) {
        null -> {
          diagnostics += nameNotFound(dependency.location.toString(), dependency.range!!)
          null
        }
        else -> {
          if (definition is R.Definition.Hole || !definition.modifiers.any { it.value == Modifier.EXPORT }) {
            null
          } else {
            definition.name.value
          }
        }
      }
    }

  private fun resolve(): Result {
    val module = resolveModule(input.module)
    return Result(
      module,
      input.diagnostics + diagnostics,
    )
  }

  private fun resolveModule(
    module: S.Module,
  ): R.Module {
    val definitions = module.definitions
      .mapNotNull { definition -> resolveDefinition(definition).takeUnless { it is R.Definition.Hole } }
      .associateBy { it.name.value }
    return R.Module(module.name, module.imports, definitions)
  }

  private fun resolveDefinition(
    definition: S.Definition,
  ): R.Definition {
    val range = definition.range
    return when (definition) {
      is S.Definition.Def  -> {
        val name = definition.name.map { input.module.name / it }
        val type = emptyEnv().resolveTerm(definition.type)
        val body = definition.body?.let { emptyEnv().resolveTerm(it) }
        R.Definition.Def(definition.modifiers, name, type, body, range)
      }
      is S.Definition.Hole -> R.Definition.Hole(range)
    }
  }

  private fun Env.resolveTerm(
    term: S.Term,
  ): R.Term {
    val range = term.range
    return when (term) {
      is S.Term.Tag         -> R.Term.Tag(range)
      is S.Term.TagOf       -> R.Term.TagOf(term.value, range)
      is S.Term.Type        -> {
        val tag = resolveTerm(term.tag)
        R.Term.Type(tag, range)
      }
      is S.Term.Bool        -> R.Term.Bool(range)
      is S.Term.BoolOf      -> R.Term.BoolOf(term.value, range)
      is S.Term.If          -> {
        val condition = resolveTerm(term.condition)
        val thenBranch = resolveTerm(term.thenBranch)
        val elseBranch = resolveTerm(term.elseBranch)
        R.Term.If(condition, thenBranch, elseBranch, range)
      }
      is S.Term.Is          -> {
        val scrutinee = resolveTerm(term.scrutinee)
        val scrutineer = resolvePattern(term.scrutineer)
        R.Term.Is(scrutinee, scrutineer, range)
      }
      is S.Term.Byte        -> R.Term.Byte(range)
      is S.Term.ByteOf      -> R.Term.ByteOf(term.value, range)
      is S.Term.Short       -> R.Term.Short(range)
      is S.Term.ShortOf     -> R.Term.ShortOf(term.value, range)
      is S.Term.Int         -> R.Term.Int(range)
      is S.Term.IntOf       -> R.Term.IntOf(term.value, range)
      is S.Term.Long        -> R.Term.Long(range)
      is S.Term.LongOf      -> R.Term.LongOf(term.value, range)
      is S.Term.Float       -> R.Term.Float(range)
      is S.Term.FloatOf     -> R.Term.FloatOf(term.value, range)
      is S.Term.Double      -> R.Term.Double(range)
      is S.Term.DoubleOf    -> R.Term.DoubleOf(term.value, range)
      is S.Term.String      -> R.Term.String(range)
      is S.Term.StringOf    -> R.Term.StringOf(term.value, range)
      is S.Term.ByteArray   -> R.Term.ByteArray(range)
      is S.Term.ByteArrayOf -> {
        val elements = term.elements.map { resolveTerm(it) }
        R.Term.ByteArrayOf(elements, range)
      }
      is S.Term.IntArray    -> R.Term.IntArray(range)
      is S.Term.IntArrayOf  -> {
        val elements = term.elements.map { resolveTerm(it) }
        R.Term.IntArrayOf(elements, range)
      }
      is S.Term.LongArray   -> R.Term.LongArray(range)
      is S.Term.LongArrayOf -> {
        val elements = term.elements.map { resolveTerm(it) }
        R.Term.LongArrayOf(elements, range)
      }
      is S.Term.List        -> {
        val element = resolveTerm(term.element)
        R.Term.List(element, range)
      }
      is S.Term.ListOf      -> {
        val elements = term.elements.map { resolveTerm(it) }
        R.Term.ListOf(elements, range)
      }
      is S.Term.Compound    -> {
        val elements = term.elements.map { (key, element) -> key to resolveTerm(element) }
        R.Term.Compound(elements, range)
      }
      is S.Term.CompoundOf  -> {
        val elements = term.elements.map { (key, element) -> key to resolveTerm(element) }
        R.Term.CompoundOf(elements, range)
      }
      is S.Term.Union       -> {
        val elements = term.elements.map { resolveTerm(it) }
        R.Term.Union(elements, range)
      }
      is S.Term.Func        -> {
        restoring {
          val params = term.params.map { (binder, type) -> resolvePattern(binder) to resolveTerm(type) }
          val result = resolveTerm(term.result)
          R.Term.Func(params, result, range)
        }
      }
      is S.Term.FuncOf      -> {
        restoring {
          val params = term.params.map { resolvePattern(it) }
          val result = resolveTerm(term.result)
          R.Term.FuncOf(params, result, range)
        }
      }
      is S.Term.Apply       -> {
        val func = resolveTerm(term.func)
        val args = term.args.map { resolveTerm(it) }
        R.Term.Apply(func, args, range)
      }
      is S.Term.Code        -> {
        val element = resolveTerm(term.element)
        R.Term.Code(element, range)
      }
      is S.Term.CodeOf      -> {
        val element = resolveTerm(term.element)
        R.Term.CodeOf(element, range)
      }
      is S.Term.Splice      -> {
        val element = resolveTerm(term.element)
        R.Term.Splice(element, range)
      }
      is S.Term.Let         -> {
        val init = resolveTerm(term.init)
        restoring {
          val binder = resolvePattern(term.binder)
          val body = resolveTerm(term.body)
          R.Term.Let(binder, init, body, range)
        }
      }
      is S.Term.Var         -> {
        when (val level = this[term.name]) {
          -1   -> {
            when (val name = resolveName(term.name, range)) {
              null -> R.Term.Hole(range)
              else -> R.Term.Def(name, range)
            }
          }
          else -> R.Term.Var(term.name, level, range)
        }
      }
      is S.Term.Hole        -> R.Term.Hole(range)
    }
  }

  private fun Env.resolvePattern(
    pattern: S.Pattern,
  ): R.Pattern {
    return when (pattern) {
      is S.Pattern.IntOf      -> R.Pattern.IntOf(pattern.value, pattern.range)
      is S.Pattern.CompoundOf -> {
        val elements = pattern.elements.map { (key, element) -> key to resolvePattern(element) }
        R.Pattern.CompoundOf(elements, pattern.range)
      }
      is S.Pattern.Splice     -> {
        val element = resolvePattern(pattern.element)
        R.Pattern.Splice(element, pattern.range)
      }
      is S.Pattern.Var        -> {
        bind(pattern.name)
        R.Pattern.Var(pattern.name, lastIndex, pattern.range)
      }
      is S.Pattern.Drop       -> R.Pattern.Drop(pattern.range)
      is S.Pattern.Anno       -> {
        val element = resolvePattern(pattern.element)
        val type = resolveTerm(pattern.type)
        R.Pattern.Anno(element, type, pattern.range)
      }
      is S.Pattern.Hole       -> R.Pattern.Hole(pattern.range)
    }
  }

  private fun resolveName(
    name: String,
    range: Range,
  ): DefinitionLocation? {
    val expected = name.split("::").let { ModuleLocation(it.dropLast(1)) / it.last() }
    val candidates = locations.filter { actual ->
      expected.module.parts.size <= actual.module.parts.size &&
      (expected.name == actual.name) &&
      (expected.module.parts.asReversed() zip actual.module.parts.asReversed()).all { it.first == it.second }
    }
    return when (candidates.size) {
      0    -> {
        diagnostics += nameNotFound(name, range)
        null
      }
      1    -> candidates.first()
      else -> {
        diagnostics += ambiguousName(name, range)
        null
      }
    }
  }

  private class Env private constructor(
    private val terms: MutableList<String>,
  ) {
    private var savedSize: Int = 0
    val lastIndex: Int get() = terms.lastIndex

    operator fun get(name: String): Int {
      return terms.lastIndexOf(name)
    }

    fun bind(name: String) {
      terms += name
    }

    inline fun <R> restoring(block: Env.() -> R): R {
      savedSize = terms.size
      val result = this.block()
      repeat(terms.size - savedSize) {
        terms.removeLast()
      }
      return result
    }

    companion object {
      fun emptyEnv(): Env {
        return Env(mutableListOf())
      }
    }
  }

  private fun nameNotFound(
    name: String,
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "name not found: '$name'",
      DiagnosticSeverity.Error,
    )
  }

  private fun ambiguousName(
    name: String,
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "ambiguous name: '$name'",
      DiagnosticSeverity.Error,
    )
  }

  data class Dependency(
    val location: DefinitionLocation,
    val module: R.Definition?,
    val range: Range?,
  )

  data class Result(
    val module: R.Module,
    val diagnostics: List<Diagnostic>,
  )

  companion object {
    operator fun invoke(
      context: Context,
      dependencies: List<Dependency>,
      input: Parse.Result,
    ): Result =
      Resolve(dependencies, input).resolve()
  }
}
