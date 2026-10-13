package com.queuego.ridernavigationmap

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.longdo.api.MapGLSurfaceView
import java.util.concurrent.CountDownLatch

/**
 * Debug-only host for the standalone navigation map. Coordinates are renderer fixtures only:
 * there are no QueueGo accounts, jobs, orders or Production writes in this activity.
 */
class NavigationMapRuntimeActivity : ComponentActivity() {
    val mapReady = CountDownLatch(1)

    @Volatile
    var latestDiagnostics = NavigationMapDiagnostics(0, 0, 0, 0)
        private set

    private val mapState = mutableStateOf(runtimeState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RiderNavigationMap(
                stateKey = "runtime-session",
                state = mapState.value,
                modifier = Modifier.fillMaxSize(),
                onMapReady = { if (it) mapReady.countDown() },
                onDiagnostics = { latestDiagnostics = it }
            )
        }
    }

    fun refreshRiderFix(step: Int) {
        val previous = mapState.value
        mapState.value = previous.copy(
            rider = NavigationMapFix(
                point = NavigationMapPoint(
                    latitude = 13.75630 + step * 0.00001,
                    longitude = 100.50180 + step * 0.00001
                ),
                bearingDegrees = (15f + step * 3f) % 360f,
                accuracyMeters = 6f,
                elapsedRealtimeNanos = step.toLong()
            )
        )
    }

    fun refreshEquivalentOrderSnapshot() {
        val previous = mapState.value
        mapState.value = previous.copy(
            stops = previous.stops.map { it.copy() },
            routes = previous.routes.map { segment ->
                segment.copy(points = segment.points.toList())
            }
        )
    }

    fun advanceToSecondStop() {
        val previous = mapState.value
        mapState.value = previous.copy(
            stops = previous.stops.map { stop ->
                if (stop.id == "pickup-1") stop.copy(completed = true) else stop
            },
            activeStopId = "dropoff-2"
        )
    }

    fun mapViewIdentity(): Int = System.identityHashCode(requireNotNull(findMapView(window.decorView)))

    fun sdkMapIdentity(): Int {
        val view = requireNotNull(findMapView(window.decorView))
        return System.identityHashCode(requireNotNull(view.renderer.map))
    }

    private fun findMapView(view: View): MapGLSurfaceView? {
        if (view is MapGLSurfaceView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findMapView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun runtimeState(): RiderNavigationMapState {
        val pickup = NavigationMapStop(
            id = "pickup-1",
            sequence = 1,
            kind = NavigationStopKind.PICKUP,
            point = NavigationMapPoint(13.7582, 100.5042),
            label = "pickup"
        )
        val dropoff = NavigationMapStop(
            id = "dropoff-2",
            sequence = 2,
            kind = NavigationStopKind.DROPOFF,
            point = NavigationMapPoint(13.7614, 100.5085),
            label = "dropoff"
        )
        val start = NavigationMapPoint(13.7563, 100.5018)
        return RiderNavigationMapState(
            rider = NavigationMapFix(start, bearingDegrees = 15f, accuracyMeters = 6f),
            stops = listOf(pickup, dropoff),
            routes = listOf(
                NavigationRouteSegment(
                    id = "fixture-leg-1",
                    toStopId = pickup.id,
                    points = listOf(start, NavigationMapPoint(13.7572, 100.5030), pickup.point)
                ),
                NavigationRouteSegment(
                    id = "fixture-leg-2",
                    toStopId = dropoff.id,
                    points = listOf(pickup.point, NavigationMapPoint(13.7598, 100.5062), dropoff.point)
                )
            ),
            activeStopId = pickup.id,
            followRider = true
        )
    }
}
