package mcx.ast

enum class Registry(
  val singular: String,
  val plural: String,
  val extension: String,
) {
  FUNCTIONS(
    "function",
    "functions",
    "mcfunction",
  ),
  PREDICATES(
    "predicate",
    "predicates",
    "json",
  ),
  RECIPES(
    "recipe",
    "recipes",
    "json",
  ),
  LOOT_TABLES(
    "loot_table",
    "loot_tables",
    "json",
  ),
  ITEM_MODIFIERS(
    "item_modifier",
    "item_modifiers",
    "json",
  ),
  ADVANCEMENTS(
    "advancement",
    "advancements",
    "json",
  ),
  DIMENSION_TYPE(
    "dimension_type",
    "dimension_type",
    "json",
  ),
  WORLDGEN_BIOME(
    "worldgen/biome",
    "worldgen/biome",
    "json",
  ),
  // TODO: more worldgen registries

  DIMENSION(
    "dimension",
    "dimension",
    "json",
  );
}
