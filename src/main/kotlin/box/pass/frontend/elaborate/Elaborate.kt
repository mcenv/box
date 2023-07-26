package box.pass.frontend.elaborate

import box.ast.Resolved
import box.ast.common.*
import box.ast.common.Annotation
import box.lsp.Instruction
import box.lsp.contains
import box.lsp.diagnostic
import box.lsp.rangeTo
import box.pass.Env
import box.pass.Phase
import box.pass.Value
import box.pass.frontend.Resolve
import box.pass.prettyTerm
import box.util.collections.mapWith
import box.util.unreachable
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import box.ast.Core as C
import box.ast.Resolved as R

@Suppress("NAME_SHADOWING", "NOTHING_TO_INLINE")
class Elaborate private constructor(
  dependencies: List<C.Module>,
  private val input: Resolve.Result,
  private val instruction: Instruction?,
) {
  private val meta: Meta = Meta()
  private val definitions: MutableMap<DefinitionLocation, C.Definition> = dependencies.flatMap { it.definitions.entries }.associateTo(hashMapOf()) { it.key to it.value }
  private lateinit var location: DefinitionLocation
  private val diagnostics: MutableMap<DefinitionLocation, MutableList<Diagnostic>> = hashMapOf()
  private var hover: (() -> Hover)? = null
  private var inlayHints: MutableList<InlayHint> = mutableListOf()

  private fun elaborate(): Result {
    val module = elaborateModule(input.module)
    val diagnostics = (input.diagnostics.keys + diagnostics.keys).associateWith { location ->
      (input.diagnostics[location] ?: emptyList()) + (diagnostics[location] ?: emptyList())
    }
    return Result(
      module,
      diagnostics,
      hover,
      inlayHints,
    )
  }

  private fun elaborateModule(
    module: R.Module,
  ): C.Module {
    val imports = module.imports.map { import ->
      when (val definition = definitions[import.value]) {
        is C.Definition.Def -> hoverDef(import.range, definition)
        else                -> {}
      }
      import.value
    }
    val definitions = module.definitions.values
      .mapNotNull { elaborateDefinition(it) }
      .associateByTo(linkedMapOf()) { it.name }
    return C.Module(module.name, imports, definitions)
  }

  private fun elaborateDefinition(
    definition: R.Definition,
  ): C.Definition? {
    if (definition is R.Definition.Hole) {
      return null
    }

    val doc = definition.doc
    val annotations = definition.annotations.map { it.value }
    val modifiers = definition.modifiers.map { it.value }
    val name = definition.name.value
    location = name
    return when (definition) {
      is R.Definition.Def  -> {
        val modifiers = if (definition.body.let { (it is R.Term.FuncOf && !it.open) || (it is R.Term.Builtin) }) modifiers + Modifier.DIRECT else modifiers
        val ctx = emptyCtx()
        val phase = getPhase(modifiers)
        val type = ctx.elaborateTerm(definition.type, meta.freshType(definition.type.range), phase).term
        if (Modifier.REC in modifiers) {
          definitions[name] = C.Definition.Def(doc, annotations, modifiers, name, type, C.Term.Hole)
        }
        val body = definition.body.let { ctx.elaborateTerm(it, ctx.env.evalTerm(type), phase).term }
        val (zonkedType, zonkedBody) = meta.checkSolved(diagnostics.computeIfAbsent(location) { mutableListOf() }, type, body)
        C.Definition.Def(doc, annotations, modifiers, name, zonkedType, zonkedBody).also {
          hoverDef(definition.name.range, it)
        }
      }
      is R.Definition.Hole -> unreachable()
    }.also { definitions[name] = it }
  }

  private fun Ctx.elaborateTerm(
    term: R.Term,
    type: Value?,
    phase: Phase,
  ): Elaborated {
    val type = type?.let { meta.forceValue(type) }
    return when {
      term is R.Term.Tag && phase == Phase.CONST && synth(type)                  -> {
        C.Term.Tag.of(Value.Type.END)
      }

      term is R.Term.TagOf && phase == Phase.CONST && synth(type)                -> {
        C.Term.TagOf(term.repr).of(Value.Tag)
      }

      term is R.Term.Type && synth(type)                                         -> {
        val tag = elaborateTerm(term.element, Value.Tag, Phase.CONST).term
        C.Term.Type(tag).of(Value.Type.BYTE)
      }

      term is R.Term.Unit && synth(type)                                         -> {
        C.Term.Unit.of(Value.Type.BYTE)
      }

      term is R.Term.Bool && synth(type)                                         -> {
        C.Term.Bool.of(Value.Type.BYTE)
      }

      term is R.Term.I8 && synth(type)                                           -> {
        C.Term.I8.of(Value.Type.BYTE)
      }

      term is R.Term.I16 && synth(type)                                          -> {
        C.Term.I16.of(Value.Type.SHORT)
      }

      term is R.Term.I32 && synth(type)                                          -> {
        C.Term.I32.of(Value.Type.INT)
      }

      term is R.Term.I64 && synth(type)                                          -> {
        C.Term.I64.of(Value.Type.LONG)
      }

      term is R.Term.F32 && synth(type)                                          -> {
        C.Term.F32.of(Value.Type.FLOAT)
      }

      term is R.Term.F64 && synth(type)                                          -> {
        C.Term.F64.of(Value.Type.DOUBLE)
      }

      term is R.Term.Wtf16 && synth(type)                                        -> {
        C.Term.Wtf16.of(Value.Type.STRING)
      }

      term is R.Term.ConstOf && synth(type)                                      -> {
        val type = when (term.value) {
          is Unit    -> Value.Unit
          is Boolean -> Value.Bool
          is Byte    -> Value.I8
          is Short   -> Value.I16
          is Int     -> Value.I32
          is Long    -> Value.I64
          is Float   -> Value.F32
          is Double  -> Value.F64
          is String  -> Value.Wtf16
          else       -> unreachable()
        }
        C.Term.ConstOf(term.value).of(type)
      }

      term is R.Term.I8Array && synth(type)                                      -> {
        C.Term.I8Array.of(Value.Type.BYTE_ARRAY)
      }

      term is R.Term.I8ArrayOf && synth(type)                                    -> {
        val elements = term.elements.map { elaborateTerm(it, Value.I8, phase).term }
        C.Term.I8ArrayOf(elements).of(Value.I8Array)
      }

      term is R.Term.I32Array && synth(type)                                     -> {
        C.Term.I32Array.of(Value.Type.INT_ARRAY)
      }

      term is R.Term.I32ArrayOf && synth(type)                                   -> {
        val elements = term.elements.map { elaborateTerm(it, Value.I32, phase).term }
        C.Term.I32ArrayOf(elements).of(Value.I32Array)
      }

      term is R.Term.I64Array && synth(type)                                     -> {
        C.Term.I64Array.of(Value.Type.LONG_ARRAY)
      }

      term is R.Term.I64ArrayOf && synth(type)                                   -> {
        val elements = term.elements.map { elaborateTerm(it, Value.I64, phase).term }
        C.Term.I64ArrayOf(elements).of(Value.I64Array)
      }

      term is Resolved.Term.List && synth(type)                                  -> {
        val element = elaborateTerm(term.element, meta.freshType(term.element.range), phase).term
        C.Term.List(element).of(Value.Type.LIST)
      }

      term is R.Term.ListOf && match<Value.List>(type)                           -> {
        val elementType = type?.element?.value
        val (elements, elementsTypes) = term.elements.map {
          val (term, type) = elaborateTerm(it, elementType, phase)
          term to type
        }.unzip()
        val type = type ?: Value.List(lazyOf(
          // TODO: perform more sophisticated normalization
          if (elementsTypes.size == 1) {
            elementsTypes.first()
          } else {
            Value.Union(elementsTypes.map { lazyOf(it) }, elementsTypes.firstOrNull()?.type ?: Value.Type.END_LAZY)
          }
        ))
        typed(type) {
          C.Term.ListOf(elements, it)
        }
      }

      term is R.Term.Compound && synth(type)                                     -> {
        val elements = term.elements.associateTo(linkedMapOf()) { (key, element) ->
          val element = elaborateTerm(element, meta.freshType(element.range), phase).term
          key.value to element
        }
        C.Term.Compound(elements).of(Value.Type.COMPOUND)
      }

      term is R.Term.CompoundOf && synth(type)                                   -> {
        val elements = linkedMapOf<String, C.Term>()
        val elementsTypes = linkedMapOf<String, Lazy<Value>>()
        term.elements.forEach { (key, element) ->
          val (element, elementType) = elaborateTerm(element, null, phase)
          elements[key.value] = element
          elementsTypes[key.value] = lazyOf(elementType)
        }
        val type = Value.Compound(elementsTypes)
        typed(type) {
          C.Term.CompoundOf(elements, it)
        }
      }

      term is R.Term.CompoundOf && check<Value.Compound>(type)                   -> {
        val elements = term.elements.associateTo(linkedMapOf()) { (name, element) ->
          val (element, _) = elaborateTerm(element, type.elements[name.value]?.value, phase)
          name.value to element
        }
        typed(type) {
          C.Term.CompoundOf(elements, it)
        }
      }

      term is R.Term.Point && synth(type)                                        -> { // TODO: unify tags
        val (element, elementType) = elaborateTerm(term.element, null, phase)
        val type = elementType.type.value
        typed(type) {
          C.Term.Point(element, it)
        }
      }

      term is R.Term.Union && match<Value.Type>(type)                            -> {
        val type = type ?: meta.freshType(term.range)
        val elements = term.elements.map { elaborateTerm(it, type, phase).term }
        typed(type) {
          C.Term.Union(elements, it)
        }
      }

      term is R.Term.Func && synth(type)                                         -> {
        val (ctx, params) = term.params.mapWith(this) { transform, (pattern, term) ->
          val term = elaborateTerm(term, meta.freshType(term.range), phase).term
          val vTerm = env.evalTerm(term)
          elaboratePattern(pattern, phase, vTerm, lazyOf(Value.Var("#${next()}", next(), lazyOf(vTerm)))) { (pattern, _) ->
            transform(this)
            pattern to term
          }
        }
        val result = ctx.elaborateTerm(term.result, meta.freshType(term.result.range), phase).term
        val type = if (term.open) Value.Type.COMPOUND else Value.Type.INT
        C.Term.Func(term.open, params, result).of(type)
      }

      term is R.Term.FuncOf && synth(type)                                       -> {
        val (ctx, params) = term.params.mapWith(this) { transform, param ->
          val paramType = meta.freshValue(param.range)
          elaboratePattern(param, phase, paramType, lazyOf(Value.Var("#${next()}", next(), lazyOf(paramType)))) { (pattern, patternType) ->
            transform(this)
            pattern to lazyOf(patternType)
          }
        }
        val (result, resultType) = ctx.elaborateTerm(term.result, null, phase)
        val type = Value.Func(term.open, params) { args -> (env + args).evalTerm(next().quoteValue(resultType)) }
        typed(type) {
          C.Term.FuncOf(term.open, params.map { param -> param.first }, result, it)
        }
      }

      term is R.Term.FuncOf && check<Value.Func>(type) && term.open == type.open -> {
        if (type.params.size == term.params.size) {
          val (ctx, params) = (term.params zip type.params).mapWith(this) { transform, (pattern, term) ->
            val paramType = term.second.value
            elaboratePattern(pattern, phase, paramType, lazyOf(Value.Var("#${next()}", next(), lazyOf(paramType)))) {
              transform(this)
              it
            }
          }
          val result = ctx.elaborateTerm(term.result, type.result.open(next(), params.map { lazyOf(it.second) }), phase).term
          typed(type) {
            C.Term.FuncOf(term.open, params.map { param -> param.first }, result, it)
          }
        } else {
          invalidTerm(arityMismatch(type.params.size, term.params.size, term.range))
        }
      }

      term is R.Term.Apply && synth(type)                                        -> {
        val (func, maybeFuncType) = elaborateTerm(term.func, null, phase)
        val funcType = when (val funcType = meta.forceValue(maybeFuncType)) {
          is Value.Func -> funcType
          else          -> return invalidTerm(cannotSynthesize(term.func.range)) // TODO: unify?
        }
        if (funcType.params.size != term.args.size) {
          return invalidTerm(arityMismatch(funcType.params.size, term.args.size, term.range))
        }
        val (_, args) = (term.args zip funcType.params).mapWith(env) { transform, (arg, param) ->
          val paramType = evalTerm(next().quoteValue(param.second.value)) // TODO: use telescopic closure
          val arg = elaborateTerm(arg, paramType, phase).term
          transform(this + lazy { evalTerm(arg) })
          arg
        }
        val (_, vArgs) = args.mapWith(env) { transform, arg ->
          val vArg = lazy { env.evalTerm(arg) }
          transform(env + vArg)
          vArg
        }
        val type = funcType.result(vArgs)
        typed(type) {
          C.Term.Apply(funcType.open, func, args, it)
        }
      }

      term is R.Term.Code && phase == Phase.CONST && synth(type)                 -> {
        val element = elaborateTerm(term.element, meta.freshType(term.element.range), Phase.WORLD).term
        C.Term.Code(element).of(Value.Type.END)
      }

      term is R.Term.CodeOf && phase == Phase.CONST && match<Value.Code>(type)   -> {
        val (element, elementType) = elaborateTerm(term.element, type?.element?.value, Phase.WORLD)
        val type = type ?: Value.Code(lazyOf(elementType))
        typed(type) {
          C.Term.CodeOf(element, it)
        }
      }

      term is R.Term.Splice && phase == Phase.WORLD && match<Value>(type)        -> {
        val type = type ?: meta.freshValue(term.range)
        val element = elaborateTerm(term.element, Value.Code(lazyOf(type)), Phase.CONST).term
        typed(type) {
          C.Term.Splice(element, it)
        }
      }

      term is R.Term.Path && phase == Phase.CONST && synth(type)                 -> {
        val element = elaborateTerm(term.element, meta.freshType(term.element.range), Phase.WORLD).term
        C.Term.Path(element).of(Value.Type.END)
      }

      term is R.Term.PathOf && phase == Phase.CONST && match<Value.Path>(type)   -> {
        val (element, elementType) = elaborateTerm(term.element, type?.element?.value, Phase.WORLD)
        val type = type ?: Value.Path(lazyOf(elementType))
        typed(type) {
          C.Term.PathOf(element, it)
        }
      }

      term is R.Term.Get && phase == Phase.WORLD && match<Value>(type)           -> {
        val type = type ?: meta.freshValue(term.range)
        val element = elaborateTerm(term.element, Value.Path(lazyOf(type)), Phase.CONST).term
        typed(type) {
          C.Term.Get(element, it)
        }
      }

      term is R.Term.Command && match<Value>(type)                               -> {
        val type = type ?: meta.freshValue(term.range)
        val element = elaborateTerm(term.element, Value.Wtf16, Phase.CONST).term
        typed(type) {
          C.Term.Command(element, it)
        }
      }

      term is R.Term.Let && match<Value>(type)                                   -> {
        val (init, initType) = elaborateTerm(term.init, null, phase)
        elaboratePattern(term.binder, phase, initType, lazy { env.evalTerm(init) }) { (binder, binderType) ->
          if (next().sub(initType, binderType)) {
            val (body, bodyType) = elaborateTerm(term.body, type, phase)
            val type = type ?: bodyType
            this@elaborateTerm.typed(type) {
              C.Term.Let(binder, init, body, it)
            }
          } else {
            invalidTerm(next().typeMismatch(initType, binderType, term.init.range))
          }
        }
      }

      term is R.Term.If && match<Value>(type)                                    -> {
        val (scrutinee, scrutineeType) = elaborateTerm(term.scrutinee, null, phase)
        val vScrutinee = lazy { env.evalTerm(scrutinee) }

        // TODO: implement fine-grained exhaustiveness checking
        var exhaustive = false
        val (branches, branchesTypes) = term.branches.map { (pattern, body) ->
          elaboratePattern(pattern, phase, scrutineeType, vScrutinee) { (pattern, _) ->
            if (!exhaustive && (pattern is C.Pattern.Var || pattern is C.Pattern.Drop)) {
              exhaustive = true
            }
            val (body, bodyType) = elaborateTerm(body, type, phase)
            (pattern to body) to bodyType
          }
        }.unzip()

        if (exhaustive) {
          val type = type ?: Value.Union(branchesTypes.map { lazyOf(it) }, branchesTypes.firstOrNull()?.type ?: Value.Type.END_LAZY /* TODO: validate */)
          typed(type) {
            C.Term.If(scrutinee, branches, it)
          }
        } else {
          invalidTerm(notExhaustive(term.range.start..term.range.start))
        }
      }

      term is R.Term.Var && synth(type)                                          -> {
        val entry = entries[term.idx.toLvl(Lvl(entries.size)).value]
        if (entry.used) {
          invalidTerm(alreadyUsed(term.range))
        } else {
          val type = meta.forceValue(entry.value.type.value)
          if (false /* TODO: quantity */) {
            entry.used = true
          }
          when {
            entry.phase == phase                      -> {
              next().quoteValue(entry.value).of(type)
            }
            entry.phase < phase                       -> {
              inlayHint(term.range.start, "`")
              typed(Value.Code(lazyOf(type))) {
                C.Term.CodeOf(next().quoteValue(entry.value), it)
              }
            }
            entry.phase > phase && type is Value.Code -> {
              inlayHint(term.range.start, "$")
              typed(type.element.value) {
                C.Term.Splice(next().quoteValue(entry.value), it)
              }
            }
            else                                      -> {
              invalidTerm(phaseMismatch(phase, entry.phase, term.range))
            }
          }
        }
      }

      term is R.Term.Def && synth(type)                                          -> {
        when (val definition = definitions[term.name]) {
          is C.Definition.Def -> {
            hoverDef(term.range, definition)

            if (Annotation.Deprecated in definition.annotations) {
              diagnose(deprecated(term.range))
            }
            if (Annotation.Unstable in definition.annotations) {
              diagnose(unstable(term.range))
            }
            if (Annotation.Delicate in definition.annotations) {
              diagnose(delicate(term.range))
            }

            val actualPhase = getPhase(definition.modifiers)
            if (actualPhase <= phase) {
              val def = C.Term.Def(definition, definition.type)
              val type = env.evalTerm(definition.type)
              def.of(type)
            } else {
              invalidTerm(phaseMismatch(phase, actualPhase, term.range))
            }
          }
          else                -> {
            invalidTerm(expectedDef(term.range))
          }
        }
      }

      term is R.Term.Meta && match<Value>(type)                                  -> {
        val type = type ?: meta.freshValue(term.range)
        next().quoteValue(meta.freshValue(term.range)).of(type)
      }

      term is R.Term.As && synth(type)                                           -> {
        val type = env.evalTerm(elaborateTerm(term.type, meta.freshType(term.type.range), phase).term)
        elaborateTerm(term.element, type, phase)
      }

      term is R.Term.Builtin && synth(type)                                      -> {
        val type = env.evalTerm(term.builtin.type)
        C.Term.Builtin(term.builtin).of(type)
      }

      term is R.Term.Hole && match<Value>(type)                                  -> {
        C.Term.Hole.of(Value.Hole)
      }

      synth(type) && phase == Phase.WORLD                                        -> {
        invalidTerm(phaseMismatch(Phase.CONST, phase, term.range))
      }

      synth(type)                                                                -> {
        invalidTerm(cannotSynthesize(term.range))
      }

      check<Value>(type)                                                         -> {
        val (synth, synthType) = elaborateTerm(term, null, phase)
        lateinit var diagnostic: Lazy<Diagnostic>

        fun sub(value1: Value, value2: Value): Boolean {
          return if (next().sub(value1, value2)) {
            true
          } else if (value2 is Value.Point) {
            val element1 = env.evalTerm(synth)
            val element2 = value2.element.value
            with(meta) { next().unifyValue(element2, element1) }.also {
              if (!it) {
                diagnostic = lazy { next().typeMismatch(element2, element1, term.range) }
              }
            }
          } else {
            diagnostic = lazy { next().typeMismatch(value2, value1, term.range) }
            false
          }
        }

        if (sub(synthType, type)) {
          return synth.of(type)
        } else if (type is Value.Code && sub(synthType, type.element.value)) {
          inlayHint(term.range.start, "`")
          return typed(type) {
            C.Term.CodeOf(synth, it)
          }
        } else if (synthType is Value.Code && sub(synthType.element.value, type)) {
          inlayHint(term.range.start, "$")
          return typed(type) {
            C.Term.Splice(synth, it)
          }
        }

        invalidTerm(diagnostic.value)
      }

      else                                                                       -> {
        unreachable()
      }
    }.also { (_, type) ->
      hoverType(term.range, type)
    }
  }

  private fun <T> Ctx.elaboratePattern(
    pattern: R.Pattern,
    phase: Phase,
    typeOfVar: Value,
    valueOfVar: Lazy<Value>,
    block: Ctx.(Pair<C.Pattern, Value>) -> T,
  ): T {
    val entries = mutableListOf<Ctx.Entry>()

    fun bind(
      pattern: R.Pattern,
      phase: Phase,
      type: Value?,
      projs: PersistentList<Proj>,
    ): Pair<C.Pattern, Value> {
      val type = type?.let { meta.forceValue(type) }
      return when {
        pattern is R.Pattern.ConstOf && synth(type)                    -> {
          val type = when (pattern.value) {
            is Unit    -> Value.Unit
            is Boolean -> Value.Bool
            is Byte    -> Value.I8
            is Short   -> Value.I16
            is Int     -> Value.I32
            is Long    -> Value.I64
            is Float   -> Value.F32
            is Double  -> Value.F64
            is String  -> Value.Wtf16
            else       -> unreachable()
          }
          C.Pattern.ConstOf(pattern.value) to type
        }

        pattern is R.Pattern.I8ArrayOf && match<Value.I8Array>(type)   -> {
          val elements = pattern.elements.mapIndexed { index, element ->
            val (element, _) = bind(element, phase, Value.I8, projs + Proj.I8ArrayOf(index))
            element
          }
          val type = type ?: Value.I8Array
          C.Pattern.I8ArrayOf(elements) to type
        }

        pattern is R.Pattern.I32ArrayOf && match<Value.I32Array>(type) -> {
          val elements = pattern.elements.mapIndexed { index, element ->
            val (element, _) = bind(element, phase, Value.I32, projs + Proj.I32ArrayOf(index))
            element
          }
          val type = type ?: Value.I32Array
          C.Pattern.I32ArrayOf(elements) to type
        }

        pattern is R.Pattern.I64ArrayOf && match<Value.I64Array>(type) -> {
          val elements = pattern.elements.mapIndexed { index, element ->
            val (element, _) = bind(element, phase, Value.I64, projs + Proj.I64ArrayOf(index))
            element
          }
          val type = type ?: Value.I64Array
          C.Pattern.I64ArrayOf(elements) to type
        }

        pattern is R.Pattern.ListOf && match<Value.List>(type)         -> {
          val elementType = type?.element?.value ?: meta.freshValue(pattern.range)
          val elements = pattern.elements.mapIndexed { index, element ->
            val (element, _) = bind(element, phase, elementType, projs + Proj.ListOf(index))
            element
          }
          val type = type ?: Value.List(lazyOf(elementType))
          C.Pattern.ListOf(elements) to type
        }

        pattern is R.Pattern.CompoundOf && match<Value.Compound>(type) -> {
          val results = pattern.elements.associateTo(linkedMapOf()) { (name, element) ->
            val type = type?.elements?.get(name.value)?.value ?: invalidTerm(unknownKey(name.value, name.range)).type
            val (element, elementType) = bind(element, phase, type, projs + Proj.CompoundOf(name.value))
            name.value to (element to elementType)
          }
          val elements = results.mapValuesTo(linkedMapOf()) { it.value.first }
          val type = type ?: Value.Compound(results.mapValuesTo(linkedMapOf()) { lazyOf(it.value.second) })
          C.Pattern.CompoundOf(elements) to type
        }

        pattern is R.Pattern.Var && match<Value>(type)                 -> {
          val type = type ?: meta.freshValue(pattern.range)
          val value = if (projs.isEmpty()) {
            Value.Var(pattern.name, next(), lazyOf(typeOfVar))
          } else {
            Value.Project(Value.Var(pattern.name, next(), lazyOf(typeOfVar)), projs, lazyOf(type))
          }
          entries += Ctx.Entry(pattern.name, phase, value, false)
          C.Pattern.Var(pattern.name) to type
        }

        pattern is R.Pattern.Drop && match<Value>(type)                -> {
          val type = type ?: meta.freshValue(pattern.range)
          C.Pattern.Drop to type
        }

        pattern is R.Pattern.As && synth(type)                         -> {
          val type = env.evalTerm(elaborateTerm(pattern.type, meta.freshType(pattern.type.range), phase).term)
          bind(pattern.element, phase, type, projs)
        }

        pattern is R.Pattern.Hole && match<Value>(type)                -> {
          C.Pattern.Hole to Value.Hole
        }

        synth(type)                                                    -> {
          invalidPattern(cannotSynthesize(pattern.range))
        }

        check<Value>(type)                                             -> {
          val synth = bind(pattern, phase, null, projs)
          if (next().sub(synth.second, type)) {
            synth
          } else {
            invalidPattern(next().typeMismatch(type, synth.second, pattern.range))
          }
        }

        else                                                           -> {
          unreachable()
        }
      }.also { (_, type) ->
        hoverType(pattern.range, type)
      }
    }

    val result = bind(pattern, phase, typeOfVar, persistentListOf())
    val ctx = Ctx(this.entries + entries, env + valueOfVar)
    return ctx.block(result)
  }

  private fun Lvl.sub(
    value1: Value,
    value2: Value,
  ): Boolean {
    val value1 = meta.forceValue(value1)
    val value2 = meta.forceValue(value2)
    return when {
      value1 is Value.List && value2 is Value.List         -> {
        sub(value1.element.value, value2.element.value)
      }

      value1 is Value.Compound && value2 is Value.Compound -> {
        value1.elements.size == value2.elements.size &&
        value1.elements.all { (key1, element1) ->
          value2.elements[key1]?.let { element2 -> sub(element1.value, element2.value) } ?: false
        }
      }

      value1 is Value.Point && value2 !is Value.Point -> {
        sub(value1.element.value.type.value, value2)
      }

      value1 is Value.Union                           -> {
        value1.elements.all { sub(it.value, value2) }
      }

      value2 is Value.Union                           -> {
        // TODO: implement subtyping with unification that has lower bound and upper bound
        if (value1 is Value.Meta && value2.elements.isEmpty()) {
          with(meta) { unifyValue(value1, value2) }
        } else {
          value2.elements.any { sub(value1, it.value) }
        }
      }

      value1 is Value.Func && value2 is Value.Func    -> {
        value1.open == value2.open &&
        value1.params.size == value2.params.size &&
        (value1.params zip value2.params).all { (param1, param2) ->
          sub(param2.second.value, param1.second.value)
        } &&
        sub(
          value1.result.open(this, value1.params.map { (_, type) -> type }),
          value2.result.open(this, value2.params.map { (_, type) -> type }),
        )
      }

      value1 is Value.Code && value2 is Value.Code    -> {
        sub(value1.element.value, value2.element.value)
      }

      value1 is Value.Path && value2 is Value.Path    -> {
        sub(value1.element.value, value2.element.value)
      }

      else                                            -> {
        with(meta) { unifyValue(value1, value2) }
      }
    }
  }

  private fun getPhase(modifiers: Collection<Modifier>): Phase {
    return if (Modifier.CONST in modifiers) Phase.CONST else Phase.WORLD
  }

  @OptIn(ExperimentalContracts::class)
  private inline fun synth(type: Value?): Boolean {
    contract {
      returns(false) implies (type != null)
      returns(true) implies (type == null)
    }
    return type == null
  }

  @OptIn(ExperimentalContracts::class)
  private inline fun <reified V : Value> check(type: Value?): Boolean {
    contract {
      returns(false) implies (type !is V)
      returns(true) implies (type is V)
    }
    return type is V
  }

  @OptIn(ExperimentalContracts::class)
  private inline fun <reified V : Value> match(type: Value?): Boolean {
    contract {
      returns(false) implies (type !is V?)
      returns(true) implies (type is V?)
    }
    return type is V?
  }

  private fun Ctx.hoverType(
    range: Range,
    type: Value,
  ) {
    hover(range) {
      val type = next().prettyValue(type)
      Hover(MarkupContent(MarkupKind.MARKDOWN, "```box\n$type\n```"))
    }
  }

  private fun hoverDef(
    range: Range,
    definition: C.Definition.Def,
  ) {
    hover(range) {
      val modifiers = definition.modifiers.joinToString(" ", "", " ").ifBlank { "" }
      val name = definition.name.name
      val type = prettyTerm(definition.type)
      val doc = definition.doc
      Hover(MarkupContent(MarkupKind.MARKDOWN, "```box\n${modifiers}def $name : $type\n```\n---\n$doc"))
    }
  }

  private fun hover(
    range: Range,
    message: () -> Hover,
  ) {
    if (hover == null && instruction is Instruction.Hover && instruction.position in range) {
      hover = message
    }
  }

  private fun inlayHint(
    position: Position,
    label: String,
  ) {
    if (instruction is Instruction.InlayHint && position in instruction.range) {
      inlayHints += InlayHint(position, forLeft(label))
    }
  }

  private fun Lvl.typeMismatch(
    expected: Value,
    actual: Value,
    range: Range,
  ): Diagnostic {
    val expected = prettyValue(expected)
    val actual = prettyValue(actual)
    return diagnostic(
      range,
      """type mismatch:
        |  expected: $expected
        |  actual  : $actual
      """.trimMargin(),
      DiagnosticSeverity.Error,
    )
  }

  private fun Lvl.prettyValue(
    value: Value,
  ): String {
    return prettyTerm(with(meta) { zonkTerm(quoteValue(value)) })
  }

  private fun invalidTerm(
    diagnostic: Diagnostic,
  ): Elaborated {
    diagnose(diagnostic)
    return C.Term.Hole.of(Value.Hole)
  }

  private fun invalidPattern(
    diagnostic: Diagnostic,
  ): Pair<C.Pattern, Value> {
    diagnose(diagnostic)
    return C.Pattern.Hole to Value.Hole
  }

  private fun diagnose(diagnostic: Diagnostic) {
    diagnostics.computeIfAbsent(location) { mutableListOf() } += diagnostic
  }

  private data class Elaborated(
    val term: C.Term,
    val type: Value,
  )

  private inline fun C.Term.of(
    type: Value,
  ): Elaborated {
    return Elaborated(this, type)
  }

  private inline fun Ctx.typed(
    type: Value,
    build: (C.Term) -> C.Term,
  ): Elaborated {
    return build(next().quoteValue(type)).of(type)
  }

  private data class Ctx(
    val entries: PersistentList<Entry>,
    val env: Env,
  ) {
    data class Entry(
      val name: String,
      val phase: Phase,
      val value: Value,
      var used: Boolean,
    )
  }

  private fun emptyCtx(): Ctx {
    return Ctx(persistentListOf(), emptyEnv())
  }

  private fun Ctx.next(): Lvl {
    return Lvl(env.size)
  }

  data class Result(
    val module: C.Module,
    val diagnostics: Map<DefinitionLocation?, List<Diagnostic>>,
    val hover: (() -> Hover)?,
    val inlayHints: List<InlayHint>,
  )

  companion object {
    private fun unknownKey(
      name: String,
      range: Range,
    ): Diagnostic {
      return diagnostic(
        range,
        "unknown key: $name",
        DiagnosticSeverity.Error,
      )
    }

    private fun notExhaustive(
      range: Range,
    ): Diagnostic {
      return diagnostic(
        range,
        "not exhaustive",
        DiagnosticSeverity.Error,
      )
    }

    private fun arityMismatch(
      expected: Int,
      actual: Int,
      range: Range,
    ): Diagnostic {
      return diagnostic(
        range,
        """arity mismatch:
          |  expected: $expected
          |  actual  : $actual
        """.trimMargin(),
        DiagnosticSeverity.Error,
      )
    }

    private fun phaseMismatch(
      expected: Phase,
      actual: Phase,
      range: Range,
    ): Diagnostic {
      return diagnostic(
        range,
        """phase mismatch:
          |  expected: $expected
          |  actual  : $actual
        """.trimMargin(),
        DiagnosticSeverity.Error,
      )
    }

    private fun alreadyUsed(
      range: Range,
    ): Diagnostic {
      return diagnostic(
        range,
        "already used",
        DiagnosticSeverity.Error,
      )
    }

    private fun expectedDef(
      range: Range,
    ): Diagnostic {
      return diagnostic(
        range,
        "expected definition: def",
        DiagnosticSeverity.Error,
      )
    }

    private fun cannotSynthesize(
      range: Range,
    ): Diagnostic {
      return diagnostic(
        range,
        "cannot synthesize",
        DiagnosticSeverity.Error,
      )
    }

    private fun deprecated(
      range: Range,
    ): Diagnostic {
      return diagnostic(
        range,
        "deprecated",
        DiagnosticSeverity.Warning,
      )
    }

    private fun unstable(
      range: Range,
    ): Diagnostic {
      return diagnostic(
        range,
        "unstable",
        DiagnosticSeverity.Warning,
      )
    }

    private fun delicate(
      range: Range,
    ): Diagnostic {
      return diagnostic(
        range,
        "delicate",
        DiagnosticSeverity.Warning,
      )
    }

    operator fun invoke(
      dependencies: List<C.Module>,
      input: Resolve.Result,
      instruction: Instruction?,
    ): Result {
      return Elaborate(dependencies, input, instruction).elaborate()
    }
  }
}
