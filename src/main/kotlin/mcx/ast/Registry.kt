package mcx.ast

enum class Registry(
  val string: String,
  val extension: String,
) {
  FUNCTIONS(
    "functions",
    "mcfunction",
  ),
  PREDICATES(
    "predicates",
    "json",
  ),
  RECIPES(
    "recipes",
    "json",
  ),
  LOOT_TABLES(
    "loot_tables",
    "json",
  ),
  ITEM_MODIFIERS(
    "item_modifiers",
    "json",
  ),
  ADVANCEMENTS(
    "advancements",
    "json",
  ),
  DIMENSION_TYPE(
    "dimension_type",
    "json",
  ),
  WORLDGEN_BIOME(
    "worldgen/biome",
    "json",
  ),
  // TODO: more worldgen registries

  DIMENSION(
    "dimension",
    "json",
  ),
}
