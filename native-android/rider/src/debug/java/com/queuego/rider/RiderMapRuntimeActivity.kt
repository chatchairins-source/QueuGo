package com.queuego.rider

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.longdo.api.MapGLSurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import java.util.concurrent.CountDownLatch

/** Mounts the actual Home map without creating any authentication or business data. */
class RiderMapRuntimeActivity : ComponentActivity() {
    val mapReady = CountDownLatch(1)
    private val recenterSignal = mutableIntStateOf(0)
    fun recenterMap() { recenterSignal.intValue += 1 }
    private fun actualMap(): com.longdo.api.Map {
        fun find(view: View): MapGLSurfaceView? {
            if (view is MapGLSurfaceView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) {
                find(view.getChildAt(i))?.let { return it }
            }
            return null
        }
        val map = requireNotNull(find(window.decorView)?.renderer?.map) { "Actual SDK map is not mounted" }
        return map
    }

    internal fun showRoadDisplayEvidence(paths: List<List<RiderRoutePoint>>) {
        val map = actualMap()
        val first = riderRouteLine(paths.first(), true, resources.displayMetrics.density)
        val later = riderRouteLine(paths.last(), false, resources.displayMetrics.density)
        check(first.getColor()[3] == 1f && later.getColor()[3] < .35f)
        check(first.getColor()[0] < later.getColor()[0])
        map.addLine(later)
        map.addLine(first)
    }

    fun actualMapCamera(): DoubleArray {
        val map = actualMap()
        // Exercise the real SDK's same-level path, which dereferences postZoom in 0.1.8.
        check(map.getZoomListener() != null) { "Real map zoom callback is missing" }
        map.setZoom(map.getZoom())
        val location = map.getLocation()
        return doubleArrayOf(location.lon, location.lat, map.getZoom().toDouble())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RiderLongdoMap(
                modifier = Modifier.fillMaxSize(),
                stateKey = "runtime-map",
                job = null,
                marketPickups = emptyList(),
                recenterSignal = recenterSignal.intValue,
                onMapReady = { ready -> if (ready) mapReady.countDown() }
            )
        }
    }
}
