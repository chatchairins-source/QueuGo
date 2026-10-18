package com.queuego.shared

import java.io.File

/** Explicit instrumentation data only. Never selected by a release APK. */
internal fun queueGoDebugMapArchive(debuggable: Boolean, archive: File): File? {
    if (!debuggable || !archive.isFile) return null
    return runCatching {
        archive.inputStream().use { input ->
            val header = ByteArray(8)
            if (input.read(header) == 8 && header.contentEquals(byteArrayOf(80, 77, 84, 105, 108, 101, 115, 3))) archive else null
        }
    }.getOrNull()
}
