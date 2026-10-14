package com.queuego.ridermaplibreosm

import org.junit.Assert.assertEquals
import org.junit.Test

class MapLibreOsmConfigTest {
    @Test fun acceptsRemoteTileJsonAndLocalPmTilesSchemes() {
        listOf(
            "https://tiles.example.com/tilejson.json",
            "file:///data/user/0/com.queuego.rider/files/offline/tilejson.json",
            "asset://queuego/tilejson.json",
            "pmtiles://https://cdn.example.com/buriram.pmtiles",
            "pmtiles://file:///data/user/0/com.queuego.rider/files/queuego-map-packages/buriram.pmtiles",
            "pmtiles://asset://starter.pmtiles"
        ).forEach { uri ->
            val config = MapLibreOsmConfig(uri, 14.90544, 103.14653, 17.0)
            assertEquals(uri, config.vectorSourceUri)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedVectorSourceScheme() {
        MapLibreOsmConfig("content://unsupported", 14.90544, 103.14653)
    }
}
