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

    fun applyLocalRoadFixture(version: Int = 1) {
        val verified = QueueGoLocalRoadSegment(
            id = "runtime:verified:$version",
            points = listOf(
                QueueGoLocalRoadPoint(14.9810, 103.1010),
                QueueGoLocalRoadPoint(14.9820, 103.1020),
                QueueGoLocalRoadPoint(14.9830, 103.1030)
            ),
            roadClass = QueueGoLocalRoadClass.SERVICE,
            source = QueueGoLocalRoadSource.FIELD_SURVEY,
            verification = QueueGoLocalRoadVerification.VERIFIED,
            access = QueueGoLocalRoadAccess.DELIVERY,
            sourceReference = "runtime-survey:$version",
            verifiedAtEpochMs = 1_797_000_000_000L + version
        )
        val candidate = QueueGoLocalRoadSegment(
            id = "runtime:candidate:$version",
            points = listOf(
                QueueGoLocalRoadPoint(14.9820, 103.1020),
                QueueGoLocalRoadPoint(14.9840, 103.1040)
            ),
            roadClass = QueueGoLocalRoadClass.ALLEY,
            source = QueueGoLocalRoadSource.RIDER_TRACE_AGGREGATE,
            verification = QueueGoLocalRoadVerification.CANDIDATE,
            access = QueueGoLocalRoadAccess.MOTORCYCLE,
            sourceReference = "runtime-trace:$version"
        )
        mapView.setLocalRoads(listOf(verified, candidate))
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
    fun localRoadApplyCount(): Int = mapView.localRoadApplyCount()
    fun localRoadSegmentCount(): Int = mapView.localRoadSegmentCount()
    fun mapDestroyed(): Boolean = mapView.isDestroyed()
}
