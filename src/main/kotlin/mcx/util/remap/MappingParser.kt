package mcx.util.remap

@Suppress("NOTHING_TO_INLINE")
class MappingParser private constructor(
  private val lines: Iterator<String>,
) {
  private var line: String = lines.next()
  private var cursor: Int = 0

  private fun parse(): Mapping {
    val classMapping: MutableMap<String, Mapping.Class> = hashMapOf()
    lateinit var fieldMapping: MutableMap<String, Mapping.Field>
    lateinit var methodMapping: MutableMap<Pair<String, String>, Mapping.Method>

    fun parseMethod(
      type: String,
      mappedMethodName: String,
    ) {
      skip(/* ( */)
      val descriptor = StringBuilder("(")
      if (peek() != ')') {
        while (true) {
          descriptor.append(readDescriptorWhile { it != ',' && it != ')' })
          when (peek()) {
            ')'  -> break
            else -> skip(/* , */)
          }
        }
      }
      descriptor.append(')')
      descriptor.append(type)
      skip(/* ) */)
      skip4(/*  ->  */)
      val obfuscatedMethodName = remaining()
      methodMapping[obfuscatedMethodName to descriptor.toString()] = Mapping.Method(mappedMethodName)
    }

    while (nextLine()) {
      when (peek()) {
        ' '  -> {
          skip4(/*      */)
          when (peek()) {
            in '0'..'9' -> {
              readWhile { it != ':' }.toInt()
              skip(/* : */)
              readWhile { it != ':' }.toInt()
              skip(/* : */)
              val type = readDescriptorWhile { it != ' ' }
              skip(/*   */)
              val mappedMethodName = readWhile { it != '(' }
              parseMethod(type, mappedMethodName)
            }
            else        -> {
              val type = readDescriptorWhile { it != ' ' }
              skip(/* */)
              val mappedName = readWhile { it != ' ' && it != '(' }
              when (peek()) {
                ' ' -> {
                  skip4(/*  ->  */)
                  val obfuscatedFieldName = remaining()
                  fieldMapping[obfuscatedFieldName] = Mapping.Field(mappedName)
                }
                '(' -> parseMethod(type = type, mappedMethodName = mappedName)
              }
            }
          }
        }
        else -> {
          val mappedClassName = readInternalNameWhile { it != ' ' }
          skip4(/*  ->  */)
          val obfuscatedClassName = readInternalNameWhile { it != ':' }
          fieldMapping = hashMapOf()
          methodMapping = hashMapOf()
          classMapping[obfuscatedClassName] = Mapping.Class(mappedClassName, fieldMapping, methodMapping)
        }
      }
    }

    return Mapping(classMapping)
  }

  private inline fun readInternalNameWhile(predicate: (Char) -> Boolean): String {
    val builder = StringBuilder()
    while (cursor < line.length && predicate(peek())) {
      when (val char = read()) {
        '.'  -> builder.append('/')
        else -> builder.append(char)
      }
    }
    return builder.toString()
  }

  private inline fun readDescriptorWhile(predicate: (Char) -> Boolean): String {
    val builder = StringBuilder()
    var dimension = 0
    while (cursor < line.length && predicate(peek())) {
      when (val char = read()) {
        '.'  -> builder.append('/')
        '['  -> {
          dimension++
          skip(/* ] */)
        }
        else -> builder.append(char)
      }
    }
    return "${"[".repeat(dimension)}${
      when (val name = builder.toString()) {
        "void"    -> "V"
        "boolean" -> "Z"
        "char"    -> "C"
        "byte"    -> "B"
        "short"   -> "S"
        "int"     -> "I"
        "float"   -> "F"
        "long"    -> "J"
        "double"  -> "D"
        else      -> "L$name;"
      }
    }"
  }

  private inline fun readWhile(predicate: (Char) -> Boolean): String {
    val start = cursor
    while (cursor < line.length && predicate(peek())) {
      skip()
    }
    return line.substring(start, cursor)
  }

  private fun nextLine(): Boolean {
    return if (lines.hasNext()) {
      line = lines.next()
      cursor = 0
      true
    } else {
      false
    }
  }

  private inline fun skip() {
    ++cursor
  }

  private inline fun skip4() {
    cursor += 4
  }

  private inline fun read(): Char {
    return line[cursor++]
  }

  private inline fun peek(): Char {
    return line[cursor]
  }

  private inline fun remaining(): String {
    return line.substring(cursor)
  }

  companion object {
    fun parse(lines: Iterator<String>): Mapping {
      return MappingParser(lines).parse()
    }
  }
}
