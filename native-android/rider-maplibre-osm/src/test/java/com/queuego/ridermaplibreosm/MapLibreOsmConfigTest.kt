package com.queuego.ridermaplibreosm

import org.junit.Assert.assertEquals
import org.junit.Test

class MapLibreOsmConfigTest {
    @Test fun acceptsRemoteAndOfflineTileJsonSchemes() {
        listOf(
            "https://tiles.example.com/tilejson.json",
            "file:///data/user/0/com.queuego.rider/files/offline/tilejson.json",
            "asset://queuego/tilejson.json"
        ).forEach { uri ->
            val config = MapLibreOsmConfig(uri, 14.90544, 103.14653, 17.0)
            assertEquals(uri, config.tileJsonUrl)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedTileJsonScheme() {
        MapLibreOsmConfig("content://unsupported", 14.90544, 103.14653)
    }
}
