package mcx.ast

enum class Registry(val string: String) {
  FUNCTIONS("functions"),
  PREDICATES("predicates"),
  RECIPES("recipes"),
  LOOT_TABLES("loot_tables"),
  ITEM_MODIFIERS("item_modifiers"),
  ADVANCEMENTS("advancements"),
  DIMENSION_TYPE("dimension_type"),
  WORLDGEN_BIOME("worldgen/biome"),

  // TODO: more worldgen registries
  DIMENSION("dimension");

  override fun toString(): String =
    string
}
