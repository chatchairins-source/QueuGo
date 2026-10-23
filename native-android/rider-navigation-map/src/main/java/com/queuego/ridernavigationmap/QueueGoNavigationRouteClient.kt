package com.queuego.ridernavigationmap

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

private const val QUEUEGO_ROUTE_RESPONSE_MAX_BYTES = 4 * 1024 * 1024
private const val QUEUEGO_ROUTE_MAX_POINTS = 12_000
private const val QUEUEGO_ROUTE_MAX_STEPS = 2_048
private const val QUEUEGO_ROUTE_MAX_SPEED_LIMIT_INTERVALS = 4_096
private val QUEUEGO_JSON = "application/json; charset=utf-8".toMediaType()

private fun InputStream.readQueueGoRouteResponseBounded(): String {
    val output = ByteArrayOutputStream(128 * 1024)
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= QUEUEGO_ROUTE_RESPONSE_MAX_BYTES) {
            "QueueGo routing response is too large"
        }
        output.write(buffer, 0, count)
    }
    return output.toString(StandardCharsets.UTF_8.name())
}

/**
 * Sole Rider route provider for QueueGo navigation.
 *
 * The map SDK remains a renderer only. Route selection is requested from QueueGo Map
 * (/map-api/route), while progress/matching/camera continue in QueueGo-owned code.
 * There is deliberately no Longdo/Google/public-routing fallback.
 */
class QueueGoNavigationRouteClient(
    baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()
) : NavigationRouteProvider {
    private val routeUrl = normalizeBaseUrl(baseUrl)

    override suspend fun route(
        from: NavigationMapPoint,
        stop: NavigationMapStop,
        segmentId: String
    ): NavigationRouteLeg {
        require(from.valid && stop.point.valid) { "Invalid navigation coordinates" }
        require(segmentId.isNotBlank()) { "Route segment id is required" }

        val body = JSONObject()
            .put(
                "points",
                JSONArray()
                    .put(JSONObject().put("lat", from.latitude).put("lon", from.longitude))
                    .put(JSONObject().put("lat", stop.point.latitude).put("lon", stop.point.longitude))
            )
            .put("profile", "rider_delivery")
            .put("preference", "fastest")
            .toString()

        val request = Request.Builder()
            .url(routeUrl)
            .post(body.toRequestBody(QUEUEGO_JSON))
            .build()
        return parseQueueGoRoute(executeCancellable(request), stop, segmentId)
    }

    private fun normalizeBaseUrl(raw: String) = run {
        val trimmed = raw.trim().trimEnd('/')
        require(trimmed.isNotEmpty()) { "QueueGo map service URL is required" }
        val endpoint = if (trimmed.endsWith("/map-api")) "$trimmed/route" else "$trimmed/map-api/route"
        val url = endpoint.toHttpUrl()
        require(url.isHttps) { "QueueGo map service must use HTTPS" }
        url
    }

    private suspend fun executeCancellable(request: Request): String =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, error: IOException) {
                        if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!continuation.isActive) return
                            if (!response.isSuccessful) {
                                continuation.resumeWith(
                                    Result.failure(
                                        IllegalStateException("QueueGo routing HTTP " + response.code)
                                    )
                                )
                                return
                            }
                            val responseBody = response.body
                            val contentLength = responseBody?.contentLength() ?: 0L
                            if (contentLength > QUEUEGO_ROUTE_RESPONSE_MAX_BYTES) {
                                continuation.resumeWith(
                                    Result.failure(
                                        IllegalStateException("QueueGo routing response is too large")
                                    )
                                )
                                return
                            }
                            val raw = runCatching {
                                responseBody?.byteStream()
                                    ?.use { it.readQueueGoRouteResponseBounded() }
                                    .orEmpty()
                            }.getOrElse { failure ->
                                continuation.resumeWith(Result.failure(failure))
                                return
                            }
                            if (raw.isBlank()) {
                                continuation.resumeWith(
                                    Result.failure(
                                        IllegalStateException("QueueGo routing response is empty")
                                    )
                                )
                                return
                            }
                            continuation.resumeWith(Result.success(raw))
                        }
                    }
                }
            )
        }

    internal fun parseQueueGoRoute(
        raw: String,
        stop: NavigationMapStop,
        segmentId: String
    ): NavigationRouteLeg {
        val root = JSONObject(raw)
        if (root.optString("code", "Ok") != "Ok") {
            error(root.optString("message", "QueueGo route unavailable"))
        }
        val routes = root.optJSONArray("routes") ?: error("QueueGo route missing")
        val route = routes.optJSONObject(0) ?: error("QueueGo route missing")
        val geometry = route.optJSONObject("geometry") ?: error("QueueGo route geometry missing")
        require(geometry.optString("type") == "LineString") { "QueueGo route geometry must be LineString" }

        val points = mutableListOf<NavigationMapPoint>()
        appendCoordinates(points, geometry.optJSONArray("coordinates"))
        if (points.size < 2) error("QueueGo route geometry is not renderable")

        val instructions = mutableListOf<NavigationRouteInstruction>()
        val legs = route.optJSONArray("legs")
        var sequence = 0
        if (legs != null) {
            for (legIndex in 0 until legs.length()) {
                val steps = legs.optJSONObject(legIndex)?.optJSONArray("steps") ?: continue
                require(sequence + steps.length() <= QUEUEGO_ROUTE_MAX_STEPS) {
                    "QueueGo route has too many steps"
                }
                for (stepIndex in 0 until steps.length()) {
                    val step = steps.optJSONObject(stepIndex) ?: continue
                    instructions += NavigationRouteInstruction(
                        sequence = sequence++,
                        turnCode = step.optInt("vendor_type", 7),
                        roadName = step.optString("name").trim(),
                        sectionDistanceMeters = step.optDouble("distance", 0.0)
                            .takeIf { it.isFinite() && it >= 0.0 } ?: 0.0,
                        sectionDurationSeconds = step.optDouble("duration", 0.0)
                            .takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
                    )
                }
            }
        }

        val distance = route.optDouble("distance", Double.NaN)
            .takeIf { it.isFinite() && it > 0.0 }
            ?: points.zipWithNext().sumOf { (a, b) -> navigationHaversineMeters(a, b) }
        val duration = route.optDouble("duration", Double.NaN)
            .takeIf { it.isFinite() && it >= 0.0 }
            ?: route.optDouble("estimatedDuration", Double.NaN)
                .takeIf { it.isFinite() && it >= 0.0 }

        val speedLimitIntervals = mutableListOf<NavigationSpeedLimitInterval>()
        route.optJSONArray("maxspeed_intervals")?.let { intervals ->
            require(intervals.length() <= QUEUEGO_ROUTE_MAX_SPEED_LIMIT_INTERVALS) {
                "QueueGo route has too many speed-limit intervals"
            }
            for (index in 0 until intervals.length()) {
                val interval = intervals.optJSONObject(index) ?: continue
                if (interval.optString("source") != "osm:maxspeed") continue
                val start = interval.optDouble("start_geometry_m", Double.NaN)
                val end = interval.optDouble("end_geometry_m", Double.NaN)
                val kph = interval.optDouble("maxspeed_kph", Double.NaN)
                val raw = interval.optString("maxspeed_raw").trim()
                if (!start.isFinite() || !end.isFinite() || end <= start || start < 0.0) continue
                if (!kph.isFinite() || kph <= 0.0 || kph > 160.0 || raw.isEmpty()) continue
                val wayId = if (interval.has("osm_way_id") && !interval.isNull("osm_way_id")) {
                    interval.optLong("osm_way_id").takeIf { it > 0L }
                } else null
                speedLimitIntervals += NavigationSpeedLimitInterval(
                    startGeometryMeters = start,
                    endGeometryMeters = end,
                    maxspeedKph = kph,
                    maxspeedRaw = raw,
                    source = "osm:maxspeed",
                    osmWayId = wayId
                )
            }
        }

        return NavigationRouteLeg(
            segment = NavigationRouteSegment(
                id = segmentId,
                toStopId = stop.id,
                points = points
            ),
            distanceMeters = distance,
            durationSeconds = duration,
            instructions = instructions,
            speedLimitIntervals = speedLimitIntervals
        )
    }

    private fun appendCoordinates(output: MutableList<NavigationMapPoint>, coordinates: JSONArray?) {
        if (coordinates == null) return
        for (index in 0 until coordinates.length()) {
            val pair = coordinates.optJSONArray(index) ?: continue
            if (pair.length() < 2) continue
            val point = NavigationMapPoint(
                latitude = pair.optDouble(1, Double.NaN),
                longitude = pair.optDouble(0, Double.NaN)
            )
            if (point.valid && output.lastOrNull() != point) {
                require(output.size < QUEUEGO_ROUTE_MAX_POINTS) {
                    "QueueGo route has too many points"
                }
                output += point
            }
        }
    }
}
