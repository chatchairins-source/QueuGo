package com.queuego.ridermaplibreosm

import java.io.File
import java.io.RandomAccessFile

data class QueueGoPmTilesHeader(
    val specVersion: Int,
    val tileType: Int,
    val minZoom: Int,
    val maxZoom: Int
)

object QueueGoPmTilesArchive {
    const val HEADER_SIZE_BYTES = 127
    const val SPEC_VERSION_V3 = 3
    const val TILE_TYPE_MVT = 1

    private val magic = byteArrayOf(
        'P'.code.toByte(),
        'M'.code.toByte(),
        'T'.code.toByte(),
        'i'.code.toByte(),
        'l'.code.toByte(),
        'e'.code.toByte(),
        's'.code.toByte()
    )

    fun inspect(file: File): QueueGoPmTilesHeader? {
        if (!file.isFile || file.length() < HEADER_SIZE_BYTES) return null
        return runCatching {
            RandomAccessFile(file, "r").use { input ->
                val header = ByteArray(HEADER_SIZE_BYTES)
                input.readFully(header)
                if (!header.copyOfRange(0, magic.size).contentEquals(magic)) return null
                val version = header[7].toInt() and 0xff
                val tileType = header[99].toInt() and 0xff
                val minZoom = header[100].toInt() and 0xff
                val maxZoom = header[101].toInt() and 0xff
                if (version != SPEC_VERSION_V3) return null
                if (tileType != TILE_TYPE_MVT) return null
                if (minZoom > maxZoom || maxZoom > 24) return null
                QueueGoPmTilesHeader(
                    specVersion = version,
                    tileType = tileType,
                    minZoom = minZoom,
                    maxZoom = maxZoom
                )
            }
        }.getOrNull()
    }

    fun isValidVectorV3(file: File): Boolean = inspect(file) != null

    fun requireValidVectorV3(file: File): QueueGoPmTilesHeader =
        checkNotNull(inspect(file)) {
            "Invalid PMTiles v3 MVT archive"
        }
}
