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
    @Test fun rejectsSymlinkedOfflineGraphRootAndGeneratedConfigWithoutTouchingTarget() {
        val directory = Files.createTempDirectory("queuego-graph-root-link").toFile()
        try {
            val real = directory.resolve("real")
            org.junit.Assert.assertTrue(real.mkdir())
            val linkedRoot = directory.resolve("routing-root")
            Files.createSymbolicLink(linkedRoot.toPath(), real.toPath())
            assertThrows(IllegalStateException::class.java) {
                rejectOfflineRoutingSymlink(linkedRoot, "offline graph root")
            }
            val config = real.resolve("config.json")
            config.writeText("keep")
            val linkedConfig = directory.resolve("valhalla.json")
            Files.createSymbolicLink(linkedConfig.toPath(), config.toPath())
            assertThrows(IllegalStateException::class.java) {
                rejectOfflineRoutingSymlink(linkedConfig, "generated offline routing config")
            }
            org.junit.Assert.assertEquals("keep", config.readText())
            rejectOfflineRoutingSymlink(real, "offline graph root")
        } finally {
            directory.deleteRecursively()
        }
    }
    @Test fun refusesSymlinkAtOfflineGraphCachePath() {
        val directory = Files.createTempDirectory("queuego-graph-symlink").toFile()
        try {
            val external = directory.resolve("external.tar")
            external.writeBytes(ByteArray(128) { 7 })
            val cached = directory.resolve("tiles.tar")
            Files.createSymbolicLink(cached.toPath(), external.toPath())
            assertThrows(IllegalStateException::class.java) {
                reclaimInvalidOfflineGraphCache(cached)
            }
            org.junit.Assert.assertTrue(external.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
    @Test fun refusesSymlinkOrDirectoryAtInterruptedCopyWithoutTouchingTarget() {
        val directory = Files.createTempDirectory("queuego-part-guard").toFile()
        try {
            val external = directory.resolve("external.tar")
            external.writeText("preserve")
            val part = directory.resolve("tiles.tar.part")
            Files.createSymbolicLink(part.toPath(), external.toPath())
            assertThrows(IllegalStateException::class.java) { reclaimInterruptedOfflineGraphCopy(part) }
            org.junit.Assert.assertEquals("preserve", external.readText())
            Files.delete(part.toPath())
            org.junit.Assert.assertTrue(part.mkdir())
            assertThrows(IllegalStateException::class.java) { reclaimInterruptedOfflineGraphCopy(part) }
            org.junit.Assert.assertTrue(part.isDirectory)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun removesInterruptedRegularFileAndAllowsMissingPart() {
        val directory = Files.createTempDirectory("queuego-part-recovery").toFile()
        try {
            val part = directory.resolve("tiles.tar.part")
            part.writeText("incomplete")
            reclaimInterruptedOfflineGraphCopy(part)
            assertFalse(part.exists())
            reclaimInterruptedOfflineGraphCopy(part)
        } finally {
            directory.deleteRecursively()
        }
    }
}
