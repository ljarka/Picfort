package com.github.ljarka.picfort.backup

import java.io.InputStream
import java.security.MessageDigest

/** SHA-1 of the stream as lowercase hex: what Immich uses to detect duplicates. */
fun sha1Hex(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-1")
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
