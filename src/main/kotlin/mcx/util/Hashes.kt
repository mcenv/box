package mcx.util

import java.security.MessageDigest

fun hash(
  digest: MessageDigest,
  string: String,
): String =
  digest
    .digest(string.encodeToByteArray())
    .joinToString("") {
      it
        .toUByte()
        .toString(16)
        .padStart(2, '0')
    }
