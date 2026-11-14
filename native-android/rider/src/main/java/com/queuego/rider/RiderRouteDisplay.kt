package com.queuego.rider

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

// Compatibility geometry/stop helpers only. Live routing and rendering are
// owned by QueueGoNavigationRouteClient and RiderNavigationMap.
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
