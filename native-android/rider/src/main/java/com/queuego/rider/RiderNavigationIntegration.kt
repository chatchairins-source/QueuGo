package com.queuego.rider

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.queuego.ridernavigationmap.CachingNavigationRouteProvider
import com.queuego.ridernavigationmap.LongdoNavigationRouteClient
import com.queuego.ridernavigationmap.NavigationMapCamera
import com.queuego.ridernavigationmap.NavigationMapFix
import com.queuego.ridernavigationmap.NavigationMapPoint
import com.queuego.ridernavigationmap.NavigationMapStop
import com.queuego.ridernavigationmap.NavigationRouteCoordinator
import com.queuego.ridernavigationmap.NavigationRouteProvider
import com.queuego.ridernavigationmap.NavigationRouteSegment
import com.queuego.ridernavigationmap.NavigationRouteStyle
import com.queuego.ridernavigationmap.NavigationSignalPipeline
import com.queuego.ridernavigationmap.NavigationStopKind
import com.queuego.ridernavigationmap.NavigationViewMode
import com.queuego.ridernavigationmap.NavigationViewModeController
import com.queuego.ridernavigationmap.NavigationViewportInsets
import com.queuego.ridernavigationmap.RiderNavigationMap
import com.queuego.ridernavigationmap.RiderNavigationMapState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel

// Shadow mode computes the candidate renderer fix while preserving the certified display.
// The display gate stays off until CI + real-device route gates pass.
private const val RIDER_NAV_CONFIDENCE_SHADOW_ENABLED = true
private const val RIDER_NAV_DISPLAY_CONFIDENCE_ENABLED = false

private fun riderLongdoNavigationKey(context: Context): String? = runCatching {
    context.packageManager
        .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        .metaData
        ?.getString("longdo.map.key")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}.getOrNull()

private fun validNavigationPoint(lat: Double?, lon: Double?): NavigationMapPoint? {
    if (lat == null || lon == null) return null
    return NavigationMapPoint(lat, lon).takeIf { it.valid }
}

internal fun riderNavigationStops(
    job: RiderJob?,
    marketPickups: List<MarketPickup>,
    bundleOrders: List<RiderBundleOrder>
): List<NavigationMapStop> {
    if (job == null || job.status.lowercase() in setOf("completed", "cancelled", "no_rider_available")) {
        return emptyList()
    }

    val stops = mutableListOf<NavigationMapStop>()
    var blockedByUnknownPendingStop = false

    fun add(
        id: String,
        sequence: Int,
        kind: NavigationStopKind,
        lat: Double?,
        lon: Double?,
        completed: Boolean,
        label: String
    ) {
        if (blockedByUnknownPendingStop) return
        val point = validNavigationPoint(lat, lon)
        if (point == null) {
            if (!completed) blockedByUnknownPendingStop = true
            return
        }
        stops += NavigationMapStop(
            id = id,
            sequence = sequence,
            kind = kind,
            point = point,
            completed = completed,
            label = label
        )
    }

    if (bundleOrders.size > 1 && job.marketOrderId == null) {
        val usable = bundleOrders.filter {
            it.job.status.lowercase() !in setOf("cancelled", "no_rider_available")
        }
        val pickups = usable.sortedWith(
            compareBy<RiderBundleOrder> { it.bundleSequence }.thenBy { it.job.id }
        )
        val drops = usable.sortedWith(
            compareBy<RiderBundleOrder> { it.dropRank }.thenBy { it.job.id }
        )
        pickups.forEachIndexed { index, order ->
            val completed = order.job.isDelivering || order.job.status.lowercase() == "completed"
            add(
                id = "pickup:" + order.job.id,
                sequence = index + 1,
                kind = NavigationStopKind.PICKUP,
                lat = order.job.pickupLat,
                lon = order.job.pickupLng,
                completed = completed,
                label = order.shopName
            )
        }
        val offset = pickups.size
        drops.forEachIndexed { index, order ->
            add(
                id = "drop:" + order.job.id,
                sequence = offset + index + 1,
                kind = NavigationStopKind.DROPOFF,
                lat = order.job.deliveryLat,
                lon = order.job.deliveryLng,
                completed = order.job.status.lowercase() == "completed",
                label = "ลูกค้า " + order.job.numberLabel
            )
        }
    } else if (job.marketOrderId != null) {
        marketPickups.forEachIndexed { index, pickup ->
            add(
                id = "pickup:" + pickup.pickupId,
                sequence = index + 1,
                kind = NavigationStopKind.PICKUP,
                lat = pickup.latitude,
                lon = pickup.longitude,
                completed = pickup.done,
                label = pickup.shopName
            )
        }
        add(
            id = "drop:" + job.id,
            sequence = marketPickups.size + 1,
            kind = NavigationStopKind.DROPOFF,
            lat = job.deliveryLat,
            lon = job.deliveryLng,
            completed = job.status.lowercase() == "completed",
            label = "ลูกค้า " + job.numberLabel
        )
    } else {
        add(
            id = "pickup:" + job.id,
            sequence = 1,
            kind = NavigationStopKind.PICKUP,
            lat = job.pickupLat,
            lon = job.pickupLng,
            completed = job.isDelivering,
            label = job.pickupAddress.orEmpty()
        )
        add(
            id = "drop:" + job.id,
            sequence = 2,
            kind = NavigationStopKind.DROPOFF,
            lat = job.deliveryLat,
            lon = job.deliveryLng,
            completed = job.status.lowercase() == "completed",
            label = job.deliveryAddress.orEmpty()
        )
    }
    return stops
}

private suspend fun buildFutureRoutes(
    routeProvider: NavigationRouteProvider,
    stops: List<NavigationMapStop>
): List<NavigationRouteSegment> {
    val remaining = stops
        .filter { !it.completed && it.point.valid }
        .sortedBy { it.sequence }
    if (remaining.size < 2) return emptyList()

    val routes = mutableListOf<NavigationRouteSegment>()
    for ((from, to) in remaining.zipWithNext()) {
        try {
            val leg = routeProvider.route(
                from = from.point,
                stop = to,
                segmentId = "future:" + from.id + ">" + to.id
            )
            routes += leg.segment.copy(style = NavigationRouteStyle.FUTURE)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Never fabricate a straight-line shortcut past a route service failure.
            break
        }
    }
    return routes
}

@Composable
internal fun QueueGoRiderNavigationMap(
    modifier: Modifier = Modifier,
    stateKey: String,
    job: RiderJob?,
    marketPickups: List<MarketPickup>,
    bundleOrders: List<RiderBundleOrder> = emptyList(),
    recenterSignal: Int,
    overviewSignal: Int = 0,
    viewportInsets: NavigationViewportInsets = NavigationViewportInsets(),
    onMapReady: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val sessionStore = remember(context) { SessionStore(context) }
    val initialCamera = remember(stateKey) {
        sessionStore.loadMapCamera(stateKey)?.let { saved ->
            NavigationMapCamera(
                point = NavigationMapPoint(saved[1], saved[0]),
                zoom = saved[2].toInt().coerceIn(4, 20)
            )
        }
    }

    val viewModeController = remember(stateKey) { NavigationViewModeController() }
    var viewModeState by remember(stateKey) {
        mutableStateOf(viewModeController.state())
    }
    val followRider = viewModeState.mode == NavigationViewMode.FOLLOW

    val stops = remember(job, marketPickups, bundleOrders) {
        riderNavigationStops(job, marketPickups, bundleOrders)
    }
    val navigationActive = stops.any { !it.completed && it.point.valid }
    val riderFix by RiderNavigationLocationStore.fix.collectAsStateWithLifecycle()

    DisposableEffect(lifecycle, navigationActive, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && navigationActive) {
                // Android 14+ requires a location foreground service to be created while
                // the app still has a visible Activity unless background location was granted.
                RiderNavigationLocationService.start(context)
            }
        }
        lifecycle.addObserver(observer)

        if (!navigationActive) {
            RiderNavigationLocationService.stop(context)
        } else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            RiderNavigationLocationService.start(context)
        }

        onDispose {
            // Deliberately do not stop an active navigation service when Compose unmounts
            // because a tab switch, screen-off or another app must not cut the GPS stream.
            lifecycle.removeObserver(observer)
        }
    }
    val activeStopId = stops
        .filter { !it.completed }
        .minByOrNull { it.sequence }
        ?.id

    LaunchedEffect(activeStopId) {
        viewModeState = viewModeController.onActiveStopChanged()
    }

    val routeKey = remember(context.packageName) { riderLongdoNavigationKey(context) }
    val routeProvider = remember(routeKey, stateKey) {
        routeKey?.let { key ->
            CachingNavigationRouteProvider(
                delegate = LongdoNavigationRouteClient(key)
            )
        }
    }
    val coordinator = remember(stateKey, routeProvider) {
        routeProvider?.let { NavigationRouteCoordinator(it) }
    }
    val displayPipeline = remember(stateKey) { NavigationSignalPipeline() }

    var futureRoutes by remember(stateKey) { mutableStateOf<List<NavigationRouteSegment>>(emptyList()) }
    val stopTopology = remember(stops) {
        stops.joinToString("|") {
            listOf(
                it.id,
                it.sequence.toString(),
                it.completed.toString(),
                it.point.latitude.toString(),
                it.point.longitude.toString()
            ).joinToString(":")
        }
    }

    LaunchedEffect(routeProvider, stopTopology) {
        displayPipeline.reset()
        futureRoutes = if (routeProvider == null) emptyList()
        else buildFutureRoutes(routeProvider, stops)
    }

    val baseState = RiderNavigationMapState(
        rider = riderFix,
        stops = stops,
        routes = futureRoutes,
        activeStopId = activeStopId,
        followRider = followRider,
        followZoom = 17,
        viewMode = viewModeState.mode,
        viewportInsets = viewportInsets.normalized()
    )
    var renderedState by remember(stateKey) { mutableStateOf(baseState) }
    val stateChannel = remember(stateKey) { Channel<RiderNavigationMapState>(Channel.CONFLATED) }

    LaunchedEffect(baseState) {
        stateChannel.trySend(baseState)
    }
    LaunchedEffect(coordinator, stateChannel, displayPipeline) {
        var lastLoggedShadowSample = 0L

        fun displayState(authoritativeState: RiderNavigationMapState): RiderNavigationMapState {
            if (!RIDER_NAV_CONFIDENCE_SHADOW_ENABLED && !RIDER_NAV_DISPLAY_CONFIDENCE_ENABLED) {
                return authoritativeState
            }
            val candidateDisplayState = displayPipeline.applyToDisplayState(authoritativeState)
            val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (debuggable && RIDER_NAV_CONFIDENCE_SHADOW_ENABLED) {
                val metrics = displayPipeline.metrics()
                if (metrics.sampleCount >= lastLoggedShadowSample + 30L) {
                    lastLoggedShadowSample = metrics.sampleCount
                    Log.d(
                        "QGNavShadow",
                        "samples=" + metrics.sampleCount +
                            " accepted=" + metrics.acceptedCount +
                            " rejected=" + metrics.rejectedCount +
                            " high=" + metrics.highMatchCount +
                            " medium=" + metrics.mediumMatchCount +
                            " low=" + metrics.lowMatchCount +
                            " weak=" + metrics.weakGpsCount +
                            " held=" + metrics.heldPuckCount +
                            " offRoute=" + metrics.offRouteCount +
                            " lastDeltaM=" + metrics.lastCandidateDeltaMeters +
                            " maxDeltaM=" + metrics.maxCandidateDeltaMeters +
                            " lastReject=" + metrics.lastRejectionReason
                    )
                }
            }
            return if (RIDER_NAV_DISPLAY_CONFIDENCE_ENABLED) {
                candidateDisplayState
            } else {
                authoritativeState
            }
        }

        if (coordinator == null) {
            for (next in stateChannel) {
                renderedState = displayState(next)
            }
        } else {
            for (next in stateChannel) {
                // Route progress/reroute stays authoritative on the raw GPS fix.
                val result = coordinator.synchronize(next)
                // Filtering/snapping, when the rollout gate is enabled, is renderer-only.
                renderedState = displayState(result.mapState)
            }
        }
    }

    LaunchedEffect(recenterSignal) {
        if (recenterSignal > 0) {
            viewModeState = viewModeController.onRecenter()
            riderFix?.let { fix ->
                stateChannel.trySend(
                    baseState.copy(
                        rider = fix,
                        followRider = true,
                        viewMode = NavigationViewMode.FOLLOW
                    )
                )
            }
        }
    }

    LaunchedEffect(overviewSignal) {
        if (overviewSignal > 0) {
            viewModeState = viewModeController.onOverview()
            stateChannel.trySend(
                baseState.copy(
                    followRider = false,
                    viewMode = NavigationViewMode.OVERVIEW
                )
            )
        }
    }

    RiderNavigationMap(
        stateKey = stateKey,
        state = renderedState,
        modifier = modifier,
        initialCamera = initialCamera,
        onMapReady = onMapReady,
        onUserGesture = {
            viewModeState = viewModeController.onUserGesture()
        },
        onCameraChanged = { camera ->
            sessionStore.saveMapCamera(
                stateKey,
                doubleArrayOf(
                    camera.point.longitude,
                    camera.point.latitude,
                    camera.zoom.toDouble(),
                    1.0
                )
            )
        }
    )
}
