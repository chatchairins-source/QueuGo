package com.queuego.ridernavigationmap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class NavigationRouteInstruction(
    val sequence: Int,
    val turnCode: Int,
    val roadName: String,
    val sectionDistanceMeters: Double,
    val sectionDurationSeconds: Double
)

data class NavigationRouteLeg(
    val segment: NavigationRouteSegment,
    val distanceMeters: Double,
    val durationSeconds: Double?,
    val instructions: List<NavigationRouteInstruction> = emptyList()
)

class LongdoNavigationRouteClient(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    suspend fun route(
        from: NavigationMapPoint,
        stop: NavigationMapStop,
        segmentId: String
    ): NavigationRouteLeg = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Longdo routing key is required" }
        require(from.valid && stop.point.valid) { "Invalid navigation coordinates" }
        require(segmentId.isNotBlank()) { "Route segment id is required" }

        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api.longdo.com")
            .addPathSegments("RouteService/geojson/route")
            .addQueryParameter("flon", from.longitude.toString())
            .addQueryParameter("flat", from.latitude.toString())
            .addQueryParameter("tlon", stop.point.longitude.toString())
            .addQueryParameter("tlat", stop.point.latitude.toString())
            .addQueryParameter("type", "1")
            .addQueryParameter("locale", "th")
            .addQueryParameter("restrict", "1")
            .addQueryParameter("key", apiKey)
            .build()

        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) error("Longdo routing HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) error("Longdo routing response is empty")
            parseGeoJson(body, stop, segmentId)
        }
    }

    internal fun parseGeoJson(raw: String, stop: NavigationMapStop, segmentId: String): NavigationRouteLeg {
        val root = JSONObject(raw)
        val status = root.optJSONObject("meta")?.optInt("status", 200) ?: 200
        if (status != 200) error("Longdo routing status $status")

        val features = root.optJSONArray("features") ?: error("Longdo route geometry missing")
        val points = mutableListOf<NavigationMapPoint>()
        val instructions = mutableListOf<NavigationRouteInstruction>()
        var featureDistance = 0.0
        var featureDuration = 0.0

        for (index in 0 until features.length()) {
            val feature = features.optJSONObject(index) ?: continue
            val properties = feature.optJSONObject("properties") ?: JSONObject()
            val sectionDistance = properties.optDouble("distance", 0.0)
                .takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
            val sectionDuration = properties.optDouble("interval", 0.0)
                .takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
            featureDistance += sectionDistance
            featureDuration += sectionDuration
            instructions += NavigationRouteInstruction(
                sequence = index,
                turnCode = properties.optInt("turn", 4),
                roadName = properties.optString("name").trim(),
                sectionDistanceMeters = sectionDistance,
                sectionDurationSeconds = sectionDuration
            )

            val geometry = feature.optJSONObject("geometry") ?: continue
            when (geometry.optString("type")) {
                "LineString" -> appendCoordinates(points, geometry.optJSONArray("coordinates"))
                "MultiLineString" -> {
                    val groups = geometry.optJSONArray("coordinates") ?: continue
                    for (groupIndex in 0 until groups.length()) {
                        appendCoordinates(points, groups.optJSONArray(groupIndex))
                    }
                }
            }
        }

        if (points.size < 2) error("Longdo route geometry is not renderable")

        val summary = root.optJSONObject("data")
        val distance = summary?.optDouble("distance", Double.NaN)
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: featureDistance.takeIf { it > 0.0 }
            ?: points.zipWithNext().sumOf { (a, b) -> navigationHaversineMeters(a, b) }
        val duration = summary?.optDouble("interval", Double.NaN)
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: featureDuration.takeIf { it > 0.0 }

        return NavigationRouteLeg(
            segment = NavigationRouteSegment(
                id = segmentId,
                toStopId = stop.id,
                points = points
            ),
            distanceMeters = distance,
            durationSeconds = duration,
            instructions = instructions
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
            if (point.valid && output.lastOrNull() != point) output += point
        }
    }
}
