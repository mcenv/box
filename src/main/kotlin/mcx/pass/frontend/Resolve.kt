package mcx.pass.frontend

import mcx.ast.*
import mcx.lsp.Instruction
import mcx.lsp.contains
import mcx.lsp.diagnostic
import mcx.pass.Context
import mcx.pass.frontend.Resolve.Env.Companion.emptyEnv
import mcx.pass.frontend.parse.Parse
import mcx.pass.lookupBuiltin
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Range
import kotlin.io.path.Path
import mcx.ast.Parsed as P
import mcx.ast.Resolved as R

@Suppress("NAME_SHADOWING")
class Resolve private constructor(
  dependencies: List<Dependency>,
  private val input: Parse.Result,
  private val instruction: Instruction?,
) {
  private lateinit var location: DefinitionLocation
  private val diagnostics: MutableMap<DefinitionLocation?, MutableList<Diagnostic>> = hashMapOf()
  private var definition: Location? = null
  private val locations: Map<DefinitionLocation, Range> =
    input.module.definitions.mapNotNull { definition ->
      if (definition is P.Definition.Hole) {
        null
      } else {
        (input.module.name / definition.name.value) to definition.name.range
      }
    }.toMap() +
    dependencies.mapNotNull { dependency ->
      when (val definition = dependency.module) {
        null -> {
          diagnostics.computeIfAbsent(null) { mutableListOf() } += nameNotFound(dependency.location.toString(), dependency.range!!)
          null
        }
        else -> {
          if (definition is R.Definition.Hole || !definition.modifiers.any { it.value == Modifier.EXPORT }) {
            null
          } else {
            definition.name.value to definition.name.range
          }
        }
      }
    }

  private fun resolve(): Result {
    val module = resolveModule(input.module)
    return Result(
      module,
      diagnostics.also { it.computeIfAbsent(null) { mutableListOf() } += input.diagnostics },
      definition,
    )
  }

  private fun resolveModule(
    module: P.Module,
  ): R.Module {
    val definitions = linkedMapOf<DefinitionLocation, R.Definition>()
    module.definitions.forEach { definition ->
      val definition = resolveDefinition(definition)
      if (definition !is R.Definition.Hole) {
        if (definition.name.value in definitions) {
          diagnose(duplicatedName(definition.name.value.name, definition.name.range))
        } else {
          definitions[definition.name.value] = definition
        }
      }
    }
    module.imports.forEach { (name, use) ->
      locations[name]?.let { def ->
        definitionDef(name, def, use)
      }
    }
    return R.Module(module.name, module.imports, definitions)
  }

  private fun resolveDefinition(
    definition: P.Definition,
  ): R.Definition {
    val range = definition.range
    if (definition is P.Definition.Hole) {
      return R.Definition.Hole(range)
    }

    val name = definition.name.map { input.module.name / it }
    location = name.value
    return when (definition) {
      is P.Definition.Def  -> {
        val builtin = definition.modifiers.find { it.value == Modifier.BUILTIN }
        if (builtin != null && lookupBuiltin(name.value) == null) {
          diagnose(undefinedBuiltin(name.value, builtin.range))
        }
        val type = emptyEnv().resolveTerm(definition.type)
        val body = definition.body?.let { emptyEnv().resolveTerm(it) }
        R.Definition.Def(definition.doc, definition.annotations, definition.modifiers, name, type, body, range)
      }
      is P.Definition.Hole -> error("Unreachable")
    }
  }

  private fun Env.resolveTerm(
    term: P.Term,
  ): R.Term {
    val range = term.range
    return when (term) {
      is P.Term.Tag        -> {
        R.Term.Tag(term.range)
      }

      is P.Term.TagOf      -> {
        R.Term.TagOf(term.repr, range)
      }

      is P.Term.Type       -> {
        val tag = resolveTerm(term.element)
        R.Term.Type(tag, range)
      }

      is P.Term.Bool       -> {
        R.Term.Bool(range)
      }

      is P.Term.BoolOf     -> {
        R.Term.BoolOf(term.value, range)
      }

      is P.Term.If         -> {
        val condition = resolveTerm(term.condition)
        val thenBranch = resolveTerm(term.thenBranch)
        val elseBranch = resolveTerm(term.elseBranch)
        R.Term.If(condition, thenBranch, elseBranch, range)
      }

      is P.Term.I8         -> {
        R.Term.I8(range)
      }

      is P.Term.I16        -> {
        R.Term.I16(range)
      }

      is P.Term.I32        -> {
        R.Term.I32(range)
      }

      is P.Term.I64        -> {
        R.Term.I64(range)
      }

      is P.Term.F32        -> {
        R.Term.F32(range)
      }

      is P.Term.F64        -> {
        R.Term.F64(range)
      }

      is P.Term.NumOf      -> {
        R.Term.NumOf(term.inferred, term.value, range)
      }

      is P.Term.Str        -> {
        R.Term.Str(range)
      }

      is P.Term.StrOf      -> {
        R.Term.StrOf(term.value, range)
      }

      is P.Term.I8Array    -> {
        R.Term.I8Array(range)
      }

      is P.Term.I8ArrayOf  -> {
        val elements = term.elements.map { resolveTerm(it) }
        R.Term.I8ArrayOf(elements, range)
      }

      is P.Term.I32Array   -> {
        R.Term.I32Array(range)
      }

      is P.Term.I32ArrayOf -> {
        val elements = term.elements.map { resolveTerm(it) }
        R.Term.I32ArrayOf(elements, range)
      }

      is P.Term.I64Array   -> {
        R.Term.I64Array(range)
      }

      is P.Term.I64ArrayOf -> {
        val elements = term.elements.map { resolveTerm(it) }
        R.Term.I64ArrayOf(elements, range)
      }

      is P.Term.Vec        -> {
        val element = resolveTerm(term.element)
        R.Term.Vec(element, range)
      }

      is P.Term.ListOf     -> {
        val elements = term.elements.map { resolveTerm(it) }
        R.Term.VecOf(elements, range)
      }

      is P.Term.Struct     -> {
        val elements = term.elements.map { (key, element) -> key to resolveTerm(element) }
        R.Term.Struct(elements, range)
      }

      is P.Term.StructOf   -> {
        val elements = term.elements.map { (key, element) -> key to resolveTerm(element) }
        R.Term.StructOf(elements, range)
      }

      is P.Term.Ref        -> {
        val element = resolveTerm(term.element)
        R.Term.Ref(element, range)
      }

      is P.Term.RefOf      -> {
        val element = resolveTerm(term.element)
        R.Term.RefOf(element, range)
      }

      is P.Term.Point      -> {
        val element = resolveTerm(term.element)
        R.Term.Point(element, range)
      }

      is P.Term.Union      -> {
        val elements = term.elements.map { resolveTerm(it) }
        R.Term.Union(elements, range)
      }

      is P.Term.Func       -> {
        restoring(0) {
          val params = term.params.map { (binder, type) ->
            val type = resolveTerm(type)
            val binder = resolvePattern(binder)
            binder to type
          }
          val result = resolveTerm(term.result)
          R.Term.Func(term.open, params, result, range)
        }
      }

      is P.Term.FuncOf     -> {
        restoring(if (term.open) 0 else size) {
          val params = term.params.map { resolvePattern(it) }
          val result = resolveTerm(term.result)
          R.Term.FuncOf(term.open, params, result, range)
        }
      }

      is P.Term.Apply      -> {
        val func = resolveTerm(term.func)
        val args = term.args.map { resolveTerm(it) }
        R.Term.Apply(func, args, range)
      }

      is P.Term.Code       -> {
        val element = resolveTerm(term.element)
        R.Term.Code(element, term.range)
      }

      is P.Term.CodeOf     -> {
        val element = resolveTerm(term.element)
        R.Term.CodeOf(element, term.range)
      }

      is P.Term.Splice     -> {
        val element = resolveTerm(term.element)
        R.Term.Splice(element, term.range)
      }

      is P.Term.Command    -> {
        val element = resolveTerm(term.element)
        R.Term.Command(element, term.range)
      }

      is P.Term.Let        -> {
        val init = resolveTerm(term.init)
        restoring(0) {
          val binder = resolvePattern(term.binder)
          val body = resolveTerm(term.body)
          R.Term.Let(binder, init, body, range)
        }
      }

      is P.Term.Match      -> {
        val scrutinee = resolveTerm(term.scrutinee)
        val branches = term.branches.map { (pattern, body) ->
          restoring(0) {
            val pattern = resolvePattern(pattern)
            val body = resolveTerm(body)
            pattern to body
          }
        }
        R.Term.Match(scrutinee, branches, range)
      }

      is P.Term.Var        -> {
        when (term.name) {
          "_"  -> R.Term.Meta(range)
          else -> {
            when (val level = lookup(term.name)) {
              -1   -> {
                when (val resolved = resolveName(term.name, range)) {
                  null -> R.Term.Hole(range)
                  else -> {
                    val (name, def) = resolved
                    definitionDef(name, def, range)
                    R.Term.Def(name, range)
                  }
                }
              }
              else -> {
                definitionVar(ranges[level], range)
                val index = Lvl(level).toIdx(Lvl(size))
                R.Term.Var(term.name, index, range)
              }
            }
          }
        }
      }

      is P.Term.As         -> {
        val element = resolveTerm(term.element)
        val type = resolveTerm(term.type)
        R.Term.As(element, type, range)
      }

      is P.Term.Hole       -> {
        R.Term.Hole(range)
      }
    }
  }

  private fun Env.resolvePattern(
    pattern: P.Term,
  ): R.Pattern {
    val range = pattern.range
    return when (pattern) {
      is P.Term.NumOf    -> {
        R.Pattern.I32Of(pattern.value.toInt() /* TODO */, range)
      }

      is P.Term.StructOf -> {
        val elements = pattern.elements.map { (key, element) -> key to resolvePattern(element) }
        R.Pattern.StructOf(elements, range)
      }

      is P.Term.Var      -> {
        when (pattern.name) {
          "_"  -> R.Pattern.Drop(range)
          else -> {
            bind(pattern.name, range)
            R.Pattern.Var(pattern.name, range)
          }
        }
      }

      is P.Term.As       -> {
        val type = resolveTerm(pattern.type)
        val element = resolvePattern(pattern.element)
        R.Pattern.As(element, type, range)
      }

      is P.Term.Hole     -> {
        R.Pattern.Hole(range)
      }

      else               -> {
        diagnose(unexpectedPattern(range))
        R.Pattern.Hole(range)
      }
    }
  }

  private fun resolveName(
    name: String,
    range: Range,
  ): Map.Entry<DefinitionLocation, Range>? {
    val expected = name.split("::").let {
      ModuleLocation(it.dropLast(1)) / it.last()
    }
    val candidates = locations.filter { (actual, _) -> // TODO: optimize search
      expected.module.parts.size <= actual.module.parts.size &&
      (expected.name == actual.name) &&
      (expected.module.parts.asReversed() zip actual.module.parts.asReversed()).all { it.first == it.second }
    }
    return when (candidates.size) {
      0    -> {
        diagnose(nameNotFound(name, range))
        null
      }
      1    -> {
        candidates.entries.first()
      }
      else -> {
        diagnose(ambiguousName(name, range))
        null
      }
    }
  }

  private fun definitionVar(
    def: Range,
    use: Range,
  ) {
    if (definition == null && instruction is Instruction.Definition && instruction.position in use) {
      definition = Location(Path(input.module.name.parts.drop(1).joinToString("/", "src/", ".mcx")).toUri().toString(), def)
    }
  }

  // TODO: support goto definitions in module
  private fun definitionDef(
    name: DefinitionLocation,
    def: Range,
    use: Range,
  ) {
    if (definition == null && instruction is Instruction.Definition && instruction.position in use) {
      // TODO: goto prelude
      definition = Location(Path(name.module.parts.drop(1).joinToString("/", "src/", ".mcx")).toUri().toString(), def)
    }
  }

  private fun diagnose(diagnostic: Diagnostic) {
    diagnostics.computeIfAbsent(location) { mutableListOf() } += diagnostic
  }

  private class Env private constructor(
    private val _terms: MutableList<String>,
    private val _ranges: MutableList<Range>,
  ) {
    private var frontier: Int = 0
    val size: Int get() = _terms.size
    val ranges: List<Range> get() = _ranges

    fun bind(name: String, range: Range) {
      _terms += name
      _ranges += range
    }

    fun lookup(name: String): Int {
      for (i in _terms.lastIndex downTo frontier) {
        if (_terms[i] == name) {
          return i
        }
      }
      return -1
    }

    inline fun <R> restoring(frontier: Int, block: Env.() -> R): R {
      val restoreFrontier = this.frontier
      this.frontier = frontier
      val restoreSize = _terms.size

      val result = this.block()

      this.frontier = restoreFrontier
      repeat(_terms.size - restoreSize) {
        _terms.removeLast()
        _ranges.removeLast()
      }

      return result
    }

    companion object {
      fun emptyEnv(): Env {
        return Env(mutableListOf(), mutableListOf())
      }
    }
  }

  private fun nameNotFound(
    name: String,
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "name not found: $name",
      DiagnosticSeverity.Error,
    )
  }

  private fun duplicatedName(
    name: String,
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "duplicated name: $name",
      DiagnosticSeverity.Error,
    )
  }

  private fun ambiguousName(
    name: String,
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "ambiguous name: $name",
      DiagnosticSeverity.Error,
    )
  }

  private fun undefinedBuiltin(
    name: DefinitionLocation,
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "undefined builtin: $name",
      DiagnosticSeverity.Error,
    )
  }

  private fun unexpectedPattern(
    range: Range,
  ): Diagnostic {
    return diagnostic(
      range,
      "unexpected pattern",
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
    val diagnostics: Map<DefinitionLocation?, List<Diagnostic>>,
    val definition: Location?,
  )

  companion object {
    operator fun invoke(
      context: Context,
      dependencies: List<Dependency>,
      input: Parse.Result,
      instruction: Instruction?,
    ): Result {
      return Resolve(dependencies, input, instruction).resolve()
    }
  }
}
