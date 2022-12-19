package mcx.util.cache

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest

private val versionManifestUrl: URL = URL("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")

private val digest: MessageDigest = MessageDigest.getInstance("SHA-1")

private val json: Json = Json { ignoreUnknownKeys = true }

@OptIn(ExperimentalSerializationApi::class)
fun fetchVersionManifest(): VersionManifest {
  return versionManifestUrl
    .openStream()
    .use {
      json.decodeFromStream(it)
    }
}

fun fetchLatestPackage(
  type: VersionManifest.Version.Type,
  manifest: VersionManifest,
): Package {
  val id = when (type) {
    VersionManifest.Version.Type.RELEASE  -> manifest.latest.release
    VersionManifest.Version.Type.SNAPSHOT -> manifest.latest.snapshot
    else                                  -> error("unexpected type: $type")
  }
  return fetchPackage(id, manifest)
}

@OptIn(ExperimentalSerializationApi::class)
fun fetchPackage(
  id: String,
  manifest: VersionManifest,
): Package {
  val version = manifest.versions.find { it.id == id } ?: error("unknown version: $id")
  return fetch(version.url.openStream(), version.sha1) {
    json.decodeFromStream(it)
  }
}

fun <T> fetch(
  input: InputStream,
  sha1: String,
  action: (InputStream) -> T,
): T {
  digest.reset()
  return DigestInputStream(input, digest)
    .use { action(it) }
    .also { check(sha1 == getSha1()) }
}

@OptIn(ExperimentalUnsignedTypes::class)
private fun getSha1(): String {
  return digest
    .digest()
    .asUByteArray()
    .joinToString("") {
      it
        .toString(16)
        .padStart(2, '0')
    }
}
