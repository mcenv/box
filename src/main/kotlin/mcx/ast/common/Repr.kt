package mcx.ast.common

enum class Repr(val id: String) {
  END("end"),
  BYTE("byte"),
  SHORT("short"),
  INT("int"),
  LONG("long"),
  FLOAT("float"),
  DOUBLE("double"),
  STRING("string"),
  BYTE_ARRAY("byte_array"),
  INT_ARRAY("int_array"),
  LONG_ARRAY("long_array"),
  LIST("list"),
  COMPOUND("compound"),
  REF("ref"),
}
