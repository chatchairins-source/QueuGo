package com.queuego.rider

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.longdo.api.Line
import com.longdo.api.type.MapLocation
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.*

internal data class RiderRoutePoint(val latitude: Double, val longitude: Double) {
    val valid get() = latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0
}
internal data class RiderRouteStop(val id: String, val point: RiderRoutePoint)
internal data class RiderDisplayLeg(val id: String, val from: RiderRoutePoint,
    val to: RiderRoutePoint, val first: Boolean)

/** Display the existing authoritative stop order; never choose or advance a work action. */
internal fun riderRouteDisplayStops(job: RiderJob?, pickups: List<MarketPickup>,
    bundle: List<RiderBundleOrder>): List<RiderRouteStop> {
    if (job == null || job.status in setOf("completed", "cancelled", "no_rider_available")) return emptyList()
    val stops = mutableListOf<RiderRouteStop>()
    var blocked = false
    fun add(id: String, lat: Double?, lon: Double?) {
        if (blocked) return
        val point = if (lat != null && lon != null) RiderRoutePoint(lat, lon) else null
        if (point?.valid == true) stops += RiderRouteStop(id, point)
        else blocked = true // Never bridge past an unknown pending stop to a customer.
    }
    if (bundle.size > 1 && job.marketOrderId == null) {
        val active = bundle.filter { it.job.status !in setOf("cancelled", "completed", "no_rider_available") }
        active.sortedWith(compareBy<RiderBundleOrder> { it.bundleSequence }.thenBy { it.job.id })
            .filter { !it.job.isDelivering }.forEach {
                // Arrival hides the pin, but does not skip the still-pending pickup/proof.
                add("pickup:" + it.job.id, it.job.pickupLat, it.job.pickupLng)
            }
        active.sortedWith(compareBy<RiderBundleOrder> { it.dropRank }.thenBy { it.job.id }).forEach {
            add("drop:" + it.job.id, it.job.deliveryLat, it.job.deliveryLng)
        }
    } else if (job.marketOrderId != null) {
        pickups.filter { !it.done }.forEach { add("pickup:" + it.pickupId, it.latitude, it.longitude) }
        add("drop:" + job.id, job.deliveryLat, job.deliveryLng)
    } else {
        if (!job.isDelivering) add("pickup:" + job.id, job.pickupLat, job.pickupLng)
        add("drop:" + job.id, job.deliveryLat, job.deliveryLng)
    }
    return stops
}

internal fun riderDisplayLegs(origin: RiderRoutePoint?, stops: List<RiderRouteStop>): List<RiderDisplayLeg> =
    buildList {
        if (origin?.valid == true && stops.isNotEmpty()) {
            add(RiderDisplayLeg("active:" + stops.first().id, origin, stops.first().point, true))
        }
        stops.zipWithNext().forEach { (from, to) ->
            add(RiderDisplayLeg(from.id + ">" + to.id, from.point, to.point, false))
        }
    }

/** Reject malformed geometry rather than drawing a fabricated straight-line shortcut. */
internal fun riderRoadGeometry(raw: String): List<List<RiderRoutePoint>> {
    val root = JSONObject(raw)
    require(root.optJSONObject("meta")?.optInt("status", 200)?.let { it < 400 } != false)
    val features = root.optJSONArray("features") ?: throw IOException("Road geometry absent")
    val paths = mutableListOf<List<RiderRoutePoint>>()
    fun path(array: JSONArray) {
        val points = (0 until array.length()).map { index ->
            val coordinate = array.getJSONArray(index)
            require(coordinate.length() >= 2)
            RiderRoutePoint(coordinate.getDouble(1), coordinate.getDouble(0)).also { require(it.valid) }
        }
        if (points.size >= 2) paths += points
    }
    for (index in 0 until features.length()) {
        val geometry = features.getJSONObject(index).optJSONObject("geometry") ?: continue
        val coordinates = geometry.optJSONArray("coordinates") ?: continue
        when (geometry.optString("type")) {
            "LineString" -> path(coordinates)
            "MultiLineString" -> for (part in 0 until coordinates.length()) path(coordinates.getJSONArray(part))
        }
    }
    if (paths.isEmpty()) throw IOException("Road geometry empty")
    return paths
}

private val roadClient = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(12, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build()

internal suspend fun loadRiderRoadGeometry(context: Context, from: RiderRoutePoint,
    to: RiderRoutePoint): List<List<RiderRoutePoint>> {
    require(from.valid && to.valid)
    val key = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        .metaData?.getString("longdo.map.key")?.takeIf { it.isNotBlank() }
        ?: throw IOException("Map key unavailable")
    val url = HttpUrl.Builder().scheme("https").host("api.longdo.com")
        .addPathSegments("RouteService/geojson/route")
        .addQueryParameter("flon", from.longitude.toString()).addQueryParameter("flat", from.latitude.toString())
        .addQueryParameter("tlon", to.longitude.toString()).addQueryParameter("tlat", to.latitude.toString())
        .addQueryParameter("type", "1").addQueryParameter("mode", "d").addQueryParameter("key", key).build()
    val raw = suspendCancellableCoroutine<String> { continuation ->
        val call = roadClient.newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!it.isSuccessful) throw IOException("Road service unavailable")
                        val body = it.body ?: throw IOException("Road response absent")
                        if (body.contentLength() > 2_000_000) throw IOException("Road response too large")
                        val source = body.source()
                        if (source.request(2_000_001L)) throw IOException("Road response too large")
                        val bytes = source.readByteArray()
                        if (continuation.isActive) continuation.resume(bytes.toString(Charsets.UTF_8))
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            }
        })
    }
    return withContext(Dispatchers.Default) { riderRoadGeometry(raw) }
}

internal fun riderRouteLine(points: List<RiderRoutePoint>, first: Boolean, density: Float): Line =
    Line(points.map { MapLocation(it.longitude, it.latitude) }.toTypedArray()).apply {
        if (first) setColor(163, 0, 31, 255) else setColor(230, 0, 45, 76)
        setWidth(((if (first) 4f else 3f) * density).roundToInt().coerceAtLeast(2))
        setWeight(if (first) 0 else 1)
    }

private fun riderRouteDistance(a: RiderRoutePoint, b: RiderRoutePoint): Double {
    val lat = Math.toRadians(b.latitude - a.latitude)
    val lon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(lat/2).pow(2) + cos(Math.toRadians(a.latitude)) *
        cos(Math.toRadians(b.latitude)) * sin(lon/2).pow(2)
    return 12_742_000 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/** Road lines only. No navigation state, guidance, ETA, voice or camera ownership. */
internal class RiderRoadRouteDisplay {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var request: Job? = null
    private var map: com.longdo.api.Map? = null
    private var stops: List<RiderRouteStop> = emptyList()
    private var origin: RiderRoutePoint? = null
    private var startedAt = 0L
    private var generation = 0
    private var disposed = false
    private val lines = mutableMapOf<String, List<Line>>()
    private val cache = linkedMapOf<Pair<RiderRoutePoint, RiderRoutePoint>, Pair<Long, List<List<RiderRoutePoint>>>>()

    fun pause() { request?.cancel(); request = null; generation++ }
    fun dispose() {
        disposed = true
        pause()
        map?.let { active -> lines.values.flatten().forEach { runCatching { active.removeLine(it) } } }
        lines.clear(); cache.clear(); map = null; scope.cancel()
    }

    fun update(context: Context, activeMap: com.longdo.api.Map, nextOrigin: RiderRoutePoint?,
        nextStops: List<RiderRouteStop>, foreground: Boolean) {
        if (disposed || !foreground) return
        val now = android.os.SystemClock.elapsedRealtime()
        val stopsChanged = stops != nextStops
        val changed = map !== activeMap || stopsChanged || (origin == null) != (nextOrigin == null)
        val moved = nextOrigin != null && origin?.let { riderRouteDistance(it, nextOrigin) >= 30.0 } != false
        val legs = riderDisplayLegs(nextOrigin, nextStops)
        val complete = legs.filter { riderRouteDistance(it.from, it.to) >= 1.0 }.all { it.id in lines }
        if (!changed && request?.isActive == true && (!moved || now - startedAt < 15_000)) return
        if (!changed && !moved && complete && now - startedAt < 120_000) return
        if (!changed && now - startedAt < 15_000) return
        pause()
        if (map !== activeMap) lines.clear()
        else if (stopsChanged) {
            lines.values.flatten().forEach { runCatching { activeMap.removeLine(it) } }
            lines.clear()
        }
        map = activeMap; stops = nextStops; origin = nextOrigin; startedAt = now
        val version = generation
        val desired = legs.map { it.id }.toSet()
        lines.keys.filter { it !in desired }.forEach { id ->
            lines.remove(id)?.forEach { runCatching { activeMap.removeLine(it) } }
        }
        if (legs.isEmpty()) return
        request = scope.launch {
            for (leg in legs) {
                try {
                    if (riderRouteDistance(leg.from, leg.to) < 1.0) continue
                    val key = leg.from to leg.to
                    val stored = cache[key]?.takeIf { now - it.first < 120_000 }
                    val geometry = stored?.second ?: loadRiderRoadGeometry(context, leg.from, leg.to).also {
                        cache[key] = now to it
                        while (cache.size > 32) cache.remove(cache.keys.first())
                    }
                    ensureActive()
                    if (disposed || generation != version || map !== activeMap) return@launch
                    val existing = lines[leg.id]
                    if (existing != null && existing.size == geometry.size) {
                        existing.zip(geometry).forEach { (line, points) ->
                            line.setLocation(points.map { MapLocation(it.longitude, it.latitude) }.toTypedArray())
                        }
                    } else {
                        val replacement = geometry.map { riderRouteLine(it, leg.first, context.resources.displayMetrics.density) }
                        replacement.forEach { activeMap.addLine(it) }
                        existing?.forEach { activeMap.removeLine(it) }
                        lines[leg.id] = replacement
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                  catch (_: Exception) { Log.w("QueueGoRouteDisplay", "road_geometry_unavailable") }
            }
        }
    }
}
