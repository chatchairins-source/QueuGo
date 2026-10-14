package com.queuego.ridermaplibreosm

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Provenance is deliberately constrained to sources QueueGo is allowed to use.
 * Competitor/private map geometry is not an accepted source.
 */
enum class QueueGoLocalRoadSource {
    OPENSTREETMAP_VERIFIED,
    PUBLIC_DATA,
    RIDER_GPS_TRACE_VERIFIED,
    MERCHANT_CONFIRMED,
    FIELD_SURVEY
}

enum class QueueGoLocalRoadAccess {
    MOTORCYCLE_AND_CAR,
    MOTORCYCLE_ONLY,
    SERVICE_ACCESS,
    PRIVATE_RESTRICTED
}

enum class QueueGoLocalRoadSurface {
    PAVED,
    UNPAVED,
    UNKNOWN
}

data class QueueGoLocalRoadPoint(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Local-road latitude is invalid"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Local-road longitude is invalid"
        }
    }
}

data class QueueGoLocalRoad(
    val id: String,
    val points: List<QueueGoLocalRoadPoint>,
    val source: QueueGoLocalRoadSource,
    val verified: Boolean,
    val access: QueueGoLocalRoadAccess = QueueGoLocalRoadAccess.MOTORCYCLE_AND_CAR,
    val oneWay: Boolean = false,
    val surface: QueueGoLocalRoadSurface = QueueGoLocalRoadSurface.UNKNOWN,
    val name: String? = null
) {
    init {
        require(id.isNotBlank()) { "Local-road id is required" }
        require(points.size >= 2) { "Local road requires at least two points" }
        require(points.zipWithNext().any { (a, b) -> a != b }) {
            "Local road must contain a non-zero segment"
        }
        require(name == null || name.length <= 160) { "Local-road name is too long" }
    }

    val renderable: Boolean
        get() = verified && access != QueueGoLocalRoadAccess.PRIVATE_RESTRICTED
}

internal fun queueGoVerifiedLocalRoadFeatures(
    roads: List<QueueGoLocalRoad>
): FeatureCollection {
    val features = roads
        .asSequence()
        .filter { it.renderable }
        .distinctBy { it.id }
        .map { road ->
            val geometry = LineString.fromLngLats(
                road.points.map { point ->
                    Point.fromLngLat(point.longitude, point.latitude)
                }
            )
            Feature.fromGeometry(geometry).apply {
                addStringProperty("road_id", road.id)
                addStringProperty("source", road.source.name.lowercase())
                addStringProperty("access", road.access.name.lowercase())
                addStringProperty("surface", road.surface.name.lowercase())
                addBooleanProperty("one_way", road.oneWay)
                road.name?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    addStringProperty("name", it)
                }
            }
        }
        .toList()

    return FeatureCollection.fromFeatures(features)
}

object QueueGoLocalRoadOverlay {
    const val SOURCE_ID = "queuego-local-roads"
    const val CASING_LAYER_ID = "queuego-local-roads-casing"
    const val SURFACE_LAYER_ID = "queuego-local-roads-surface"
    const val MOTORCYCLE_LAYER_ID = "queuego-local-roads-motorcycle-only"

    fun apply(style: Style, roads: List<QueueGoLocalRoad>) {
        val features = queueGoVerifiedLocalRoadFeatures(roads)
        val existing = style.getSource(SOURCE_ID)
        if (existing is GeoJsonSource) {
            existing.setGeoJson(features)
        } else if (existing == null) {
            style.addSource(GeoJsonSource(SOURCE_ID, features))
        } else {
            return
        }

        if (style.getLayer(CASING_LAYER_ID) == null) {
            style.addLayer(
                LineLayer(CASING_LAYER_ID, SOURCE_ID)
                    .withProperties(
                        lineColor("#9ca0a4"),
                        lineOpacity(0.96f),
                        lineCap(Property.LINE_CAP_ROUND),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineWidth(
                            Expression.interpolate(
                                Expression.linear(),
                                Expression.zoom(),
                                Expression.stop(13f, 2.2f),
                                Expression.stop(16f, 6.0f),
                                Expression.stop(19f, 9.0f)
                            )
                        )
                    )
                    .also { it.minZoom = 13f }
            )
        }

        if (style.getLayer(SURFACE_LAYER_ID) == null) {
            style.addLayer(
                LineLayer(SURFACE_LAYER_ID, SOURCE_ID)
                    .withProperties(
                        lineColor("#ffffff"),
                        lineOpacity(1.0f),
                        lineCap(Property.LINE_CAP_ROUND),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineWidth(
                            Expression.interpolate(
                                Expression.linear(),
                                Expression.zoom(),
                                Expression.stop(13f, 1.2f),
                                Expression.stop(16f, 4.2f),
                                Expression.stop(19f, 6.8f)
                            )
                        )
                    )
                    .also { it.minZoom = 13f }
            )
        }

        if (style.getLayer(MOTORCYCLE_LAYER_ID) == null) {
            style.addLayer(
                LineLayer(MOTORCYCLE_LAYER_ID, SOURCE_ID)
                    .withFilter(
                        Expression.eq(
                            Expression.get("access"),
                            Expression.literal(
                                QueueGoLocalRoadAccess.MOTORCYCLE_ONLY.name.lowercase()
                            )
                        )
                    )
                    .withProperties(
                        lineColor("#E6002D"),
                        lineOpacity(0.78f),
                        lineDasharray(arrayOf(2.0f, 1.2f)),
                        lineCap(Property.LINE_CAP_ROUND),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineWidth(
                            Expression.interpolate(
                                Expression.linear(),
                                Expression.zoom(),
                                Expression.stop(14f, 0.8f),
                                Expression.stop(16f, 1.3f),
                                Expression.stop(19f, 2.0f)
                            )
                        )
                    )
                    .also { it.minZoom = 14f }
            )
        }
    }
}
