package com.queuego.ridernavigationmap

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Files

class BundledOfflineRoutingCacheRecoveryTest {
    @Test fun removesCorruptCachedBytesBeforeReplacementSpaceCheck() {
        val directory = Files.createTempDirectory("queuego-graph-cache").toFile()
        try {
            val cached = directory.resolve("tiles.tar")
            cached.writeBytes(ByteArray(4096) { 42 })
            reclaimInvalidOfflineGraphCache(cached)
            assertFalse(cached.exists())
            // Repeated interrupted-install recovery must be idempotent.
            reclaimInvalidOfflineGraphCache(cached)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun refusesToDeleteUnexpectedDirectoryAtGraphFilePath() {
        val directory = Files.createTempDirectory("queuego-graph-cache").toFile()
        try {
            val cached = directory.resolve("tiles.tar")
            cached.mkdir()
            assertThrows(IllegalStateException::class.java) {
                reclaimInvalidOfflineGraphCache(cached)
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
