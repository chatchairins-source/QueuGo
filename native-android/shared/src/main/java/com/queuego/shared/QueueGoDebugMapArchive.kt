package com.queuego.shared

import java.io.File
import java.io.DataInputStream
import java.io.InputStream

/** Handles short reads and truncated assets without accepting a partial header. */
internal fun queueGoPmTilesV3Header(input: InputStream): Boolean = runCatching {
    val header = ByteArray(8)
    DataInputStream(input).readFully(header)
    header.contentEquals(byteArrayOf(80, 77, 84, 105, 108, 101, 115, 3))
}.getOrDefault(false)

/** Explicit instrumentation data only. Never selected by a release APK. */
internal fun queueGoDebugMapArchive(debuggable: Boolean, archive: File): File? {
    if (!debuggable || !archive.isFile) return null
    return runCatching {
        archive.inputStream().use { input ->
            if (queueGoPmTilesV3Header(input)) archive else null
        }
    }.getOrNull()
}
