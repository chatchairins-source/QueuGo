package com.queuego.ridermaplibreosm

import android.app.Activity
import android.os.Bundle
import java.util.concurrent.CountDownLatch

/**
 * Debug-only renderer fixture. No QueueGo account, order, Rider job or Production write is used.
 * The OSMF endpoint is intentionally confined to this runtime gate; Production renderer config
 * stays provider-agnostic so QueueGo can switch to self-hosted/PMTiles without an app rewrite.
 */
class MapLibreOsmRuntimeActivity : Activity() {
    val mapReady = CountDownLatch(1)

    private lateinit var mapHost: QueueGoMapLibreOsmView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mapHost = QueueGoMapLibreOsmView(this).apply {
            onMapReady = { mapReady.countDown() }
            setConfig(
                MapLibreOsmConfig(
                    tileJsonUrl = "https://vector.openstreetmap.org/shortbread_v1/tilejson.json",
                    initialLatitude = 14.90544,
                    initialLongitude = 103.14653,
                    initialZoom = 17.0
                )
            )
        }
        setContentView(mapHost)
    }

    override fun onStart() {
        super.onStart()
        mapHost.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapHost.onResume()
    }

    override fun onPause() {
        mapHost.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapHost.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        mapHost.onLowMemory()
        super.onLowMemory()
    }

    override fun onDestroy() {
        mapHost.destroy()
        super.onDestroy()
    }

    fun mapViewIdentity(): Int = mapHost.mapViewIdentity()
    fun mapIdentity(): Int? = mapHost.mapIdentity()
    fun villageRoadLayerCount(): Int = mapHost.villageRoadLayerCount()
    fun renderedVillageRoadFeatureCount(): Int = mapHost.renderedVillageRoadFeatureCount()
}
