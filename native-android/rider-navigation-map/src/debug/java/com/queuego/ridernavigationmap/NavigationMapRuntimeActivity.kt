package com.queuego.ridernavigationmap

import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import org.maplibre.android.maps.MapView
import java.util.concurrent.CountDownLatch
import com.queuego.shared.queueGoMapVectorSourceUri
import org.maplibre.android.maps.MapLibreMap

class NavigationMapRuntimeActivity : ComponentActivity() {
    val mapReady = CountDownLatch(1)

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
                    mapSourceUri = if (intent.getBooleanExtra("installedThailandMap", false)) {
                        queueGoMapVectorSourceUri(this)
                    } else "asset://queuego-nav-runtime-tilejson.json",
                    onMapReady = { if (it) mapReady.countDown() },
                    onUserGesture = { userGestureCount += 1 },
                    onDiagnostics = { latestDiagnostics = it }
                )
            }
        }
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
            routes = previous.routes.map { route ->
                route.copy(points = route.points.toList())
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

    fun showDemandHeatmapFixture(level: Int = 1) {
        mapState.value = mapState.value.copy(
            stops = emptyList(),
            routes = emptyList(),
            activeStopId = null,
            demandZones = listOf(
                NavigationDemandZone(
                    id = "demand:a",
                    point = NavigationMapPoint(13.7560, 100.5000),
                    level = level,
                    windowHours = 24
                ),
                NavigationDemandZone(
                    id = "demand:b",
                    point = NavigationMapPoint(13.7600, 100.5060),
                    level = 2,
                    windowHours = 24
                )
            )
        )
    }

    fun clearDemandHeatmapFixture() {
        mapState.value = mapState.value.copy(demandZones = emptyList())
    }

    fun dispatchTapOnMap() {
        dispatchSinglePointerGesture(deltaX = 0f)
    }

    fun dispatchDragOnMap(deltaX: Float = 160f) {
        dispatchSinglePointerGesture(deltaX = deltaX)
    }

    fun dispatchPinchOnMap() {
        val view = requireNotNull(findMapView(window.decorView))
        val downTime = SystemClock.uptimeMillis()
        val centerX = view.width.coerceAtLeast(400) / 2f
        val centerY = view.height.coerceAtLeast(400) / 2f
        val pointers = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            },
            MotionEvent.PointerProperties().apply {
                id = 1
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        )
        fun coordinate(x: Float) = MotionEvent.PointerCoords().apply {
            this.x = x
            y = centerY
            pressure = 1f
            size = 1f
        }
        fun event(action: Int, offsetMillis: Long, spread: Float, count: Int) =
            MotionEvent.obtain(
                downTime, downTime + offsetMillis, action, count,
                pointers.take(count).toTypedArray(),
                arrayOf(coordinate(centerX - spread), coordinate(centerX + spread)).take(count).toTypedArray(),
                0, 0, 1f, 1f, 0, 0,
                android.view.InputDevice.SOURCE_TOUCHSCREEN, 0
            )
        val events = listOf(
            event(MotionEvent.ACTION_DOWN, 0L, 24f, 1),
            event(
                MotionEvent.ACTION_POINTER_DOWN or
                    (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                16L, 24f, 2
            ),
            event(MotionEvent.ACTION_MOVE, 32L, 82f, 2),
            event(
                MotionEvent.ACTION_POINTER_UP or
                    (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                48L, 82f, 2
            ),
            event(MotionEvent.ACTION_UP, 64L, 82f, 1)
        )
        try {
            events.forEach { view.dispatchTouchEvent(it) }
        } finally {
            events.forEach(MotionEvent::recycle)
        }
    }

    private fun dispatchSinglePointerGesture(deltaX: Float) {
        val view = requireNotNull(findMapView(window.decorView))
        val downTime = SystemClock.uptimeMillis()
        val startX = view.width.coerceAtLeast(400) / 2f
        val startY = view.height.coerceAtLeast(400) / 2f
        val down = MotionEvent.obtain(
            downTime, downTime, MotionEvent.ACTION_DOWN,
            startX, startY, 0
        )
        val move = MotionEvent.obtain(
            downTime, downTime + 16L, MotionEvent.ACTION_MOVE,
            startX + deltaX, startY, 0
        )
        val up = MotionEvent.obtain(
            downTime, downTime + 32L, MotionEvent.ACTION_UP,
            startX + deltaX, startY, 0
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

    fun hasMapView(): Boolean = findMapView(window.decorView) != null

    fun inspectMap(callback: (MapLibreMap) -> Unit) {
        requireNotNull(findMapView(window.decorView)).getMapAsync(callback)
    }

    fun showCountryPosition(latitude: Double, longitude: Double) {
        mapState.value = mapState.value.copy(
            stops = emptyList(), routes = emptyList(), activeStopId = null,
            followRider = true, perspectiveCameraEnabled = true,
            rider = NavigationMapFix(
                point = NavigationMapPoint(latitude, longitude), bearingDegrees = 0f,
                accuracyMeters = 5f, speedMetersPerSecond = 0f,
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            )
        )
    }

    fun mapViewIdentity(): Int =
        System.identityHashCode(requireNotNull(findMapView(window.decorView)))

    fun mapViewUsesActivityContext(): Boolean =
        requireNotNull(findMapView(window.decorView)).context === this

    private fun findMapView(view: View): MapView? {
        if (view is MapView) return view
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
            rider = NavigationMapFix(
                start,
                bearingDegrees = 15f,
                accuracyMeters = 6f
            ),
            stops = listOf(pickup, dropoff),
            routes = listOf(
                NavigationRouteSegment(
                    id = "fixture-leg-1",
                    toStopId = pickup.id,
                    points = listOf(
                        start,
                        NavigationMapPoint(13.7572, 100.5030),
                        pickup.point
                    )
                ),
                NavigationRouteSegment(
                    id = "fixture-leg-2",
                    toStopId = dropoff.id,
                    points = listOf(
                        pickup.point,
                        NavigationMapPoint(13.7598, 100.5062),
                        dropoff.point
                    )
                )
            ),
            activeStopId = pickup.id,
            followRider = true,
            perspectiveCameraEnabled = true
        )
    }
}
