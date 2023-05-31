package mcx.util.nbt

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class AsStringTag

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class AsIntTag

internal inline fun <reified T> List<Annotation>.has(): Boolean {
  return this.any { it is T }
}
