package mcx.pass.frontend

import mcx.ast.*
import mcx.lsp.HEADER
import mcx.lsp.Instruction
import mcx.lsp.contains
import mcx.lsp.diagnostic
import mcx.pass.Context
import mcx.pass.frontend.Resolve.Env.Companion.emptyEnv
import mcx.pass.lookupBuiltin
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Range
import kotlin.io.path.Path
import mcx.ast.Resolved as R
import mcx.ast.Surface as S

@Suppress("NAME_SHADOWING")
class Resolve private constructor(
  dependencies: List<Dependency>,
  private val input: Parse.Result,
  private val instruction: Instruction?,
) {
  private val diagnostics: MutableList<Diagnostic> = mutableListOf()
  private var definition: Location? = null
  private val locations: Set<DefinitionLocation> =
    input.module.definitions.mapNotNull { definition ->
      if (definition is S.Definition.Hole) {
        null
      } else {
        input.module.name / definition.name.value
      }
    }.toHashSet() +
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
      definition,
    )
  }

  private fun resolveModule(
    module: S.Module,
  ): R.Module {
    val definitions = linkedMapOf<DefinitionLocation, R.Definition>()
    module.definitions.forEach { definition ->
      val definition = resolveDefinition(definition)
      if (definition !is R.Definition.Hole) {
        if (definition.name.value in definitions) {
          diagnostics += duplicatedName(definition.name.value.name, definition.name.range)
        } else {
          definitions[definition.name.value] = definition
        }
      }
    }
    return R.Module(module.name, module.imports, definitions)
  }

  private fun resolveDefinition(
    definition: S.Definition,
  ): R.Definition {
    val range = definition.range
    if (definition is S.Definition.Hole) {
      return R.Definition.Hole(range)
    }

    val name = definition.name.map { input.module.name / it }
    return when (definition) {
      is S.Definition.Def  -> {
        val builtin = definition.modifiers.find { it.value == Modifier.BUILTIN }
        if (builtin != null && lookupBuiltin(name.value) == null) {
          diagnostics += undefinedBuiltin(name.value, builtin.range)
        }
        val type = emptyEnv().resolveTerm(definition.type)
        val body = definition.body?.let { emptyEnv().resolveTerm(it) }
        R.Definition.Def(definition.doc, definition.annotations, definition.modifiers, name, type, body, range)
      }
      is S.Definition.Hole -> error("unreachable")
    }
  }

  private fun Env.resolveTerm(
    term: S.Term,
  ): R.Term {
    val range = term.range
    return when (term) {
      is S.Term.Tag        -> {
        R.Term.Tag(term.range)
      }

      is S.Term.TagOf      -> {
        R.Term.TagOf(term.value, range)
      }

      is S.Term.Type       -> {
        val tag = resolveTerm(term.element)
        R.Term.Type(tag, range)
      }

      is S.Term.Bool       -> {
        R.Term.Bool(range)
      }

      is S.Term.BoolOf     -> {
        R.Term.BoolOf(term.value, range)
      }

      is S.Term.If         -> {
        val condition = resolveTerm(term.condition)
        val thenBranch = resolveTerm(term.thenBranch)
        val elseBranch = resolveTerm(term.elseBranch)
        R.Term.If(condition, thenBranch, elseBranch, range)
      }

      is S.Term.I8         -> {
        R.Term.I8(range)
      }

      is S.Term.I8Of       -> {
        R.Term.I8Of(term.value, range)
      }

      is S.Term.I16        -> {
        R.Term.I16(range)
      }

      is S.Term.I16Of      -> {
        R.Term.I16Of(term.value, range)
      }

      is S.Term.I32        -> {
        R.Term.I32(range)
      }

      is S.Term.I32Of      -> {
        R.Term.I32Of(term.value, range)
      }

      is S.Term.I64        -> {
        R.Term.I64(range)
      }

      is S.Term.I64Of      -> {
        R.Term.I64Of(term.value, range)
      }

      is S.Term.F32        -> {
        R.Term.F32(range)
      }

      is S.Term.F32Of      -> {
        R.Term.F32Of(term.value, range)
      }

      is S.Term.F64        -> {
        R.Term.F64(range)
      }

      is S.Term.F64Of      -> {
        R.Term.F64Of(term.value, range)
      }

      is S.Term.Str        -> {
        R.Term.Str(range)
      }

      is S.Term.StrOf      -> {
        R.Term.StrOf(term.value, range)
      }

      is S.Term.I8Array    -> {
        R.Term.I8Array(range)
      }

      is S.Term.I8ArrayOf  -> {
        val elements = term.elements.map { resolveTerm(it) }
        R.Term.I8ArrayOf(elements, range)
      }

      is S.Term.I32Array   -> {
        R.Term.I32Array(range)
      }

      is S.Term.I32ArrayOf -> {
        val elements = term.elements.map { resolveTerm(it) }
        R.Term.I32ArrayOf(elements, range)
      }

      is S.Term.I64Array   -> {
        R.Term.I64Array(range)
      }

      is S.Term.I64ArrayOf -> {
        val elements = term.elements.map { resolveTerm(it) }
        R.Term.I64ArrayOf(elements, range)
      }

      is S.Term.Vec        -> {
        val element = resolveTerm(term.element)
        R.Term.Vec(element, range)
      }

      is S.Term.ListOf     -> {
        val elements = term.elements.map { resolveTerm(it) }
        R.Term.VecOf(elements, range)
      }

      is S.Term.Struct     -> {
        val elements = term.elements.map { (key, element) -> key to resolveTerm(element) }
        R.Term.Struct(elements, range)
      }

      is S.Term.StructOf   -> {
        val elements = term.elements.map { (key, element) -> key to resolveTerm(element) }
        R.Term.StructOf(elements, range)
      }

      is S.Term.Ref        -> {
        val element = resolveTerm(term.element)
        R.Term.Ref(element, range)
      }

      is S.Term.RefOf      -> {
        val element = resolveTerm(term.element)
        R.Term.RefOf(element, range)
      }

      is S.Term.Point      -> {
        val element = resolveTerm(term.element)
        R.Term.Point(element, range)
      }

      is S.Term.Union      -> {
        val elements = term.elements.map { resolveTerm(it) }
        R.Term.Union(elements, range)
      }

      is S.Term.Func       -> {
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

      is S.Term.FuncOf      -> {
        restoring(if (term.open) 0 else size) {
          val params = term.params.map { resolvePattern(it) }
          val result = resolveTerm(term.result)
          R.Term.FuncOf(term.open, params, result, range)
        }
      }

      is S.Term.Apply       -> {
        val func = resolveTerm(term.func)
        val args = term.args.map { resolveTerm(it) }
        R.Term.Apply(func, args, range)
      }

      is S.Term.Code        -> {
        val element = resolveTerm(term.element)
        R.Term.Code(element, term.range)
      }

      is S.Term.CodeOf      -> {
        val element = resolveTerm(term.element)
        R.Term.CodeOf(element, term.range)
      }

      is S.Term.Splice      -> {
        val element = resolveTerm(term.element)
        R.Term.Splice(element, term.range)
      }

      is S.Term.Command     -> {
        val element = resolveTerm(term.element)
        R.Term.Command(element, term.range)
      }

      is S.Term.Let         -> {
        val init = resolveTerm(term.init)
        restoring(0) {
          val binder = resolvePattern(term.binder)
          val body = resolveTerm(term.body)
          R.Term.Let(binder, init, body, range)
        }
      }

      is S.Term.Var        -> {
        when (term.name) {
          "_"  -> R.Term.Meta(range)
          else -> {
            when (val level = lookup(term.name)) {
              -1   -> {
                when (val name = resolveName(term.name, range)) {
                  null -> R.Term.Hole(range)
                  else -> {
                    definition(name, range)
                    R.Term.Def(name, range)
                  }
                }
              }
              else -> {
                val index = Lvl(size).toIdx(Lvl(level))
                R.Term.Var(term.name, index, range)
              }
            }
          }
        }
      }

      is S.Term.As         -> {
        val element = resolveTerm(term.element)
        val type = resolveTerm(term.type)
        R.Term.As(element, type, range)
      }

      is S.Term.Hole       -> {
        R.Term.Hole(range)
      }
    }
  }

  private fun Env.resolvePattern(
    pattern: S.Term,
  ): R.Pattern {
    return when (pattern) {
      is S.Term.I32Of -> {
        R.Pattern.I32Of(pattern.value, pattern.range)
      }

      is S.Term.Var   -> {
        when (pattern.name) {
          "_"  -> R.Pattern.Drop(pattern.range)
          else -> {
            bind(pattern.name)
            R.Pattern.Var(pattern.name, pattern.range)
          }
        }
      }

      is S.Term.As    -> {
        val type = resolveTerm(pattern.type)
        val element = resolvePattern(pattern.element)
        R.Pattern.As(element, type, pattern.range)
      }

      is S.Term.Hole  -> {
        R.Pattern.Hole(pattern.range)
      }

      else            -> {
        diagnostics += unexpectedPattern(pattern.range)
        R.Pattern.Hole(pattern.range)
      }
    }
  }

  private fun resolveName(
    name: String,
    range: Range,
  ): DefinitionLocation? {
    val expected = name.split("::").let {
      ModuleLocation(it.dropLast(1)) / it.last()
    }
    val candidates = locations.filter { actual -> // TODO: optimize search
      expected.module.parts.size <= actual.module.parts.size &&
      (expected.name == actual.name) &&
      (expected.module.parts.asReversed() zip actual.module.parts.asReversed()).all { it.first == it.second }
    }
    return when (candidates.size) {
      0    -> {
        diagnostics += nameNotFound(name, range)
        null
      }
      1    -> {
        candidates.first()
      }
      else -> {
        diagnostics += ambiguousName(name, range)
        null
      }
    }
  }

  // TODO: support goto definitions in module
  private fun definition(
    name: DefinitionLocation,
    range: Range,
  ) {
    if (definition == null && instruction is Instruction.Definition && instruction.position in range) {
      // TODO: goto prelude
      definition = Location(Path(name.module.parts.drop(1).joinToString("/", "src/", ".mcx")).toUri().toString(), HEADER)
    }
  }

  private class Env private constructor(
    private val _terms: MutableList<String>,
  ) {
    private var frontier: Int = 0
    val size: Int get() = _terms.size

    fun bind(name: String) {
      _terms += name
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
    val diagnostics: List<Diagnostic>,
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
