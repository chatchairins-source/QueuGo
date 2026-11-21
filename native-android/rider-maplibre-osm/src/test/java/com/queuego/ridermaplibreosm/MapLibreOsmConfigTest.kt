package com.queuego.ridermaplibreosm

import java.io.File
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

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRelativePmTilesPath() {
        MapLibreOsmConfig.localPmTiles(File("buriram.pmtiles"), 14.99, 103.10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedSourceScheme() {
        MapLibreOsmConfig("ftp://tiles.invalid/map", 14.99, 103.10)
    }
}
