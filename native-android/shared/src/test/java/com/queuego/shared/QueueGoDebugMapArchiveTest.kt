package com.queuego.shared

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class QueueGoDebugMapArchiveTest {
    @Test fun headerHandlesShortReadsAndRejectsTruncationOrOtherVersions() {
        val valid = byteArrayOf(80, 77, 84, 105, 108, 101, 115, 3)
        val shortReads = object : java.io.ByteArrayInputStream(valid) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(1, len))
        }
        assertTrue(queueGoPmTilesV3Header(shortReads))
        for (size in 0..7) assertFalse(queueGoPmTilesV3Header(valid.copyOf(size).inputStream()))
        assertFalse(queueGoPmTilesV3Header(valid.copyOf().apply { this[7] = 2 }.inputStream()))
    }
    @Test fun releaseCannotSelectAnInjectedArchiveAndDebugRejectsMissingOrInvalidFiles() {
        val archive = File.createTempFile("queuego-map", ".pmtiles")
        try {
            assertNull(queueGoDebugMapArchive(true, archive))
            archive.writeBytes(byteArrayOf(80, 77, 84, 105, 108, 101, 115, 3))
            assertNull(queueGoDebugMapArchive(false, archive))
            assertEquals(archive, queueGoDebugMapArchive(true, archive))
            archive.writeText("not a PMTiles archive")
            assertNull(queueGoDebugMapArchive(true, archive))
        } finally { archive.delete() }
        assertNull(queueGoDebugMapArchive(true, archive))
    }
}
