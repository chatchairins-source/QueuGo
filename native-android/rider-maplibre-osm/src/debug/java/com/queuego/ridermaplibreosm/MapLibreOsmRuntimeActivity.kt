package com.queuego.ridermaplibreosm

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import java.util.concurrent.CountDownLatch

class MapLibreOsmRuntimeActivity : Activity() {
    val mapReady = CountDownLatch(1)

    private lateinit var root: FrameLayout
    private lateinit var mapView: QueueGoMapLibreOsmView

    private val baseConfig = MapLibreOsmConfig(
        sourceUri = "asset://queuego-runtime-tilejson.json",
        initialLatitude = 14.9820,
        initialLongitude = 103.1020,
        initialZoom = 16.0
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    fun setVerifiedLocalRoadFixture(version: Int = 1) {
        val delta = version.coerceAtLeast(1) * 0.0001
        mapView.setLocalRoads(
            listOf(
                QueueGoLocalRoad(
                    id = "runtime-local-road",
                    points = listOf(
                        QueueGoLocalRoadPoint(14.9818, 103.1018),
                        QueueGoLocalRoadPoint(14.9822 + delta, 103.1023 + delta)
                    ),
                    source = QueueGoLocalRoadSource.FIELD_SURVEY,
                    verified = true,
                    access = QueueGoLocalRoadAccess.MOTORCYCLE_AND_CAR,
                    oneWay = false,
                    surface = QueueGoLocalRoadSurface.PAVED,
                    name = "runtime fixture"
                )
            )
        )
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

    fun mapViewIdentity(): Int = mapView.mapViewIdentity()
    fun mapIdentity(): Int = requireNotNull(mapView.mapIdentity())
    fun styleApplyCount(): Int = mapView.styleApplyCount()
    fun localRoadUpdateCount(): Int = mapView.localRoadUpdateCount()
    fun hasLocalRoadSource(): Boolean = mapView.hasLocalRoadSource()
    fun hasLocalRoadLayers(): Boolean = mapView.hasLocalRoadLayers()
    fun mapDestroyed(): Boolean = mapView.isDestroyed()
}
