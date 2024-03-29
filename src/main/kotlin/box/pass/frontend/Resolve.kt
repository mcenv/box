package box.pass.frontend

import box.ast.Surface
import box.ast.common.*
import box.lsp.Instruction
import box.lsp.contains
import box.lsp.diagnostic
import box.pass.frontend.Resolve.Env.Companion.emptyEnv
import box.pass.frontend.parse.Parse
import box.pass.lookupBuiltin
import box.util.unreachable
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Range
import kotlin.io.path.Path
import box.ast.Resolved as R
import box.ast.Surface as S

/**
 * Resolves all names in a module.
 * Also converts surface syntax to terms and patterns.
 */
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
      if (definition is S.Definition.Hole) {
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

  private fun resolveModule(module: S.Module): R.Module {
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

  private fun resolveDefinition(definition: S.Definition): R.Definition {
    val range = definition.range
    if (definition is S.Definition.Hole) {
      return R.Definition.Hole(range)
    }

    val name = definition.name.map { input.module.name / it }
    location = name.value

    run {
      var inline = false
      var rec = false
      definition.modifiers.forEach {
        when (it.value) {
          Modifier.INLINE -> {
            inline = true
            if (rec) {
              diagnose(inlineAndRecCannotBeMixed(it.range))
            }
          }
          Modifier.REC    -> {
            rec = true
            if (inline) {
              diagnose(inlineAndRecCannotBeMixed(it.range))
            }
          }
          else            -> {}
        }
      }
    }

    return when (definition) {
      is S.Definition.Def  -> {
        val type = emptyEnv().resolveTerm(definition.type)
        val body = definition.body.let { emptyEnv().resolveTerm(it) }
        R.Definition.Def(definition.doc, definition.annotations, definition.modifiers, name, type, body, range)
      }
      is S.Definition.Hole -> unreachable()
    }
  }

  private fun Env.resolveTerm(term: S.Term): R.Term {
    val range = term.range
    return when (term) {
      is S.Term.Tag       -> {
        R.Term.Tag(term.range)
      }

      is S.Term.TagOf     -> {
        R.Term.TagOf(term.repr, range)
      }

      is S.Term.Type      -> {
        val tag = resolveTerm(term.element)
        R.Term.Type(tag, range)
      }

      is S.Term.Unit      -> {
        R.Term.Unit(range)
      }

      is S.Term.Bool      -> {
        R.Term.Bool(range)
      }

      is S.Term.I8        -> {
        R.Term.I8(range)
      }

      is S.Term.I16       -> {
        R.Term.I16(range)
      }

      is S.Term.I32       -> {
        R.Term.I32(range)
      }

      is S.Term.I64       -> {
        R.Term.I64(range)
      }

      is S.Term.F32       -> {
        R.Term.F32(range)
      }

      is S.Term.F64       -> {
        R.Term.F64(range)
      }

      is S.Term.Wtf16     -> {
        R.Term.Wtf16(range)
      }

      is S.Term.ConstOf   -> {
        R.Term.ConstOf(term.value, range)
      }

      is S.Term.I8Array   -> {
        R.Term.I8Array(range)
      }

      is S.Term.I8ArrayOf -> {
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

      is Surface.Term.List -> {
        val element = resolveTerm(term.element)
        R.Term.List(element, range)
      }

      is S.Term.ListOf     -> {
        val elements = term.elements.map { resolveTerm(it) }
        R.Term.ListOf(elements, range)
      }

      is S.Term.Compound   -> {
        val elements = term.elements.map { (key, element) -> key to resolveTerm(element) }
        R.Term.Compound(elements, range)
      }

      is S.Term.CompoundOf -> {
        val elements = term.elements.map { (key, element) -> key to resolveTerm(element) }
        R.Term.CompoundOf(elements, range)
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

      is S.Term.FuncOf     -> {
        restoring(if (term.open) 0 else size) {
          val params = term.params.map { resolvePattern(it) }
          val result = resolveTerm(term.result)
          R.Term.FuncOf(term.open, params, result, range)
        }
      }

      is S.Term.Apply     -> {
        val func = resolveTerm(term.func)
        val args = term.args.map { resolveTerm(it) }
        R.Term.Apply(func, args, range)
      }

      is S.Term.Code      -> {
        val element = resolveTerm(term.element)
        R.Term.Code(element, range)
      }

      is S.Term.CodeOf    -> {
        val element = resolveTerm(term.element)
        R.Term.CodeOf(element, range)
      }

      is S.Term.Splice    -> {
        val element = resolveTerm(term.element)
        R.Term.Splice(element, range)
      }

      is S.Term.Path      -> {
        val element = resolveTerm(term.element)
        R.Term.Path(element, range)
      }

      is S.Term.PathOf    -> {
        val element = resolveTerm(term.element)
        R.Term.PathOf(element, range)
      }

      is S.Term.Get       -> {
        val element = resolveTerm(term.element)
        R.Term.Get(element, range)
      }

      is S.Term.Command   -> {
        val element = resolveTerm(term.element)
        R.Term.Command(element, range)
      }

      is S.Term.Let       -> {
        val init = resolveTerm(term.init)
        restoring(0) {
          val binder = resolvePattern(term.binder)
          val body = resolveTerm(term.body)
          R.Term.Let(binder, init, body, range)
        }
      }

      is S.Term.If        -> {
        val scrutinee = resolveTerm(term.scrutinee)
        val branches = term.branches.map { (pattern, body) ->
          restoring(0) {
            val pattern = resolvePattern(pattern)
            val body = resolveTerm(body)
            pattern to body
          }
        }
        R.Term.If(scrutinee, branches, range)
      }

      is S.Term.Var       -> {
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

      is S.Term.As        -> {
        val element = resolveTerm(term.element)
        val type = resolveTerm(term.type)
        R.Term.As(element, type, range)
      }

      is S.Term.Builtin   -> {
        when (val builtin = lookupBuiltin(term.name)) {
          null -> {
            diagnose(undefinedBuiltin(term.name, range))
            R.Term.Hole(range)
          }
          else -> {
            R.Term.Builtin(builtin, range)
          }
        }
      }

      is S.Term.Hole      -> {
        R.Term.Hole(range)
      }
    }
  }

  private fun Env.resolvePattern(pattern: S.Term): R.Pattern {
    val range = pattern.range
    return when (pattern) {
      is S.Term.ConstOf    -> {
        R.Pattern.ConstOf(pattern.value, range)
      }

      is S.Term.I8ArrayOf  -> {
        val elements = pattern.elements.map { resolvePattern(it) }
        R.Pattern.I8ArrayOf(elements, range)
      }

      is S.Term.I32ArrayOf -> {
        val elements = pattern.elements.map { resolvePattern(it) }
        R.Pattern.I32ArrayOf(elements, range)
      }

      is S.Term.I64ArrayOf -> {
        val elements = pattern.elements.map { resolvePattern(it) }
        R.Pattern.I64ArrayOf(elements, range)
      }

      is S.Term.ListOf     -> {
        val elements = pattern.elements.map { resolvePattern(it) }
        R.Pattern.ListOf(elements, range)
      }

      is S.Term.CompoundOf -> {
        val elements = pattern.elements.map { (key, element) -> key to resolvePattern(element) }
        R.Pattern.CompoundOf(elements, range)
      }

      is S.Term.Var        -> {
        when (pattern.name) {
          "_"  -> R.Pattern.Drop(range)
          else -> {
            bind(pattern.name, range)
            R.Pattern.Var(pattern.name, range)
          }
        }
      }

      is S.Term.As         -> {
        val type = resolveTerm(pattern.type)
        val element = resolvePattern(pattern.element)
        R.Pattern.As(element, type, range)
      }

      is S.Term.Hole       -> {
        R.Pattern.Hole(range)
      }

      else -> {
        diagnose(unexpectedPattern(range))
        R.Pattern.Hole(range)
      }
    }
  }

  private fun resolveName(name: String, range: Range): Map.Entry<DefinitionLocation, Range>? {
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

  private fun definitionVar(def: Range, use: Range) {
    if (definition == null && instruction is Instruction.Definition && instruction.position in use) {
      definition = Location(Path(input.module.name.parts.drop(1).joinToString("/", "src/", ".box")).toUri().toString(), def)
    }
  }

  // TODO: support goto definitions in module
  private fun definitionDef(name: DefinitionLocation, def: Range, use: Range) {
    if (definition == null && instruction is Instruction.Definition && instruction.position in use) {
      // TODO: goto prelude
      definition = Location(Path(name.module.parts.drop(1).joinToString("/", "src/", ".box")).toUri().toString(), def)
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

  private fun nameNotFound(name: String, range: Range): Diagnostic {
    return diagnostic(
      range,
      "name not found: $name",
      DiagnosticSeverity.Error,
    )
  }

  private fun duplicatedName(name: String, range: Range): Diagnostic {
    return diagnostic(
      range,
      "duplicated name: $name",
      DiagnosticSeverity.Error,
    )
  }

  private fun ambiguousName(name: String, range: Range): Diagnostic {
    return diagnostic(
      range,
      "ambiguous name: $name",
      DiagnosticSeverity.Error,
    )
  }

  private fun undefinedBuiltin(name: String, range: Range): Diagnostic {
    return diagnostic(
      range,
      "undefined builtin: $name",
      DiagnosticSeverity.Error,
    )
  }

  private fun unexpectedPattern(range: Range): Diagnostic {
    return diagnostic(
      range,
      "unexpected pattern",
      DiagnosticSeverity.Error,
    )
  }

  private fun inlineAndRecCannotBeMixed(range: Range): Diagnostic {
    return diagnostic(
      range,
      "inline and rec cannot be mixed",
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
      dependencies: List<Dependency>,
      input: Parse.Result,
      instruction: Instruction?,
    ): Result {
      return Resolve(dependencies, input, instruction).resolve()
    }
  }
}
