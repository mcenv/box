package mcx.pass.frontend

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toPersistentList
import mcx.ast.*
import mcx.ast.Annotation
import mcx.lsp.Instruction
import mcx.lsp.contains
import mcx.lsp.diagnostic
import mcx.pass.*
import mcx.util.collections.mapWith
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import mcx.ast.Core as C
import mcx.ast.Resolved as R

@Suppress("NAME_SHADOWING", "NOTHING_TO_INLINE")
class Elaborate private constructor(
  dependencies: List<C.Module>,
  private val input: Resolve.Result,
  private val instruction: Instruction?,
) {
  private val meta: Meta = Meta()
  private val definitions: MutableMap<DefinitionLocation, C.Definition> = dependencies.flatMap { dependency -> dependency.definitions.map { it.name to it } }.toMap().toMutableMap()
  private val diagnostics: MutableList<Diagnostic> = mutableListOf()
  private var hover: (() -> Hover)? = null
  private var inlayHints: MutableList<InlayHint> = mutableListOf()

  private fun elaborate(): Result {
    val module = elaborateModule(input.module)
    return Result(
      module,
      input.diagnostics + diagnostics,
      hover,
      inlayHints,
    )
  }

  private fun elaborateModule(
    module: R.Module,
  ): C.Module {
    module.imports.forEach { import ->
      when (val definition = definitions[import.value]) {
        is C.Definition.Def -> hoverDef(import.range, definition)
        else                -> {}
      }
    }
    val definitions = module.definitions.values.mapNotNull { elaborateDefinition(it) }
    return C.Module(module.name, definitions)
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
    return when (definition) {
      is R.Definition.Def  -> {
        val modifiers = if (definition.body.let { it is R.Term.FuncOf && !it.open }) modifiers + Modifier.DIRECT else modifiers
        val ctx = emptyCtx()
        val phase = getPhase(modifiers)
        val type = ctx.checkTerm(definition.type, phase, meta.freshType(definition.type.range))
        if (Modifier.REC in modifiers) {
          definitions[name] = C.Definition.Def(doc, annotations, modifiers, name, type, null)
        }
        val body = definition.body?.let { ctx.checkTerm(it, phase, ctx.env.evalTerm(type)) }
        val (zonkedType, zonkedBody) = meta.checkSolved(diagnostics, type, body)
        C.Definition.Def(doc, annotations, modifiers, name, zonkedType!!, zonkedBody).also {
          hoverDef(definition.name.range, it)
        }
      }
      is R.Definition.Hole -> error("Unreachable")
    }.also { definitions[name] = it }
  }

  private inline fun Ctx.synthTerm(
    term: R.Term,
    phase: Phase,
  ): Pair<C.Term, Value> {
    return elaborateTerm(term, phase, null)
  }

  private inline fun Ctx.checkTerm(
    term: R.Term,
    phase: Phase,
    type: Value,
  ): C.Term {
    return elaborateTerm(term, phase, type).first
  }

  private fun Ctx.elaborateTerm(
    term: R.Term,
    phase: Phase,
    type: Value?,
  ): Pair<C.Term, Value> {
    val type = type?.let { meta.forceValue(type) }
    return when {
      term is R.Term.Tag && phase == Phase.CONST && synth(type)                  -> {
        C.Term.Tag to Value.Type.END
      }

      term is R.Term.TagOf && phase == Phase.CONST && synth(type)                -> {
        C.Term.TagOf(term.value) to Value.Tag
      }

      term is R.Term.Type && synth(type)                                         -> {
        val tag = checkTerm(term.element, Phase.CONST, Value.Tag)
        C.Term.Type(tag) to Value.Type.BYTE
      }

      term is R.Term.Bool && synth(type)                                         -> {
        C.Term.Bool to Value.Type.BYTE
      }

      term is R.Term.BoolOf && synth(type)                                       -> {
        C.Term.BoolOf(term.value) to Value.Bool
      }

      term is R.Term.If && match<Value>(type)              -> {
        val condition = checkTerm(term.condition, phase, Value.Bool)
        val (thenBranch, thenBranchType) = /* TODO: avoid duplication? */ duplicate().elaborateTerm(term.thenBranch, phase, type)
        val (elseBranch, elseBranchType) = elaborateTerm(term.elseBranch, phase, type)
        val type = type ?: Value.Union(listOf(lazyOf(thenBranchType), lazyOf(elseBranchType)), thenBranchType.type /* TODO: validate */)
        typed(type) {
          C.Term.If(condition, thenBranch, elseBranch, it)
        }
      }

      term is R.Term.I8 && synth(type)                                           -> {
        C.Term.I8 to Value.Type.BYTE
      }

      term is R.Term.I8Of && synth(type)                                         -> {
        C.Term.I8Of(term.value) to Value.I8
      }

      term is R.Term.I16 && synth(type)                                          -> {
        C.Term.I16 to Value.Type.SHORT
      }

      term is R.Term.I16Of && synth(type)                                        -> {
        C.Term.I16Of(term.value) to Value.I16
      }

      term is R.Term.I32 && synth(type)                                          -> {
        C.Term.I32 to Value.Type.INT
      }

      term is R.Term.I32Of && synth(type)                                        -> {
        C.Term.I32Of(term.value) to Value.I32
      }

      term is R.Term.I64 && synth(type)                                          -> {
        C.Term.I64 to Value.Type.LONG
      }

      term is R.Term.I64Of && synth(type)                                        -> {
        C.Term.I64Of(term.value) to Value.I64
      }

      term is R.Term.F32 && synth(type)                                          -> {
        C.Term.F32 to Value.Type.FLOAT
      }

      term is R.Term.F32Of && synth(type)                                        -> {
        C.Term.F32Of(term.value) to Value.F32
      }

      term is R.Term.F64 && synth(type)                                          -> {
        C.Term.F64 to Value.Type.DOUBLE
      }

      term is R.Term.F64Of && synth(type)                                        -> {
        C.Term.F64Of(term.value) to Value.F64
      }

      term is R.Term.Str && synth(type)                                          -> {
        C.Term.Str to Value.Type.STRING
      }

      term is R.Term.StrOf && synth(type)                                        -> {
        C.Term.StrOf(term.value) to Value.Str
      }

      term is R.Term.I8Array && synth(type)                                      -> {
        C.Term.I8Array to Value.Type.BYTE_ARRAY
      }

      term is R.Term.I8ArrayOf && synth(type)                                    -> {
        val elements = term.elements.map { checkTerm(it, phase, Value.I8) }
        C.Term.I8ArrayOf(elements) to Value.I8Array
      }

      term is R.Term.I32Array && synth(type)                                     -> {
        C.Term.I32Array to Value.Type.INT_ARRAY
      }

      term is R.Term.I32ArrayOf && synth(type)                                   -> {
        val elements = term.elements.map { checkTerm(it, phase, Value.I32) }
        C.Term.I32ArrayOf(elements) to Value.I32Array
      }

      term is R.Term.I64Array && synth(type)                                     -> {
        C.Term.I64Array to Value.Type.LONG_ARRAY
      }

      term is R.Term.I64ArrayOf && synth(type)                                   -> {
        val elements = term.elements.map { checkTerm(it, phase, Value.I64) }
        C.Term.I64ArrayOf(elements) to Value.I64Array
      }

      term is R.Term.Vec && synth(type)                                          -> {
        val element = checkTerm(term.element, phase, meta.freshType(term.element.range))
        C.Term.Vec(element) to Value.Type.LIST
      }

      term is R.Term.VecOf && match<Value.Vec>(type)                             -> {
        val elementType = type?.element?.value
        val (elements, elementsTypes) = term.elements.map { elaborateTerm(it, phase, elementType) }.unzip()
        val type = type ?: Value.Vec(lazyOf(Value.Union(elementsTypes.map { lazyOf(it) }, elementsTypes.firstOrNull()?.type ?: Value.Type.END_LAZY)))
        typed(type) {
          C.Term.VecOf(elements, it)
        }
      }

      term is R.Term.Struct && synth(type)                 -> {
        val elements = term.elements.associateTo(linkedMapOf()) { (key, element) ->
          val element = checkTerm(element, phase, meta.freshType(element.range))
          key.value to element
        }
        C.Term.Struct(elements) to Value.Type.COMPOUND
      }

      term is R.Term.StructOf && synth(type)               -> {
        val elements = linkedMapOf<String, C.Term>()
        val elementsTypes = linkedMapOf<String, Lazy<Value>>()
        term.elements.forEach { (key, element) ->
          val (element, elementType) = synthTerm(element, phase)
          elements[key.value] = element
          elementsTypes[key.value] = lazyOf(elementType)
        }
        val type = Value.Struct(elementsTypes)
        typed(type) {
          C.Term.StructOf(elements, it)
        }
      }

      term is R.Term.StructOf && check<Value.Struct>(type) -> {
        TODO("implement")
      }

      term is R.Term.Ref && synth(type)                    -> {
        val element = checkTerm(term.element, phase, meta.freshType(term.element.range))
        C.Term.Ref(element) to Value.Type.INT
      }

      term is R.Term.RefOf && match<Value.Ref>(type)       -> {
        val (element, elementType) = elaborateTerm(term.element, phase, type?.element?.value)
        val type = type ?: Value.Ref(lazyOf(elementType))
        typed(type) {
          C.Term.RefOf(element, it)
        }
      }

      term is R.Term.Point && synth(type)                  -> { // TODO: unify tags
        val (element, elementType) = synthTerm(term.element, phase)
        val type = type ?: elementType.type.value
        typed(type) {
          C.Term.Point(element, it)
        }
      }

      term is R.Term.Union && match<Value.Type>(type)      -> {
        val type = type ?: meta.freshType(term.range)
        val elements = term.elements.map { checkTerm(it, phase, type) }
        typed(type) {
          C.Term.Union(elements, it)
        }
      }

      term is R.Term.Func && synth(type)                   -> {
        val (ctx, params) = term.params.mapWith(this) { transform, (pattern, term) ->
          val term = checkTerm(term, phase, meta.freshType(term.range))
          val vTerm = env.evalTerm(term)
          elaboratePattern(pattern, phase, vTerm, lazyOf(Value.Var("#${next()}", next(), lazyOf(vTerm)))) { (pattern, patternType) ->
            transform(this)
            pattern to term
          }
        }
        val result = ctx.checkTerm(term.result, phase, meta.freshType(term.result.range))
        val type = if (term.open) Value.Type.COMPOUND else Value.Type.INT
        C.Term.Func(term.open, params, result) to type
      }

      term is R.Term.FuncOf && synth(type)                                       -> {
        val (ctx, params) = term.params.mapWith(this) { transform, param ->
          val paramType = meta.freshValue(param.range)
          elaboratePattern(param, phase, paramType, lazyOf(Value.Var("#${next()}", next(), lazyOf(paramType)))) { (pattern, patternType) ->
            transform(this)
            pattern to lazyOf(patternType)
          }
        }
        val (result, resultType) = ctx.synthTerm(term.result, phase)
        val type = Value.Func(term.open, params, Closure(env, next().quoteValue(resultType)))
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
          val result = ctx.checkTerm(term.result, phase, type.result.open(next(), params.map { lazyOf(it.second) }))
          typed(type) {
            C.Term.FuncOf(term.open, params.map { param -> param.first }, result, it)
          }
        } else {
          invalidTerm(arityMismatch(type.params.size, term.params.size, term.range))
        }
      }

      term is R.Term.Apply && synth(type)                                        -> {
        val (func, maybeFuncType) = synthTerm(term.func, phase)
        val funcType = when (val funcType = meta.forceValue(maybeFuncType)) {
          is Value.Func -> funcType
          else          -> return invalidTerm(cannotSynthesize(term.func.range)) // TODO: unify?
        }
        if (funcType.params.size != term.args.size) {
          return invalidTerm(arityMismatch(funcType.params.size, term.args.size, term.range))
        }
        val (_, args) = (term.args zip funcType.params).mapWith(env) { transform, (arg, param) ->
          val paramType = evalTerm(next().quoteValue(param.second.value)) // TODO: use telescopic closure
          val arg = checkTerm(arg, phase, paramType)
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
        val element = checkTerm(term.element, Phase.WORLD, meta.freshType(term.element.range))
        C.Term.Code(element) to Value.Type.END
      }

      term is R.Term.CodeOf && phase == Phase.CONST && match<Value.Code>(type)   -> {
        val (element, elementType) = elaborateTerm(term.element, Phase.WORLD, type?.element?.value)
        val type = type ?: Value.Code(lazyOf(elementType))
        typed(type) {
          C.Term.CodeOf(element, it)
        }
      }

      term is R.Term.Splice && match<Value>(type)                                -> {
        val type = type ?: meta.freshValue(term.range)
        val element = checkTerm(term.element, Phase.CONST, Value.Code(lazyOf(type)))
        typed(type) {
          C.Term.Splice(element, it)
        }
      }

      term is R.Term.Command && match<Value>(type)                               -> {
        val type = type ?: meta.freshValue(term.range)
        val element = checkTerm(term.element, Phase.CONST, Value.Str)
        typed(type) {
          C.Term.Command(element, it)
        }
      }

      term is R.Term.Let && match<Value>(type)                                   -> {
        val (init, initType) = synthTerm(term.init, phase)
        elaboratePattern(term.binder, phase, initType, lazy { env.evalTerm(init) }) { (binder, binderType) ->
          if (!next().sub(initType, binderType)) {
            diagnostics += next().typeMismatch(initType, binderType, term.init.range)
          }
          val (body, bodyType) = elaborateTerm(term.body, phase, type)
          val type = type ?: bodyType
          this@elaborateTerm.typed(type) {
            C.Term.Let(binder, init, body, it)
          }
        }
      }

      term is R.Term.Var && synth(type)                    -> {
        val entry = entries[term.idx.toLvl(Lvl(entries.size)).value]
        if (entry.used) {
          invalidTerm(alreadyUsed(term.range))
        } else {
          val type = meta.forceValue(entry.value.type.value)
          if (type is Value.Ref) {
            entry.used = true
          }
          when {
            entry.phase == phase                      -> {
              next().quoteValue(entry.value) to type
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

      term is R.Term.Def && synth(type)                    -> {
        when (val definition = definitions[term.name]) {
          is C.Definition.Def -> {
            hoverDef(term.range, definition)

            if (Annotation.Deprecated in definition.annotations) {
              diagnostics += deprecated(term.range)
            }
            if (Annotation.Unstable in definition.annotations) {
              diagnostics += unstable(term.range)
            }
            if (Annotation.Delicate in definition.annotations) {
              diagnostics += delicate(term.range)
            }

            val actualPhase = getPhase(definition.modifiers)
            val def = C.Term.Def(definition, lazyOf(definition.type))
            val type = env.evalTerm(definition.type)
            when {
              // builtin definitions are always phase-polymorphic
              actualPhase == phase || Modifier.BUILTIN in definition.modifiers -> {
                def to type
              }
              actualPhase < phase                                              -> {
                inlayHint(term.range.start, "`")
                val type = Value.Code(lazyOf(type))
                typed(type) {
                  C.Term.CodeOf(def, it)
                }
              }
              actualPhase > phase && type is Value.Code                        -> {
                inlayHint(term.range.start, "$")
                val type = type.element.value
                typed(type) {
                  C.Term.Splice(def, it)
                }
              }
              else                                                             -> {
                invalidTerm(phaseMismatch(phase, actualPhase, term.range))
              }
            }
          }
          else                -> {
            invalidTerm(expectedDef(term.range))
          }
        }
      }

      term is R.Term.Meta && match<Value>(type)                                  -> {
        val type = type ?: meta.freshValue(term.range)
        next().quoteValue(meta.freshValue(term.range)) to type
      }

      term is R.Term.As && synth(type)                                           -> {
        val type = env.evalTerm(checkTerm(term.type, phase, meta.freshType(term.type.range)))
        checkTerm(term.element, phase, type) to type
      }

      term is R.Term.Hole && match<Value>(type)                                  -> {
        C.Term.Hole to Value.Hole
      }

      synth(type)                                                                -> {
        invalidTerm(cannotSynthesize(term.range))
      }

      check<Value>(type)                                   -> {
        val (synth, synthType) = synthTerm(term, phase)
        if (next().sub(synthType, type)) {
          return synth to type
        } else if (type is Value.Code && next().sub(synthType, type.element.value)) {
          inlayHint(term.range.start, "`")
          return typed(type) {
            C.Term.CodeOf(synth, it)
          }
        } else if (synthType is Value.Code && next().sub(synthType.element.value, type)) {
          inlayHint(term.range.start, "$")
          return typed(type) {
            C.Term.Splice(synth, it)
          }
        } else if (type is Value.Point) {
          val value = env.evalTerm(synth)
          if (with(meta) { next().unifyValue(value, type.element.value) }) {
            return synth to type
          }
        }

        invalidTerm(next().typeMismatch(type, synthType, term.range))
      }

      else                                                                       -> {
        error("Unreachable")
      }
    }.also { (_, type) ->
      hoverType(term.range, type)
    }
  }

  private inline fun <T> Ctx.elaboratePattern(
    pattern: R.Pattern,
    phase: Phase,
    type: Value?,
    value: Lazy<Value>,
    block: Ctx.(Pair<C.Pattern, Value>) -> T,
  ): T {
    val entries = mutableListOf<Ctx.Entry>()
    val result = bindPattern(entries, pattern, phase, type)
    val ctx = Ctx(this.entries + entries, env + value)
    return ctx.block(result)
  }

  private fun Ctx.bindPattern(
    entries: MutableList<Ctx.Entry>,
    pattern: R.Pattern,
    phase: Phase,
    type: Value?,
  ): Pair<C.Pattern, Value> {
    val type = type?.let { meta.forceValue(type) }
    return when {
      pattern is R.Pattern.I32Of && synth(type)       -> {
        C.Pattern.I32Of(pattern.value) to Value.I32
      }

      pattern is R.Pattern.Var && match<Value>(type)  -> {
        val type = type ?: meta.freshValue(pattern.range)
        entries += Ctx.Entry(pattern.name, phase, Value.Var(pattern.name, next(), lazyOf(type)), false)
        C.Pattern.Var(pattern.name) to type
      }

      pattern is R.Pattern.Drop && match<Value>(type) -> {
        val type = type ?: meta.freshValue(pattern.range)
        C.Pattern.Drop to type
      }

      pattern is R.Pattern.As && synth(type)          -> {
        val type = env.evalTerm(checkTerm(pattern.type, phase, meta.freshType(pattern.type.range)))
        bindPattern(entries, pattern.element, phase, type)
      }

      pattern is R.Pattern.Hole && match<Value>(type) -> {
        C.Pattern.Hole to Value.Hole
      }

      synth(type)                                     -> {
        invalidPattern(cannotSynthesize(pattern.range))
      }

      check<Value>(type)                              -> {
        val synth = bindPattern(entries, pattern, phase, null)
        if (next().sub(synth.second, type)) {
          synth
        } else {
          invalidPattern(next().typeMismatch(type, synth.second, pattern.range))
        }
      }

      else                                            -> {
        error("Unreachable")
      }
    }.also { (_, type) ->
      hoverType(pattern.range, type)
    }
  }

  private fun Lvl.sub(
    value1: Value,
    value2: Value,
  ): Boolean {
    val value1 = meta.forceValue(value1)
    val value2 = meta.forceValue(value2)
    return when {
      value1 is Value.Vec && value2 is Value.Vec       -> {
        sub(value1.element.value, value2.element.value)
      }

      value1 is Value.Struct && value2 is Value.Struct -> {
        value1.elements.size == value2.elements.size &&
        value1.elements.all { (key1, element1) ->
          value2.elements[key1]?.let { element2 -> sub(element1.value, element2.value) } ?: false
        }
      }

      value1 is Value.Ref && value2 is Value.Ref       -> {
        sub(value1.element.value, value2.element.value)
      }

      value1 is Value.Point && value2 !is Value.Point  -> {
        sub(value1.element.value.type.value, value2)
      }

      value1 is Value.Union                            -> {
        value1.elements.all { sub(it.value, value2) }
      }

      value2 is Value.Union                            -> {
        // TODO: implement subtyping with unification that has lower bound and upper bound
        if (value1 is Value.Meta && value2.elements.isEmpty()) {
          with(meta) { unifyValue(value1, value2) }
        } else {
          value2.elements.any { sub(value1, it.value) }
        }
      }

      value1 is Value.Func && value2 is Value.Func     -> {
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

      value1 is Value.Code && value2 is Value.Code     -> {
        sub(value1.element.value, value2.element.value)
      }

      else                                             -> {
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
      Hover(MarkupContent(MarkupKind.MARKDOWN, "```mcx\n$type\n```"))
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
      Hover(MarkupContent(MarkupKind.MARKDOWN, "```mcx\n${modifiers}def $name : $type\n```\n---\n$doc"))
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
  ): Pair<C.Term, Value> {
    diagnostics += diagnostic
    return C.Term.Hole to Value.Hole
  }

  private fun invalidPattern(
    diagnostic: Diagnostic,
  ): Pair<C.Pattern, Value> {
    diagnostics += diagnostic
    return C.Pattern.Hole to Value.Hole
  }

  private inline fun Ctx.typed(
    type: Value,
    build: (Lazy<C.Term>) -> C.Term,
  ): Pair<C.Term, Value> {
    return build(lazy { next().quoteValue(type) }) to type
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

  private fun Ctx.duplicate(): Ctx {
    return copy(entries = entries.map { it.copy() }.toPersistentList(), env = env)
  }

  data class Result(
    val module: C.Module,
    val diagnostics: List<Diagnostic>,
    val hover: (() -> Hover)?,
    val inlayHints: List<InlayHint>,
  )

  companion object {
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
      context: Context,
      dependencies: List<C.Module>,
      input: Resolve.Result,
      instruction: Instruction?,
    ): Result {
      try {
        return Elaborate(dependencies, input, instruction).elaborate()
      } catch (e: Exception) {
        println(input.module.name)
        throw e
      }
    }
  }
}
