package com.queuego.ridermaplibreosm

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLibreOsmConfigTest {
    @Test fun acceptsOnlineTileJson() {
        val config = MapLibreOsmConfig.onlineTileJson(
            tileJsonUrl = "https://tiles.queuego.example/shortbread/tilejson.json",
            initialLatitude = 14.99,
            initialLongitude = 103.10
        )
        assertEquals("https://tiles.queuego.example/shortbread/tilejson.json", config.sourceUri)
        assertFalse(config.isOfflinePmTiles)
    }

    @Test fun localPmTilesUsesAbsoluteFileUri() {
        val file = File("/data/user/0/com.queuego.rider/files/queuego-maps/buriram.pmtiles")
        val config = MapLibreOsmConfig.localPmTiles(file, 14.99, 103.10)
        assertEquals("pmtiles://file://${file.absolutePath}", config.sourceUri)
        assertTrue(config.isOfflinePmTiles)
    }

    @Test fun prefersOnlineSourceUntilOfflineArchiveIsValid() {
        val directory = createTempDirectory("queuego-map-config-").toFile()
        try {
            val local = File(directory, "buriram.pmtiles")
            local.writeText("not-a-pmtiles-archive")
            val fallback = "https://tiles.queuego.example/shortbread/tilejson.json"

            val config = MapLibreOsmConfig.preferOfflinePmTiles(
                localFile = local,
                fallbackTileJsonUrl = fallback,
                initialLatitude = 14.9058,
                initialLongitude = 103.14682
            )

            assertEquals(fallback, config.sourceUri)
            assertFalse(config.isOfflinePmTiles)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun prefersValidOfflineArchiveWithoutNetworkSource() {
        val directory = createTempDirectory("queuego-map-config-valid-").toFile()
        try {
            val local = File(directory, "buriram.pmtiles")
            val header = ByteArray(QueueGoPmTilesArchive.HEADER_SIZE_BYTES)
            "PMTiles".toByteArray().copyInto(header)
            header[7] = QueueGoPmTilesArchive.SPEC_VERSION_V3.toByte()
            header[99] = QueueGoPmTilesArchive.TILE_TYPE_MVT.toByte()
            header[100] = 0
            header[101] = 14
            local.writeBytes(header)

            val config = MapLibreOsmConfig.preferOfflinePmTiles(
                localFile = local,
                fallbackTileJsonUrl = "https://tiles.queuego.example/shortbread/tilejson.json",
                initialLatitude = 14.9058,
                initialLongitude = 103.14682
            )

            assertTrue(config.isOfflinePmTiles)
            assertEquals("pmtiles://file://${local.absolutePath}", config.sourceUri)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRelativePmTilesPath() {
        MapLibreOsmConfig.localPmTiles(File("buriram.pmtiles"), 14.99, 103.10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedSourceScheme() {
        MapLibreOsmConfig("ftp://tiles.invalid/map", 14.99, 103.10)
    }
}
