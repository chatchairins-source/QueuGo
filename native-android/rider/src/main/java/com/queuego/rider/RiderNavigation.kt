package com.queuego.rider

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal data class RiderNavPoint(val latitude: Double, val longitude: Double) {
    val valid: Boolean
        get() = latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}

internal data class RiderNavigationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val bearingDegrees: Float? = null,
    val speedMetersPerSecond: Float? = null,
    val elapsedRealtimeNanos: Long = 0L
) {
    val point: RiderNavPoint
        get() = RiderNavPoint(latitude, longitude)
}

internal data class RiderNavigationTarget(
    val key: String,
    val label: String,
    val destinationKind: String,
    val latitude: Double,
    val longitude: Double
) {
    val point: RiderNavPoint
        get() = RiderNavPoint(latitude, longitude)
}

internal data class RiderRouteInstruction(
    val sequence: Int,
    val turnCode: Int,
    val roadName: String,
    val sectionDistanceMeters: Double,
    val sectionIntervalSeconds: Double
)

internal data class RiderRoutePlan(
    val targetKey: String,
    val parts: List<List<RiderNavPoint>>,
    val instructions: List<RiderRouteInstruction>,
    val distanceMeters: Double,
    val intervalSeconds: Double,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    val geometryDistanceMeters: Double by lazy {
        parts.sumOf { part ->
            part.zipWithNext().sumOf { (a, b) -> riderHaversineMeters(a, b) }
        }
    }
}

internal data class RiderRouteMatch(
    val snapped: RiderNavPoint,
    val offRouteMeters: Double,
    val progressMeters: Double,
    val geometryDistanceMeters: Double,
    val segmentBearingDegrees: Float?
)

internal data class RiderNavigationState(
    val active: Boolean = false,
    val targetKey: String? = null,
    val targetLabel: String = "",
    val destinationKind: String = "",
    val loading: Boolean = false,
    val routeError: String? = null,
    val plan: RiderRoutePlan? = null,
    val fix: RiderNavigationFix? = null,
    val snappedPoint: RiderNavPoint? = null,
    val headingDegrees: Float? = null,
    val gpsAccuracyMeters: Float? = null,
    val weakGps: Boolean = false,
    val offRoute: Boolean = false,
    val offRouteMeters: Double? = null,
    val distanceRemainingMeters: Double? = null,
    val etaSeconds: Double? = null,
    val nextInstruction: RiderRouteInstruction? = null,
    val distanceToInstructionMeters: Double? = null,
    val rerouteVersion: Int = 0
) {
    val routeReady: Boolean
        get() = active && plan != null && plan.parts.any { it.size > 1 }
}

private object RiderNavigationProcessStore {
    private var cachedPlan: RiderRoutePlan? = null
    private var rerouteVersion: Int = 0
    private var lastVoiceToken: String? = null

    @Synchronized
    fun restore(targetKey: String): Pair<RiderRoutePlan?, Int> =
        (cachedPlan?.takeIf { it.targetKey == targetKey }) to rerouteVersion

    @Synchronized
    fun save(plan: RiderRoutePlan, version: Int) {
        cachedPlan = plan
        rerouteVersion = version
    }

    @Synchronized
    fun consumeVoiceToken(token: String): Boolean {
        if (lastVoiceToken == token) return false
        lastVoiceToken = token
        return true
    }

    @Synchronized
    fun clearTarget(targetKey: String?) {
        if (targetKey == null || cachedPlan?.targetKey != targetKey) {
            cachedPlan = null
            rerouteVersion = 0
        }
    }
}

internal class RiderLongdoRouteClient(
    private val apiKey: String = "347649ca13f49db0d8a599ae35f8de2b",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    suspend fun route(from: RiderNavPoint, target: RiderNavigationTarget): RiderRoutePlan =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            require(from.valid && target.point.valid) { "พิกัดเส้นทางไม่ถูกต้อง" }
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("api.longdo.com")
                .addPathSegments("RouteService/geojson/route")
                .addQueryParameter("flon", from.longitude.toString())
                .addQueryParameter("flat", from.latitude.toString())
                .addQueryParameter("tlon", target.longitude.toString())
                .addQueryParameter("tlat", target.latitude.toString())
                .addQueryParameter("type", "1")
                .addQueryParameter("locale", "th")
                .addQueryParameter("restrict", "1")
                .addQueryParameter("key", apiKey)
                .build()
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Longdo routing HTTP " + response.code)
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) error("Longdo routing response ว่าง")
                parseGeoJson(body, target)
            }
        }

    internal fun parseGeoJson(raw: String, target: RiderNavigationTarget): RiderRoutePlan {
        val root = JSONObject(raw)
        val features = root.optJSONArray("features") ?: error("Longdo ไม่ส่งเส้นทาง")
        val parts = mutableListOf<List<RiderNavPoint>>()
        val instructions = mutableListOf<RiderRouteInstruction>()
        var totalDistance = 0.0
        var totalInterval = 0.0

        for (index in 0 until features.length()) {
            val feature = features.optJSONObject(index) ?: continue
            val properties = feature.optJSONObject("properties") ?: JSONObject()
            val distance = properties.optDouble("distance", 0.0).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
            val interval = properties.optDouble("interval", 0.0).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
            totalDistance += distance
            totalInterval += interval
            instructions += RiderRouteInstruction(
                sequence = index,
                turnCode = properties.optInt("turn", 4),
                roadName = properties.optString("name").trim(),
                sectionDistanceMeters = distance,
                sectionIntervalSeconds = interval
            )
            val geometry = feature.optJSONObject("geometry") ?: continue
            when (geometry.optString("type")) {
                "LineString" -> parseCoordinatePart(geometry.optJSONArray("coordinates"))?.let(parts::add)
                "MultiLineString" -> {
                    val groups = geometry.optJSONArray("coordinates") ?: continue
                    for (groupIndex in 0 until groups.length()) {
                        parseCoordinatePart(groups.optJSONArray(groupIndex))?.let(parts::add)
                    }
                }
            }
        }

        val cleanedParts = parts.filter { it.size > 1 }
        if (cleanedParts.isEmpty()) error("Longdo ไม่พบเส้นทางถนน")
        val geometryDistance = cleanedParts.sumOf { part ->
            part.zipWithNext().sumOf { (a, b) -> riderHaversineMeters(a, b) }
        }
        if (totalDistance <= 0.0) totalDistance = geometryDistance
        if (totalInterval <= 0.0) totalInterval = max(60.0, totalDistance / 7.0)

        return RiderRoutePlan(
            targetKey = target.key,
            parts = cleanedParts,
            instructions = instructions,
            distanceMeters = totalDistance,
            intervalSeconds = totalInterval
        )
    }

    private fun parseCoordinatePart(array: JSONArray?): List<RiderNavPoint>? {
        if (array == null) return null
        val result = ArrayList<RiderNavPoint>(array.length())
        for (index in 0 until array.length()) {
            val pair = array.optJSONArray(index) ?: continue
            if (pair.length() < 2) continue
            val point = RiderNavPoint(
                latitude = pair.optDouble(1, Double.NaN),
                longitude = pair.optDouble(0, Double.NaN)
            )
            if (point.valid && (result.lastOrNull() != point)) result += point
        }
        return result.takeIf { it.size > 1 }
    }
}

internal class RiderNavigationController(
    private val routeClient: RiderLongdoRouteClient = RiderLongdoRouteClient(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(RiderNavigationState())
    val state: StateFlow<RiderNavigationState> = _state.asStateFlow()

    private var target: RiderNavigationTarget? = null
    private var routeJob: Job? = null
    private var lastFix: RiderNavigationFix? = null
    private var previousFix: RiderNavigationFix? = null
    private var offRouteSamples = 0
    private var lastRouteRequestAt = 0L

    fun setTarget(next: RiderNavigationTarget?) {
        if (next?.key == target?.key) return
        routeJob?.cancel()
        routeJob = null
        target = next
        offRouteSamples = 0
        if (next == null) {
            RiderNavigationProcessStore.clearTarget(null)
            _state.value = RiderNavigationState()
            return
        }
        val restored = RiderNavigationProcessStore.restore(next.key)
        _state.value = RiderNavigationState(
            active = true,
            targetKey = next.key,
            targetLabel = next.label,
            destinationKind = next.destinationKind,
            plan = restored.first,
            rerouteVersion = restored.second,
            fix = lastFix
        )
        lastFix?.let { updateProgress(it) }
        if (restored.first == null) lastFix?.let { requestRoute(it, false) }
    }

    fun onLocationFix(fix: RiderNavigationFix) {
        if (!fix.point.valid) return
        previousFix = lastFix
        lastFix = fix
        if (target == null) {
            _state.value = _state.value.copy(fix = fix)
            return
        }
        updateProgress(fix)
        if (_state.value.plan == null) requestRoute(fix, false)
    }

    private fun updateProgress(fix: RiderNavigationFix) {
        val currentTarget = target ?: return
        val plan = _state.value.plan
        val accuracy = fix.accuracyMeters?.takeIf { it.isFinite() && it >= 0f }
        val weakGps = accuracy != null && accuracy > 75f
        val heading = fix.bearingDegrees
            ?.takeIf { it.isFinite() }
            ?.let(::normalizeBearing)
            ?: derivedBearing(previousFix, fix)

        if (plan == null) {
            _state.value = _state.value.copy(
                active = true,
                targetKey = currentTarget.key,
                targetLabel = currentTarget.label,
                destinationKind = currentTarget.destinationKind,
                fix = fix,
                headingDegrees = heading,
                gpsAccuracyMeters = accuracy,
                weakGps = weakGps
            )
            return
        }

        val match = riderMatchRoute(plan, fix.point)
        if (match == null) {
            _state.value = _state.value.copy(fix = fix, weakGps = weakGps, gpsAccuracyMeters = accuracy)
            return
        }
        val geometryLength = match.geometryDistanceMeters.coerceAtLeast(1.0)
        val progressRatio = (match.progressMeters / geometryLength).coerceIn(0.0, 1.0)
        val remaining = (plan.distanceMeters * (1.0 - progressRatio)).coerceAtLeast(0.0)
        val eta = (plan.intervalSeconds * (remaining / plan.distanceMeters.coerceAtLeast(1.0))).coerceAtLeast(0.0)
        val threshold = max(55.0, (accuracy?.toDouble() ?: 15.0) * 1.8)
        val offRoute = !weakGps && match.offRouteMeters > threshold
        if (offRoute) offRouteSamples++ else offRouteSamples = 0
        val next = riderNextInstruction(plan, plan.distanceMeters - remaining)

        _state.value = _state.value.copy(
            active = true,
            targetKey = currentTarget.key,
            targetLabel = currentTarget.label,
            destinationKind = currentTarget.destinationKind,
            fix = fix,
            snappedPoint = match.snapped,
            headingDegrees = heading ?: match.segmentBearingDegrees,
            gpsAccuracyMeters = accuracy,
            weakGps = weakGps,
            offRoute = offRoute,
            offRouteMeters = match.offRouteMeters,
            distanceRemainingMeters = remaining,
            etaSeconds = eta,
            nextInstruction = next.first,
            distanceToInstructionMeters = next.second
        )

        if (offRouteSamples >= 2 && nowMillis() - lastRouteRequestAt >= 8_000L) {
            offRouteSamples = 0
            requestRoute(fix, true)
        }
    }

    private fun requestRoute(fix: RiderNavigationFix, reroute: Boolean) {
        val currentTarget = target ?: return
        if (routeJob?.isActive == true) return
        val now = nowMillis()
        if (!reroute && now - lastRouteRequestAt < 4_000L) return
        lastRouteRequestAt = now
        _state.value = _state.value.copy(loading = true, routeError = null)
        routeJob = scope.launch {
            runCatching { routeClient.route(fix.point, currentTarget) }
                .onSuccess { plan ->
                    if (target?.key != currentTarget.key) return@onSuccess
                    val version = if (reroute || _state.value.plan != null) _state.value.rerouteVersion + 1 else _state.value.rerouteVersion
                    RiderNavigationProcessStore.save(plan, version)
                    _state.value = _state.value.copy(
                        plan = plan,
                        loading = false,
                        routeError = null,
                        rerouteVersion = version
                    )
                    lastFix?.let { updateProgress(it) }
                }
                .onFailure { error ->
                    if (target?.key != currentTarget.key) return@onFailure
                    _state.value = _state.value.copy(
                        loading = false,
                        routeError = error.message ?: "คำนวณเส้นทางไม่สำเร็จ"
                    )
                }
        }
    }

    fun retry() {
        lastFix?.let { requestRoute(it, _state.value.plan != null) }
    }

    fun close() {
        routeJob?.cancel()
        scope.cancel()
    }
}

internal fun riderNavigationTarget(job: RiderJob, pickups: List<MarketPickup>): RiderNavigationTarget? {
    if (job.marketOrderId != null && !job.isDelivering) {
        val next = pickups.firstOrNull { !it.done && it.latitude != null && it.longitude != null }
        if (next != null) {
            return RiderNavigationTarget(
                key = job.id + ":market:" + next.pickupId,
                label = "ร้าน " + next.shopName,
                destinationKind = "pickup",
                latitude = requireNotNull(next.latitude),
                longitude = requireNotNull(next.longitude)
            ).takeIf { it.point.valid }
        }
    }
    val point = job.navigationTarget ?: return null
    val delivering = job.isDelivering
    return RiderNavigationTarget(
        key = job.id + if (delivering) ":customer" else ":shop",
        label = if (delivering) "ลูกค้า" else "ร้านค้า",
        destinationKind = if (delivering) "delivery" else "pickup",
        latitude = point.first,
        longitude = point.second
    ).takeIf { it.point.valid }
}

internal fun riderMatchRoute(plan: RiderRoutePlan, point: RiderNavPoint): RiderRouteMatch? {
    if (!point.valid) return null
    var bestDistance = Double.POSITIVE_INFINITY
    var bestPoint: RiderNavPoint? = null
    var bestProgress = 0.0
    var bestBearing: Float? = null
    var walked = 0.0

    for (part in plan.parts) {
        for (index in 0 until part.lastIndex) {
            val a = part[index]
            val b = part[index + 1]
            val segmentMeters = riderHaversineMeters(a, b)
            if (segmentMeters <= 0.01) continue
            val projection = projectToSegment(point, a, b)
            if (projection.distanceMeters < bestDistance) {
                bestDistance = projection.distanceMeters
                bestPoint = projection.point
                bestProgress = walked + segmentMeters * projection.fraction
                bestBearing = riderBearingDegrees(a, b)
            }
            walked += segmentMeters
        }
    }
    val snapped = bestPoint ?: return null
    return RiderRouteMatch(
        snapped = snapped,
        offRouteMeters = bestDistance,
        progressMeters = bestProgress,
        geometryDistanceMeters = walked,
        segmentBearingDegrees = bestBearing
    )
}

private data class SegmentProjection(
    val point: RiderNavPoint,
    val distanceMeters: Double,
    val fraction: Double
)

private fun projectToSegment(p: RiderNavPoint, a: RiderNavPoint, b: RiderNavPoint): SegmentProjection {
    val referenceLat = Math.toRadians(p.latitude)
    val metersPerDegreeLat = 111_320.0
    val metersPerDegreeLon = 111_320.0 * cos(referenceLat).coerceAtLeast(0.01)
    val ax = (a.longitude - p.longitude) * metersPerDegreeLon
    val ay = (a.latitude - p.latitude) * metersPerDegreeLat
    val bx = (b.longitude - p.longitude) * metersPerDegreeLon
    val by = (b.latitude - p.latitude) * metersPerDegreeLat
    val dx = bx - ax
    val dy = by - ay
    val denom = dx * dx + dy * dy
    val fraction = if (denom <= 0.0001) 0.0 else (-(ax * dx + ay * dy) / denom).coerceIn(0.0, 1.0)
    val x = ax + dx * fraction
    val y = ay + dy * fraction
    val snapped = RiderNavPoint(
        latitude = a.latitude + (b.latitude - a.latitude) * fraction,
        longitude = a.longitude + (b.longitude - a.longitude) * fraction
    )
    return SegmentProjection(snapped, sqrt(x * x + y * y), fraction)
}

internal fun riderNextInstruction(
    plan: RiderRoutePlan,
    traveledMeters: Double
): Pair<RiderRouteInstruction?, Double?> {
    var actionAt = 0.0
    val traveled = traveledMeters.coerceAtLeast(0.0)
    for (index in plan.instructions.indices) {
        val instruction = plan.instructions[index]
        if (index > 0 && riderTurnPhrase(instruction.turnCode) != null && actionAt + 15.0 >= traveled) {
            return instruction to (actionAt - traveled).coerceAtLeast(0.0)
        }
        actionAt += instruction.sectionDistanceMeters
    }
    val arrival = RiderRouteInstruction(
        sequence = plan.instructions.size,
        turnCode = 9,
        roadName = "",
        sectionDistanceMeters = 0.0,
        sectionIntervalSeconds = 0.0
    )
    return arrival to (plan.distanceMeters - traveled).coerceAtLeast(0.0)
}

internal fun riderTurnPhrase(turnCode: Int): String? = when (turnCode) {
    0 -> "เลี้ยวซ้าย"
    1 -> "เลี้ยวขวา"
    2 -> "เบี่ยงซ้าย"
    3 -> "เบี่ยงขวา"
    5 -> "ตรงไป"
    6 -> "ขึ้นทางด่วน"
    9 -> "ถึงจุดหมายแล้ว"
    11 -> "ขึ้นเรือ"
    else -> null
}

internal data class RiderVoiceCue(val token: String, val phrase: String)

internal fun riderVoiceCue(state: RiderNavigationState): RiderVoiceCue? {
    if (!state.active || !state.routeReady || state.targetKey.isNullOrBlank()) return null
    val targetKey = state.targetKey
    val remaining = state.distanceRemainingMeters
    if (remaining != null && remaining <= 28.0) {
        return RiderVoiceCue(
            token = targetKey + ":arrive",
            phrase = "ถึงจุดหมายแล้ว"
        )
    }
    val instruction = state.nextInstruction
    val toInstruction = state.distanceToInstructionMeters
    if (instruction != null && toInstruction != null) {
        val action = riderTurnPhrase(instruction.turnCode)
        if (action != null && instruction.turnCode != 9 && toInstruction <= 320.0) {
            val threshold = when {
                toInstruction <= 40.0 -> 30
                toInstruction <= 120.0 -> 100
                else -> 300
            }
            val road = instruction.roadName.takeIf { it.isNotBlank() }?.let { " เข้าสู่" + it }.orEmpty()
            return RiderVoiceCue(
                token = targetKey + ":" + state.rerouteVersion + ":turn:" + instruction.sequence + ":" + threshold,
                phrase = "อีก " + threshold + " เมตร " + action + road
            )
        }
    }
    if (state.rerouteVersion > 0) {
        return RiderVoiceCue(
            token = targetKey + ":reroute:" + state.rerouteVersion,
            phrase = "คำนวณเส้นทางใหม่แล้ว"
        )
    }
    return RiderVoiceCue(
        token = targetKey + ":start",
        phrase = "เริ่มนำทางไป" + state.targetLabel
    )
}

internal class RiderNavigationSpeaker(context: android.content.Context) : android.speech.tts.TextToSpeech.OnInitListener {
    private val engine = android.speech.tts.TextToSpeech(context.applicationContext, this)
    @Volatile private var ready = false

    override fun onInit(status: Int) {
        ready = status == android.speech.tts.TextToSpeech.SUCCESS
        if (ready) {
            engine.language = Locale("th", "TH")
            engine.setSpeechRate(1.0f)
        }
    }

    fun speakIfNeeded(state: RiderNavigationState) {
        if (!ready) return
        val cue = riderVoiceCue(state) ?: return
        if (!RiderNavigationProcessStore.consumeVoiceToken(cue.token)) return
        engine.speak(
            cue.phrase,
            android.speech.tts.TextToSpeech.QUEUE_FLUSH,
            null,
            "queuego-nav-" + cue.token.hashCode()
        )
    }

    fun close() {
        engine.stop()
        engine.shutdown()
    }
}

internal fun riderHaversineMeters(a: RiderNavPoint, b: RiderNavPoint): Double {
    val radius = 6_371_000.0
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val sinLat = sin(dLat / 2)
    val sinLon = sin(dLon / 2)
    val h = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon
    return 2 * radius * atan2(sqrt(h.coerceIn(0.0, 1.0)), sqrt((1.0 - h).coerceIn(0.0, 1.0)))
}

internal fun riderBearingDegrees(a: RiderNavPoint, b: RiderNavPoint): Float {
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return normalizeBearing(Math.toDegrees(atan2(y, x)).toFloat())
}

private fun derivedBearing(previous: RiderNavigationFix?, current: RiderNavigationFix): Float? {
    if (previous == null) return null
    val from = previous.point
    val to = current.point
    if (riderHaversineMeters(from, to) < 3.0) return null
    return riderBearingDegrees(from, to)
}

private fun normalizeBearing(value: Float): Float {
    var normalized = value % 360f
    if (normalized < 0f) normalized += 360f
    return normalized
}
