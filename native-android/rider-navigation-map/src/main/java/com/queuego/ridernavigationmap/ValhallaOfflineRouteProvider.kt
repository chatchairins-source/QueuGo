package com.queuego.ridernavigationmap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** In-process routing. No HTTP client, cached online leg or straight-line fallback. */
class ValhallaOfflineRouteProvider(
    private val executeRoute: (String) -> String
) : NavigationRouteProvider {
    override suspend fun route(
        from: NavigationMapPoint,
        stop: NavigationMapStop,
        segmentId: String
    ): NavigationRouteLeg = withContext(Dispatchers.IO) {
        require(from.valid && stop.point.valid && segmentId.isNotBlank()) {
            "Invalid offline routing request"
        }
        val locations = JSONArray().put(location(from)).put(location(stop.point))
        val request = JSONObject()
            .put("locations", locations)
            .put("costing", "motorcycle")
            .put("units", "kilometers")
            .put("language", "en-US")
            .put("shape_format", "polyline6")
            .put("directions_type", "instructions")
            .put("costing_options", JSONObject().put("motorcycle", JSONObject()
                .put("use_highways", 0.0).put("use_tolls", 0.0)))
        parseValhallaOfflineRoute(executeRoute(request.toString()), from, stop, segmentId)
    }

    private fun location(point: NavigationMapPoint) = JSONObject()
        .put("lat", point.latitude).put("lon", point.longitude)
        .put("type", "break").put("search_cutoff", 250)
}

internal fun parseValhallaOfflineRoute(
    raw: String,
    from: NavigationMapPoint,
    stop: NavigationMapStop,
    segmentId: String
): NavigationRouteLeg {
    require(raw.length <= 8 * 1024 * 1024) { "Offline routing response is too large" }
    val root = JSONObject(raw)
    val trip = root.optJSONObject("trip") ?: error("Offline route unavailable")
    require(trip.getInt("status") == 0 && trip.getString("units") == "kilometers") {
        "Offline routing failed or returned unexpected distance units"
    }
    val legs = trip.getJSONArray("legs")
    require(legs.length() == 1) { "Offline routing must return exactly one delivery leg" }
    val leg = legs.getJSONObject(0)
    val points = decodeValhallaPolyline6(leg.getString("shape"))
    val maneuvers = leg.getJSONArray("maneuvers")
    require(maneuvers.length() in 2..2048) { "Offline turn instructions are missing or too large" }
    val steps = JSONArray()
    val texts = mutableListOf<String>()
    var previousBegin = -1
    for (index in 0 until maneuvers.length()) {
        val maneuver = maneuvers.getJSONObject(index)
        val begin = maneuver.getInt("begin_shape_index")
        val end = maneuver.getInt("end_shape_index")
        require(begin >= previousBegin && begin in points.indices && end in begin..points.lastIndex) {
            "Offline maneuver does not match route geometry"
        }
        previousBegin = begin
        val length = maneuver.getDouble("length") * 1000.0
        val time = maneuver.getDouble("time")
        require(length.isFinite() && length >= 0 && time.isFinite() && time >= 0) {
            "Offline maneuver has invalid metrics"
        }
        val text = maneuver.getString("instruction").trim()
        require(text.length in 1..2000) { "Offline maneuver instruction is invalid" }
        texts += text
        // Preserve the full engine instruction (including roundabout exit numbers)
        // separately from QueueGo's existing compact icon codes.
        steps.put(JSONObject().put("vendor_type", valhallaQueueGoTurnCode(maneuver.getInt("type")))
            .put("name", maneuver.optJSONArray("street_names")?.optString(0).orEmpty())
            .put("distance", length).put("duration", time))
    }
    require(maneuvers.getJSONObject(0).getInt("begin_shape_index") == 0 &&
        maneuvers.getJSONObject(maneuvers.length() - 1).getInt("end_shape_index") == points.lastIndex) {
        "Offline maneuvers do not cover the route"
    }
    val summary = leg.getJSONObject("summary")
    val distance = summary.getDouble("length") * 1000.0
    val duration = summary.getDouble("time")
    require(distance.isFinite() && distance > 0 && duration.isFinite() && duration >= 0) {
        "Offline route summary is invalid"
    }
    val coordinates = JSONArray()
    points.forEach { coordinates.put(JSONArray().put(it.longitude).put(it.latitude)) }
    val normalized = JSONObject().put("code", "Ok").put("routes", JSONArray().put(JSONObject()
        .put("distance", distance).put("duration", duration)
        .put("geometry", JSONObject().put("type", "LineString").put("coordinates", coordinates))
        .put("legs", JSONArray().put(JSONObject().put("steps", steps)))))
    // Reuse the same endpoint, geometry, metric and instruction boundary checks as online routes.
    val parsed = QueueGoNavigationRouteClient("https://offline.invalid")
        .parseQueueGoRoute(normalized.toString(), stop, segmentId, from)
    return parsed.copy(instructions = parsed.instructions.mapIndexed { index, instruction ->
        instruction.copy(instructionText = texts[index])
    })
}

internal fun valhallaQueueGoTurnCode(type: Int): Int = when (type) {
    1, 2, 3, 7, 8, 17, 22, 23 -> 5
    4, 5, 6 -> 9
    9, 18, 20, 24 -> 3
    10, 11 -> 1
    12, 13 -> 4
    14, 15 -> 0
    16, 19, 21, 25 -> 2
    28 -> 11
    else -> 7 // Full narrative remains visible for roundabouts and other maneuvers.
}

internal fun decodeValhallaPolyline6(encoded: String): List<NavigationMapPoint> {
    require(encoded.length in 2..2_000_000) { "Invalid offline route shape size" }
    var index = 0
    var latitude = 0L
    var longitude = 0L
    fun component(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            require(index < encoded.length && shift <= 30) { "Truncated or oversized offline polyline" }
            val value = encoded[index++].code - 63
            require(value in 0..63) { "Invalid offline polyline character" }
            result = result or ((value and 31).toLong() shl shift)
            if (value < 32) break
            shift += 5
        }
        return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
    }
    return buildList {
        while (index < encoded.length) {
            latitude += component()
            longitude += component()
            val point = NavigationMapPoint(latitude / 1e6, longitude / 1e6)
            require(point.valid && size < 12_000) { "Invalid or oversized offline route geometry" }
            add(point)
        }
        require(size >= 2) { "Offline route has no road geometry" }
    }
}
