package mcx.data

enum class NbtType(val id: String) {
  END("end"),
  BYTE("byte"),
  SHORT("short"),
  INT("int"),
  LONG("long"),
  FLOAT("float"),
  DOUBLE("double"),
  BYTE_ARRAY("byte_array"),
  STRING("string"),
  LIST("list"),
  COMPOUND("compound"),
  INT_ARRAY("int_array"),
  LONG_ARRAY("long_array"),
}
