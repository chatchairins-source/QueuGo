package com.queuego.ridermaplibreosm

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.backgroundColor
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.VectorSource

data class ShortbreadRoadPolicy(
    val kind: String,
    val minZoom: Float,
    val casingColor: String,
    val surfaceColor: String,
    val widthAtMinZoom: Float,
    val widthAt16: Float,
    val widthAt19: Float
)

data class ShortbreadContextRoadPolicy(
    val kind: String,
    val minZoom: Float,
    val color: String,
    val widthAtMinZoom: Float,
    val widthAt16: Float,
    val widthAt19: Float,
    val dashPattern: Array<Float>
)

object ShortbreadVillageStyle {
    const val SOURCE_ID = "queuego-osm-shortbread"
    const val SOURCE_LAYER_STREETS = "streets"
    const val SOURCE_LAYER_WATER = "water_polygons"
    const val SOURCE_LAYER_BUILDINGS = "buildings"

    val roadPolicies = listOf(
        ShortbreadRoadPolicy("motorway", 5f, "#c47d79", "#e8a7a1", 1.3f, 7.0f, 12.0f),
        ShortbreadRoadPolicy("trunk", 6f, "#c98f82", "#edb4a4", 1.2f, 6.5f, 11.0f),
        ShortbreadRoadPolicy("primary", 8f, "#d1a66a", "#f2cf86", 1.1f, 6.0f, 10.0f),
        ShortbreadRoadPolicy("secondary", 9f, "#c7b678", "#f2dfa0", 1.0f, 5.4f, 9.0f),
        ShortbreadRoadPolicy("tertiary", 10f, "#b7b7ac", "#f7f4e8", 0.9f, 4.8f, 8.2f),
        ShortbreadRoadPolicy("unclassified", 12f, "#b5b9bd", "#ffffff", 0.8f, 4.2f, 7.2f),
        ShortbreadRoadPolicy("residential", 12f, "#b5b9bd", "#ffffff", 0.8f, 4.2f, 7.2f),
        ShortbreadRoadPolicy("living_street", 13f, "#bec1c4", "#ffffff", 0.7f, 3.8f, 6.6f),
        ShortbreadRoadPolicy("service", 13f, "#c4c6c8", "#ffffff", 0.6f, 3.2f, 5.6f)
    )

    // Shortbread contains more local-access geometry than a car-navigation route should
    // necessarily use. Draw these as muted dashed context so village alleys/tracks are visible
    // without presenting every foot/cycle path as a normal drivable street.
    val contextRoadPolicies = listOf(
        ShortbreadContextRoadPolicy("track", 13f, "#b9b4aa", 0.8f, 2.2f, 3.8f, arrayOf(2.0f, 1.2f)),
        ShortbreadContextRoadPolicy("pedestrian", 14f, "#c7c3bc", 0.7f, 1.8f, 3.2f, arrayOf(1.5f, 1.0f)),
        ShortbreadContextRoadPolicy("cycleway", 14f, "#bfc9bf", 0.7f, 1.7f, 3.0f, arrayOf(1.6f, 1.1f)),
        ShortbreadContextRoadPolicy("footway", 15f, "#c9c7c2", 0.6f, 1.4f, 2.5f, arrayOf(1.2f, 1.2f)),
        ShortbreadContextRoadPolicy("path", 15f, "#c9c7c2", 0.6f, 1.4f, 2.5f, arrayOf(1.2f, 1.2f))
    )

    fun builder(sourceUri: String): Style.Builder {
        var builder = Style.Builder()
            .withSource(VectorSource(SOURCE_ID, sourceUri))
            .withLayer(
                BackgroundLayer("queuego-background").withProperties(
                    backgroundColor("#f3f0e9")
                )
            )
            .withLayer(
                FillLayer("queuego-water", SOURCE_ID)
                    .withSourceLayer(SOURCE_LAYER_WATER)
                    .withProperties(
                        fillColor("#b9dadd"),
                        fillOpacity(1.0f)
                    )
            )
            .withLayer(
                FillLayer("queuego-buildings", SOURCE_ID)
                    .withSourceLayer(SOURCE_LAYER_BUILDINGS)
                    .withProperties(
                        fillColor("#d5d7d9"),
                        fillOpacity(0.9f)
                    )
                    .also { it.minZoom = 14f }
            )

        contextRoadPolicies.forEach { policy ->
            val filter = Expression.eq(
                Expression.get("kind"),
                Expression.literal(policy.kind)
            )
            val contextLine = LineLayer("queuego-context-${policy.kind}", SOURCE_ID)
                .withSourceLayer(SOURCE_LAYER_STREETS)
                .withFilter(filter)
                .withProperties(
                    lineColor(policy.color),
                    lineOpacity(0.78f),
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND),
                    lineDasharray(policy.dashPattern),
                    lineWidth(
                        Expression.interpolate(
                            Expression.linear(),
                            Expression.zoom(),
                            Expression.stop(policy.minZoom, policy.widthAtMinZoom),
                            Expression.stop(16f, policy.widthAt16),
                            Expression.stop(19f, policy.widthAt19)
                        )
                    )
                )
                .also { it.minZoom = policy.minZoom }
            builder = builder.withLayer(contextLine)
        }

        roadPolicies.forEach { policy ->
            val filter = Expression.eq(
                Expression.get("kind"),
                Expression.literal(policy.kind)
            )
            val casing = LineLayer("queuego-road-${policy.kind}-casing", SOURCE_ID)
                .withSourceLayer(SOURCE_LAYER_STREETS)
                .withFilter(filter)
                .withProperties(
                    lineColor(policy.casingColor),
                    lineOpacity(1.0f),
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND),
                    lineWidth(
                        Expression.interpolate(
                            Expression.linear(),
                            Expression.zoom(),
                            Expression.stop(policy.minZoom, policy.widthAtMinZoom + 1.4f),
                            Expression.stop(16f, policy.widthAt16 + 1.8f),
                            Expression.stop(19f, policy.widthAt19 + 2.2f)
                        )
                    )
                )
                .also { it.minZoom = policy.minZoom }

            val surface = LineLayer("queuego-road-${policy.kind}", SOURCE_ID)
                .withSourceLayer(SOURCE_LAYER_STREETS)
                .withFilter(filter)
                .withProperties(
                    lineColor(policy.surfaceColor),
                    lineOpacity(1.0f),
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND),
                    lineWidth(
                        Expression.interpolate(
                            Expression.linear(),
                            Expression.zoom(),
                            Expression.stop(policy.minZoom, policy.widthAtMinZoom),
                            Expression.stop(16f, policy.widthAt16),
                            Expression.stop(19f, policy.widthAt19)
                        )
                    )
                )
                .also { it.minZoom = policy.minZoom }

            builder = builder.withLayer(casing).withLayer(surface)
        }

        return builder
    }
}
