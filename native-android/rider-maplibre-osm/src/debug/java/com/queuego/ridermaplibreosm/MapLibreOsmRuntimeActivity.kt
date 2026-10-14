package com.queuego.ridermaplibreosm

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import java.io.File
import java.util.concurrent.CountDownLatch

class MapLibreOsmRuntimeActivity : Activity() {
    val mapReady = CountDownLatch(1)
    val mapFullyRendered = CountDownLatch(1)

    private lateinit var root: FrameLayout
    private lateinit var mapView: QueueGoMapLibreOsmView
    private lateinit var baseConfig: MapLibreOsmConfig
    private var usingRealPmTiles = false

    private fun runtimeConfig(): MapLibreOsmConfig {
        val realAsset = "sawaijee-shortbread.pmtiles"
        val available = assets.list("")?.toSet().orEmpty()
        if (realAsset in available) {
            val directory = File(filesDir, "queuego-runtime-map").apply { mkdirs() }
            val localFile = File(directory, realAsset)
            assets.open(realAsset).use { input ->
                localFile.outputStream().use { output -> input.copyTo(output) }
            }
            QueueGoPmTilesArchive.requireValidVectorV3(localFile)
            usingRealPmTiles = true
            return MapLibreOsmConfig.localPmTiles(
                file = localFile,
                initialLatitude = 14.9058,
                initialLongitude = 103.14682,
                initialZoom = 16.0
            )
        }
        return MapLibreOsmConfig(
            sourceUri = "asset://queuego-runtime-tilejson.json",
            initialLatitude = 14.9820,
            initialLongitude = 103.1020,
            initialZoom = 16.0
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        baseConfig = runtimeConfig()
        root = FrameLayout(this)
        setContentView(root)
        mapView = QueueGoMapLibreOsmView(this).apply {
            onMapReady = { mapReady.countDown() }
            onMapFullyRendered = { mapFullyRendered.countDown() }
            onCreate(savedInstanceState)
            setConfig(baseConfig)
        }
        root.addView(
            mapView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        mapView.onLowMemory()
        super.onLowMemory()
    }

    override fun onDestroy() {
        mapView.destroy()
        super.onDestroy()
    }

    fun refreshEquivalentConfig() {
        mapView.setConfig(baseConfig.copy())
    }

    fun refreshCamera(step: Int) {
        mapView.setConfig(
            baseConfig.copy(
                initialLatitude = baseConfig.initialLatitude + step * 0.00001,
                initialLongitude = baseConfig.initialLongitude + step * 0.00001
            )
        )
    }

    fun detachForAnotherTab() {
        (mapView.parent as? ViewGroup)?.removeView(mapView)
    }

    fun reattachAfterReturningHome() {
        if (mapView.parent == null) {
            root.addView(
                mapView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    fun isUsingRealPmTiles(): Boolean = usingRealPmTiles

    fun renderedVillageRoadFeatureCount(): Int =
        mapView.renderedFeatureCount(ShortbreadVillageStyle.villageQaLayerIds())

    fun mapViewIdentity(): Int = mapView.mapViewIdentity()
    fun mapIdentity(): Int = requireNotNull(mapView.mapIdentity())
    fun styleApplyCount(): Int = mapView.styleApplyCount()
    fun mapDestroyed(): Boolean = mapView.isDestroyed()
}
