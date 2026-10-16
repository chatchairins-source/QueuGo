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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.queuego.ridernavigationmap.CachingNavigationRouteProvider
import com.queuego.ridernavigationmap.LongdoNavigationRouteClient
import com.queuego.ridernavigationmap.NavigationMapCamera
import com.queuego.ridernavigationmap.NavigationMapFix
import com.queuego.ridernavigationmap.NavigationGuidanceState
import com.queuego.ridernavigationmap.NavigationMapPoint
import com.queuego.ridernavigationmap.NavigationDemandZone
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Real-device pilot: use the stabilized road-matched renderer in the visible Rider map.
// Shadow diagnostics are disabled so the Rider sees the exact pipeline being evaluated.
private const val RIDER_NAV_CONFIDENCE_SHADOW_ENABLED = false
private const val RIDER_NAV_DISPLAY_CONFIDENCE_ENABLED = true

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
        val dynamicPlan = usable.any { it.pickupStopRank != null || it.dropoffStopRank != null }
        if (dynamicPlan) {
            val rankedStops = RiderRouteBundleState(activeOrders = usable).routeStops()
            if (rankedStops.isEmpty() && usable.any {
                    it.job.status.lowercase() !in setOf("completed", "cancelled")
                }
            ) {
                // Never fall back to a legacy order when the authoritative plan is
                // partial/stale. An empty route is safer than navigating to the wrong stop.
                return emptyList()
            }
            rankedStops.forEach { stop ->
                val order = stop.order
                add(
                    id = (if (stop.pickup) "pickup:" else "drop:") + order.job.id,
                    sequence = stop.rank,
                    kind = if (stop.pickup) NavigationStopKind.PICKUP else NavigationStopKind.DROPOFF,
                    lat = if (stop.pickup) order.job.pickupLat else order.job.deliveryLat,
                    lon = if (stop.pickup) order.job.pickupLng else order.job.deliveryLng,
                    completed = if (stop.pickup) order.job.isDelivering
                        else order.job.status.lowercase() == "completed",
                    label = if (stop.pickup) order.shopName else "ลูกค้า " + order.job.numberLabel
                )
            }
        } else {
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
    demandZones: List<RiderDemandZone> = emptyList(),
    recenterSignal: Int,
    overviewSignal: Int = 0,
    viewportInsets: NavigationViewportInsets = NavigationViewportInsets(),
    onMapReady: (Boolean) -> Unit,
    onGuidance: (NavigationGuidanceState) -> Unit = {}
) {
    val context = LocalContext.current
    val guidanceCallback by rememberUpdatedState(onGuidance)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val sessionStore = remember(context.applicationContext) { SessionStore(context.applicationContext) }
    var initialCamera by remember(stateKey) { mutableStateOf<NavigationMapCamera?>(null) }
    var initialViewMode by remember(stateKey) { mutableStateOf(NavigationViewMode.FOLLOW) }
    var persistenceHydrated by remember(stateKey) { mutableStateOf(false) }
    var pendingCameraPersistence by remember(stateKey) { mutableStateOf<DoubleArray?>(null) }
    var pendingViewModePersistence by remember(stateKey) { mutableStateOf<String?>(null) }

    LaunchedEffect(stateKey) {
        val restored = withContext(Dispatchers.IO) {
            val camera = sessionStore.loadMapCamera(stateKey)?.let { saved ->
                NavigationMapCamera(
                    point = NavigationMapPoint(saved[1], saved[0]),
                    zoom = saved[2].toInt().coerceIn(4, 20),
                    tiltDegrees = saved.getOrNull(4)?.toFloat()?.takeIf { it.isFinite() }
                )
            }
            val mode = when (sessionStore.loadMapViewMode(stateKey)) {
                "FREE_PAN" -> NavigationViewMode.FREE_PAN
                else -> NavigationViewMode.FOLLOW
            }
            camera to mode
        }
        initialCamera = restored.first
        initialViewMode = restored.second
        persistenceHydrated = true
    }

    LaunchedEffect(stateKey, pendingViewModePersistence, persistenceHydrated) {
        val mode = pendingViewModePersistence ?: return@LaunchedEffect
        if (!persistenceHydrated) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            sessionStore.saveMapViewMode(stateKey, mode)
        }
    }

    LaunchedEffect(stateKey, pendingCameraPersistence, persistenceHydrated) {
        val camera = pendingCameraPersistence ?: return@LaunchedEffect
        if (!persistenceHydrated) return@LaunchedEffect
        delay(250L)
        withContext(Dispatchers.IO) {
            sessionStore.saveMapCamera(stateKey, camera)
        }
    }

    if (!persistenceHydrated) {
        Box(modifier) {}
        return
    }
    val viewModeController = remember(stateKey, initialViewMode) {
        NavigationViewModeController(initialViewMode)
    }
    var viewModeState by remember(stateKey) {
        mutableStateOf(viewModeController.state())
    }
    val followRider = viewModeState.mode == NavigationViewMode.FOLLOW

    val stops = remember(job, marketPickups, bundleOrders) {
        riderNavigationStops(job, marketPickups, bundleOrders)
    }
    val navigationDemandZones = remember(demandZones) {
        demandZones.map { zone ->
            NavigationDemandZone(
                id = "demand:" + zone.latitude + ":" + zone.longitude,
                point = NavigationMapPoint(zone.latitude, zone.longitude),
                level = zone.level,
                windowHours = zone.windowHours
            )
        }
    }
    val navigationActive = stops.any { !it.completed && it.point.valid }
    val riderFix by RiderNavigationLocationStore.fix.collectAsStateWithLifecycle()

    LaunchedEffect(navigationActive) {
        if (!navigationActive) {
            // Leaving a job must also leave navigation perspective. Restore the normal
            // top-down FOLLOW camera even if the Rider used overview/free-pan during work.
            viewModeState = viewModeController.onRecenter()
            pendingViewModePersistence = NavigationViewMode.FOLLOW.name
        }
    }

    DisposableEffect(lifecycle, navigationActive, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // The visible Rider map must always own a fresh GPS fix, even before a job
                // is accepted. Otherwise the current-location puck disappears and recenter
                // has no location to follow.
                RiderNavigationLocationService.start(context)
            }
        }
        lifecycle.addObserver(observer)

        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            RiderNavigationLocationService.start(context)
        }

        onDispose {
            lifecycle.removeObserver(observer)
            if (!navigationActive) {
                // No active job: stop foreground GPS only after the Rider map leaves the
                // screen. Active navigation continues through tab/background transitions.
                RiderNavigationLocationService.stop(context)
            }
        }
    }
    val activeStopId = stops
        .filter { !it.completed }
        .minByOrNull { it.sequence }
        ?.id

    LaunchedEffect(activeStopId) {
        val next = viewModeController.onActiveStopChanged()
        viewModeState = next
        if (next.mode != NavigationViewMode.OVERVIEW) {
            pendingViewModePersistence = next.mode.name
        }
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
    val retainedFrame = remember(stateKey, stopTopology) {
        RiderNavigationSessionRegistry.load(stateKey, stopTopology)
    }
    var futureRoutes by remember(stateKey, stopTopology) {
        mutableStateOf(retainedFrame?.futureRoutes.orEmpty())
    }

    LaunchedEffect(routeProvider, stopTopology, navigationActive) {
        displayPipeline.reset()
        if (!navigationActive) {
            futureRoutes = emptyList()
            RiderNavigationSessionRegistry.clear(stateKey)
        } else if (routeProvider != null) {
            val builtRoutes = buildFutureRoutes(routeProvider, stops)
            futureRoutes = builtRoutes
            RiderNavigationSessionRegistry.saveFutureRoutes(
                stateKey = stateKey,
                topologyKey = stopTopology,
                routes = builtRoutes
            )
        }
    }

    val baseState = RiderNavigationMapState(
        rider = riderFix,
        stops = stops,
        routes = futureRoutes,
        demandZones = if (navigationActive) emptyList() else navigationDemandZones,
        activeStopId = activeStopId,
        followRider = followRider,
        followZoom = 17,
        viewMode = viewModeState.mode,
        viewportInsets = viewportInsets.normalized()
    )
    var renderedState by remember(stateKey, stopTopology) {
        mutableStateOf(
            retainedFrame
                ?.renderedRoutes
                ?.takeIf { it.isNotEmpty() }
                ?.let { baseState.copy(routes = it) }
                ?: baseState
        )
    }
    val stateChannel = remember(stateKey, stopTopology) {
        Channel<RiderNavigationMapState>(Channel.CONFLATED)
    }

    LaunchedEffect(baseState) {
        stateChannel.trySend(baseState)
    }
    LaunchedEffect(coordinator, stateChannel, displayPipeline, stopTopology, navigationActive) {
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
                guidanceCallback(NavigationGuidanceState())
                val candidate = displayState(next)
                renderedState = if (
                    navigationActive &&
                    candidate.routes.isEmpty() &&
                    renderedState.routes.isNotEmpty()
                ) {
                    candidate.copy(routes = renderedState.routes)
                } else {
                    candidate
                }
                RiderNavigationSessionRegistry.saveRenderedRoutes(
                    stateKey = stateKey,
                    topologyKey = stopTopology,
                    routes = renderedState.routes
                )
            }
        } else {
            for (next in stateChannel) {
                // Route progress/reroute stays authoritative on the raw GPS fix.
                val result = coordinator.synchronize(next)
                guidanceCallback(result.guidance)
                // Filtering/snapping, when the rollout gate is enabled, is renderer-only.
                val candidate = displayState(result.mapState)
                renderedState = if (
                    result.routeError != null &&
                    renderedState.routes.isNotEmpty()
                ) {
                    candidate.copy(routes = renderedState.routes)
                } else {
                    candidate
                }
                RiderNavigationSessionRegistry.saveRenderedRoutes(
                    stateKey = stateKey,
                    topologyKey = stopTopology,
                    routes = renderedState.routes
                )
            }
        }
    }

    LaunchedEffect(recenterSignal) {
        if (recenterSignal > 0) {
            // A recenter tap is also an explicit request for a fresh device fix. This is
            // important immediately after granting permission or returning to the map.
            RiderNavigationLocationService.start(context)
            viewModeState = viewModeController.onRecenter()
            pendingViewModePersistence = NavigationViewMode.FOLLOW.name
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
            pendingViewModePersistence = NavigationViewMode.FREE_PAN.name
        },
        onCameraChanged = { camera ->
            pendingCameraPersistence = doubleArrayOf(
                camera.point.longitude,
                camera.point.latitude,
                camera.zoom.toDouble(),
                1.0,
                (camera.tiltDegrees ?: 0f).toDouble()
            )
        },
        onDiagnostics = RiderRuntimeDiagnosticsStore::publishMap
    )
}
