package com.queuego.ridernavigationmap

import android.os.Bundle
import android.os.SystemClock
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.longdo.api.MapGLSurfaceView
import java.util.concurrent.CountDownLatch
import kotlin.math.ceil

/**
 * Debug-only host for the standalone navigation map. Coordinates are renderer fixtures only:
 * there are no QueueGo accounts, jobs, orders or Production writes in this activity.
 */
data class NavigationRuntimeFrameTiming(
    val frameCount: Int,
    val p95IntervalMs: Double,
    val maxIntervalMs: Double
)

class NavigationMapRuntimeActivity : ComponentActivity() {
    val mapReady = CountDownLatch(1)

    private val frameIntervalsNanos = ArrayList<Long>(512)
    private var frameCaptureActive = false
    private var previousFrameTimeNanos = 0L
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!frameCaptureActive) return
            if (previousFrameTimeNanos > 0L && frameTimeNanos > previousFrameTimeNanos) {
                frameIntervalsNanos += frameTimeNanos - previousFrameTimeNanos
            }
            previousFrameTimeNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    @Volatile
    var latestDiagnostics = NavigationMapDiagnostics(0, 0, 0, 0)
        private set

    @Volatile
    var userGestureCount: Int = 0
        private set

    private val mapState = mutableStateOf(runtimeState())
    private val mapMounted = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            if (mapMounted.value) {
                RiderNavigationMap(
                    stateKey = "runtime-session",
                    state = mapState.value,
                    modifier = Modifier.fillMaxSize(),
                    onMapReady = { if (it) mapReady.countDown() },
                    onUserGesture = { userGestureCount += 1 },
                    onDiagnostics = { latestDiagnostics = it }
                )
            }
        }
    }

    fun startFrameTimingCapture() {
        frameIntervalsNanos.clear()
        previousFrameTimeNanos = 0L
        frameCaptureActive = true
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stopFrameTimingCapture(): NavigationRuntimeFrameTiming {
        frameCaptureActive = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        val millis = frameIntervalsNanos
            .map { it / 1_000_000.0 }
            .sorted()
        if (millis.isEmpty()) {
            return NavigationRuntimeFrameTiming(0, 0.0, 0.0)
        }
        val p95Index = (ceil(millis.size * 0.95).toInt() - 1)
            .coerceIn(0, millis.lastIndex)
        return NavigationRuntimeFrameTiming(
            frameCount = millis.size,
            p95IntervalMs = millis[p95Index],
            maxIntervalMs = millis.last()
        )
    }

    fun hideMapForAnotherTab() {
        mapMounted.value = false
    }

    fun showMapAfterReturningHome() {
        mapMounted.value = true
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
                speedMetersPerSecond = 8f,
                elapsedRealtimeNanos = (step.toLong() + 1L) * 1_000_000_000L
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

    fun shortenActiveRouteFixture() {
        val previous = mapState.value
        mapState.value = previous.copy(
            routes = previous.routes.map { segment ->
                if (segment.id == "fixture-leg-1" && segment.points.size > 2) {
                    segment.copy(
                        points = listOf(
                            NavigationMapPoint(13.7572, 100.5030),
                            segment.points.last()
                        )
                    )
                } else {
                    segment
                }
            }
        )
    }

    fun replaceActiveRouteFixture() {
        mapState.value = mapState.value.copy(routes = mapState.value.routes.map {
            if (it.id == "fixture-leg-1") it.copy(id = "fixture-reroute") else it
        })
    }

    fun routeLineIdentities(): List<Int> {
        val mapView = requireNotNull(findMapView(window.decorView))
        val activeMap = requireNotNull(mapView.renderer.map)
        return activeMap.allLines.map { line -> System.identityHashCode(line) }
    }

    fun dispatchTapOnMap() {
        dispatchSinglePointerGesture(deltaX = 0f)
    }

    fun dispatchDragOnMap(deltaX: Float = 160f) {
        dispatchSinglePointerGesture(deltaX = deltaX)
    }

    private fun dispatchSinglePointerGesture(deltaX: Float) {
        val view = requireNotNull(findMapView(window.decorView))
        val downTime = SystemClock.uptimeMillis()
        val startX = (view.width.coerceAtLeast(400) / 2f)
        val startY = (view.height.coerceAtLeast(400) / 2f)
        val down = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            startX,
            startY,
            0
        )
        val move = MotionEvent.obtain(
            downTime,
            downTime + 16L,
            MotionEvent.ACTION_MOVE,
            startX + deltaX,
            startY,
            0
        )
        val up = MotionEvent.obtain(
            downTime,
            downTime + 32L,
            MotionEvent.ACTION_UP,
            startX + deltaX,
            startY,
            0
        )
        try {
            view.dispatchTouchEvent(down)
            if (deltaX != 0f) view.dispatchTouchEvent(move)
            view.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            move.recycle()
            up.recycle()
        }
    }

    override fun onDestroy() {
        frameCaptureActive = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDestroy()
    }

    fun hasMapView(): Boolean = findMapView(window.decorView) != null

    fun mapViewIdentity(): Int = System.identityHashCode(requireNotNull(findMapView(window.decorView)))

    fun sdkMapIdentity(): Int {
        val view = requireNotNull(findMapView(window.decorView))
        return System.identityHashCode(requireNotNull(view.renderer.map))
    }

    fun mapViewUsesActivityContext(): Boolean =
        requireNotNull(findMapView(window.decorView)).context === this

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
