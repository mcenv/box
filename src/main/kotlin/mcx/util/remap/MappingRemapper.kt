package mcx.util.remap

import org.objectweb.asm.commons.Remapper

class MappingRemapper(
  private val mapping: Mapping,
  private val hierarchy: TypeHierarchy,
) : Remapper() {
  override fun mapMethodName(
    owner: String,
    name: String,
    descriptor: String,
  ): String {
    return hierarchy[owner].firstNotNullOfOrNull {
      mapping.classMapping[it]?.methodMapping?.get(name to mapMethodDesc(descriptor))?.name
    } ?: name
  }

  override fun mapRecordComponentName(
    owner: String,
    name: String,
    descriptor: String,
  ): String {
    return hierarchy[owner].firstNotNullOfOrNull {
      mapping.classMapping[it]?.fieldMapping?.get(name)?.name
    } ?: name
  }

  override fun mapFieldName(
    owner: String,
    name: String,
    descriptor: String,
  ): String {
    return hierarchy[owner].firstNotNullOfOrNull {
      mapping.classMapping[it]?.fieldMapping?.get(name)?.name
    } ?: name
  }

  override fun map(internalName: String): String {
    return mapping.classMapping[internalName]?.name ?: internalName
  }
}
