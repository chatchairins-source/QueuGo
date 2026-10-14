package com.queuego.ridermaplibreosm

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import java.io.File
import java.util.concurrent.CountDownLatch

class MapLibreOsmRuntimeActivity : Activity() {
    val mapReady = CountDownLatch(1)

    private lateinit var root: FrameLayout
    private lateinit var mapView: QueueGoMapLibreOsmView
    private lateinit var baseConfig: MapLibreOsmConfig
    private lateinit var runtimePmTiles: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtimePmTiles = File(filesDir, "queuego-runtime.pmtiles")
        assets.open("queuego-runtime.pmtiles").use { input ->
            runtimePmTiles.outputStream().use { output -> input.copyTo(output) }
        }
        baseConfig = MapLibreOsmConfig.localPmTiles(
            file = runtimePmTiles,
            initialLatitude = 14.9820,
            initialLongitude = 103.1020,
            initialZoom = 16.0
        )
        root = FrameLayout(this)
        setContentView(root)
        mapView = QueueGoMapLibreOsmView(this).apply {
            onMapReady = { mapReady.countDown() }
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

    fun usesLocalPmTiles(): Boolean =
        baseConfig.isOfflinePmTiles && runtimePmTiles.isFile && runtimePmTiles.length() > 0L

    fun renderedResidentialRoadCount(): Int =
        mapView.renderedFeatureCount("queuego-road-residential")

    fun mapViewIdentity(): Int = mapView.mapViewIdentity()
    fun mapIdentity(): Int = requireNotNull(mapView.mapIdentity())
    fun styleApplyCount(): Int = mapView.styleApplyCount()
    fun mapDestroyed(): Boolean = mapView.isDestroyed()
}
