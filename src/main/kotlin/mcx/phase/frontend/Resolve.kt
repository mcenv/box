package mcx.phase.frontend

import mcx.ast.Annotation
import mcx.ast.DefinitionLocation
import mcx.ast.ModuleLocation
import mcx.phase.Context
import mcx.phase.frontend.Resolve.Env.Companion.emptyEnv
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
    dependencies.flatMap { dependency ->
      when (val module = dependency.module) {
        null -> {
          diagnostics += Diagnostic.ModuleNotFound(dependency.location, dependency.range!!)
          emptyList()
        }
        else -> {
          module.definitions.mapNotNull { definition ->
            if (definition is R.Definition.Hole || !definition.annotations.any { it.value == Annotation.Export }) {
              null
            } else {
              definition.name.value
            }
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
    val definitions = module.definitions.map { resolveDefinition(it) }
    return R.Module(module.name, module.imports, definitions)
  }

  private fun resolveDefinition(
    definition: S.Definition,
  ): R.Definition {
    return when (definition) {
      is S.Definition.Resource -> {
        val name = definition.name.map { input.module.name / it }
        val env = emptyEnv(emptyList())
        val body = env.resolveTerm(definition.body)
        R.Definition.Resource(definition.annotations, definition.registry, name, body, definition.range)
      }
      is S.Definition.Function -> {
        val name = definition.name.map { input.module.name / it }
        val env = emptyEnv(definition.typeParams)
        val binder = env.resolvePattern(definition.binder)
        val result = env.resolveType(definition.result)
        val body = env.resolveTerm(definition.body)
        R.Definition.Function(definition.annotations, name, definition.typeParams, binder, result, body, definition.range)
      }
      is S.Definition.Type     -> {
        val name = definition.name.map { input.module.name / it }
        val env = emptyEnv(emptyList())
        val body = env.resolveType(definition.body)
        R.Definition.Type(definition.annotations, name, body, definition.range)
      }
      is S.Definition.Hole     -> R.Definition.Hole(definition.range)
    }
  }

  private fun Env.resolveType(
    type: S.Type,
  ): R.Type {
    return when (type) {
      is S.Type.Bool      -> R.Type.Bool(type.value, type.range)
      is S.Type.Byte      -> R.Type.Byte(type.value, type.range)
      is S.Type.Short     -> R.Type.Short(type.value, type.range)
      is S.Type.Int       -> R.Type.Int(type.value, type.range)
      is S.Type.Long      -> R.Type.Long(type.value, type.range)
      is S.Type.Float     -> R.Type.Float(type.value, type.range)
      is S.Type.Double    -> R.Type.Double(type.value, type.range)
      is S.Type.String    -> R.Type.String(type.value, type.range)
      is S.Type.ByteArray -> R.Type.ByteArray(type.range)
      is S.Type.IntArray  -> R.Type.IntArray(type.range)
      is S.Type.LongArray -> R.Type.LongArray(type.range)
      is S.Type.List      -> R.Type.List(resolveType(type.element), type.range)
      is S.Type.Compound  -> R.Type.Compound(type.elements.mapValues { resolveType(it.value) }, type.range)
      is S.Type.Ref       -> R.Type.Ref(resolveType(type.element), type.range)
      is S.Type.Tuple     -> R.Type.Tuple(type.elements.map { resolveType(it) }, type.range)
      is S.Type.Fun       -> R.Type.Fun(resolveType(type.param), resolveType(type.result), type.range)
      is S.Type.Union     -> R.Type.Union(type.elements.map { resolveType(it) }, type.range)
      is S.Type.Code      -> R.Type.Code(resolveType(type.element), type.range)
      is S.Type.Var       -> {
        when (val level = getTypeVar(type.name)) {
          -1   -> {
            when (val location = resolveName(type.name, type.range)) {
              null -> R.Type.Hole(type.range)
              else -> R.Type.Run(location, type.range)
            }
          }
          else -> R.Type.Var(type.name, level, type.range)
        }
      }
      is S.Type.Hole      -> R.Type.Hole(type.range)
    }
  }

  private fun Env.resolveTerm(
    term: S.Term,
  ): R.Term {
    return when (term) {
      is S.Term.BoolOf      -> R.Term.BoolOf(term.value, term.range)
      is S.Term.ByteOf      -> R.Term.ByteOf(term.value, term.range)
      is S.Term.ShortOf     -> R.Term.ShortOf(term.value, term.range)
      is S.Term.IntOf       -> R.Term.IntOf(term.value, term.range)
      is S.Term.LongOf      -> R.Term.LongOf(term.value, term.range)
      is S.Term.FloatOf     -> R.Term.FloatOf(term.value, term.range)
      is S.Term.DoubleOf    -> R.Term.DoubleOf(term.value, term.range)
      is S.Term.StringOf    -> R.Term.StringOf(term.value, term.range)
      is S.Term.ByteArrayOf -> R.Term.ByteArrayOf(term.elements.map { resolveTerm(it) }, term.range)
      is S.Term.IntArrayOf  -> R.Term.IntArrayOf(term.elements.map { resolveTerm(it) }, term.range)
      is S.Term.LongArrayOf -> R.Term.LongArrayOf(term.elements.map { resolveTerm(it) }, term.range)
      is S.Term.ListOf      -> R.Term.ListOf(term.elements.map { resolveTerm(it) }, term.range)
      is S.Term.CompoundOf  -> R.Term.CompoundOf(term.elements.map { (key, element) -> key to resolveTerm(element) }, term.range)
      is S.Term.RefOf       -> R.Term.RefOf(resolveTerm(term.element), term.range)
      is S.Term.TupleOf     -> R.Term.TupleOf(term.elements.map { resolveTerm(it) }, term.range)
      is S.Term.FunOf       -> {
        val (binder, body) = restoring {
          val binder = resolvePattern(term.binder)
          val body = resolveTerm(term.body)
          binder to body
        }
        R.Term.FunOf(binder, body, term.range)
      }
      is S.Term.Apply       -> R.Term.Apply(resolveTerm(term.operator), resolveTerm(term.operand), term.range)
      is S.Term.If          -> {
        val condition = resolveTerm(term.condition)
        val thenClause = resolveTerm(term.thenClause)
        val elseClause = resolveTerm(term.elseClause)
        R.Term.If(condition, thenClause, elseClause, term.range)
      }
      is S.Term.Let         -> {
        val init = resolveTerm(term.init)
        val (binder, body) = restoring {
          val binder = resolvePattern(term.binder)
          val body = resolveTerm(term.body)
          binder to body
        }
        R.Term.Let(binder, init, body, term.range)
      }
      is S.Term.Var         -> {
        when (val level = getVar(term.name)) {
          -1   -> {
            diagnostics += Diagnostic.VarNotFound(term.name, term.range)
            R.Term.Hole(term.range)
          }
          else -> R.Term.Var(term.name, level, term.range)
        }
      }
      is S.Term.Run         -> {
        when (val location = resolveName(term.name.value, term.name.range)) {
          null -> R.Term.Hole(term.range)
          else -> {
            val name = term.name.map { location }
            val typeArgs = term.typeArgs.map { typeArgs -> typeArgs.map { resolveType(it) } }
            val arg = resolveTerm(term.arg)
            R.Term.Run(name, typeArgs, arg, term.range)
          }
        }
      }
      is S.Term.Is          -> {
        val scrutinee = resolveTerm(term.scrutinee)
        val scrutineer = restoring { resolvePattern(term.scrutineer) }
        R.Term.Is(scrutinee, scrutineer, term.range)
      }
      is S.Term.CodeOf      -> R.Term.CodeOf(resolveTerm(term.element), term.range)
      is S.Term.Splice      -> R.Term.Splice(resolveTerm(term.element), term.range)
      is S.Term.Hole        -> R.Term.Hole(term.range)
    }
  }

  private fun Env.resolvePattern(
    pattern: S.Pattern,
  ): R.Pattern {
    return when (pattern) {
      is S.Pattern.IntOf      -> R.Pattern.IntOf(pattern.value, pattern.annotations, pattern.range)
      is S.Pattern.IntRangeOf -> R.Pattern.IntRangeOf(pattern.min, pattern.max, pattern.annotations, pattern.range)
      is S.Pattern.CompoundOf -> R.Pattern.CompoundOf(pattern.elements.map { (key, element) -> key to resolvePattern(element) }, pattern.annotations, pattern.range)
      is S.Pattern.TupleOf    -> R.Pattern.TupleOf(pattern.elements.map { resolvePattern(it) }, pattern.annotations, pattern.range)
      is S.Pattern.Var        -> {
        bind(pattern.name)
        R.Pattern.Var(pattern.name, lastIndex, pattern.annotations, pattern.range)
      }
      is S.Pattern.Drop       -> R.Pattern.Drop(pattern.annotations, pattern.range)
      is S.Pattern.Anno       -> R.Pattern.Anno(resolvePattern(pattern.element), resolveType(pattern.type), pattern.annotations, pattern.range)
      is S.Pattern.Hole       -> R.Pattern.Hole(pattern.annotations, pattern.range)
    }
  }

  private fun resolveName(
    name: String,
    range: Range,
  ): DefinitionLocation? {
    val expected =
      name
        .split('.')
        .let { ModuleLocation(it.dropLast(1)) / it.last() }
    val candidates = locations.filter { actual ->
      expected.module.parts.size <= actual.module.parts.size &&
      (expected.name == actual.name) &&
      (expected.module.parts.asReversed() zip actual.module.parts.asReversed()).all { it.first == it.second }
    }
    return when (candidates.size) {
      0    -> {
        diagnostics += Diagnostic.DefinitionNotFound(name, range)
        null
      }
      1    -> candidates.first()
      else -> {
        diagnostics += Diagnostic.AmbiguousDefinition(name, range)
        null
      }
    }
  }

  private class Env private constructor(
    private val terms: MutableList<String>,
    private val types: MutableList<String>,
  ) {
    private var savedSize: Int = 0
    val lastIndex: Int get() = terms.lastIndex

    fun getVar(
      name: String,
    ): Int =
      terms.lastIndexOf(name)

    fun getTypeVar(
      name: String,
    ): Int =
      types.lastIndexOf(name)

    fun bind(
      name: String,
    ) {
      terms += name
    }

    inline fun <R> restoring(
      block: () -> R,
    ): R {
      savedSize = terms.size
      val result = block()
      repeat(terms.size - savedSize) {
        terms.removeLast()
      }
      return result
    }

    companion object {
      fun emptyEnv(
        types: List<String>,
      ): Env =
        Env(mutableListOf(), types.toMutableList())
    }
  }

  data class Dependency(
    val location: ModuleLocation,
    val module: R.Module?,
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
