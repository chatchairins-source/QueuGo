package com.queuego.shared

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class QueueGoDebugMapArchiveTest {
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
