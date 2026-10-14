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

object QueueGoLocalRoadOverlay {
    const val SOURCE_ID = "queuego-local-roads"
    const val VERIFIED_CASING_LAYER_ID = "queuego-local-roads-verified-casing"
    const val VERIFIED_LAYER_ID = "queuego-local-roads-verified"
    const val PRIVATE_LAYER_ID = "queuego-local-roads-private"
    const val CANDIDATE_LAYER_ID = "queuego-local-roads-candidate"

    fun installOrUpdate(
        style: Style,
        segments: List<QueueGoLocalRoadSegment>
    ) {
        val geoJson = queueGoLocalRoadGeoJson(segments)
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID)
        if (source == null) {
            style.addSource(GeoJsonSource(SOURCE_ID, geoJson))
        } else {
            source.setGeoJson(geoJson)
        }

        if (style.getLayer(VERIFIED_CASING_LAYER_ID) == null) {
            style.addLayer(
                LineLayer(VERIFIED_CASING_LAYER_ID, SOURCE_ID)
                    .withFilter(
                        Expression.all(
                            Expression.eq(
                                Expression.get("verification"),
                                Expression.literal(QueueGoLocalRoadVerification.VERIFIED.wireName)
                            ),
                            Expression.eq(
                                Expression.get("private_access"),
                                Expression.literal(false)
                            )
                        )
                    )
                    .withProperties(
                        lineColor("#aeb4ba"),
                        lineOpacity(0.96f),
                        lineCap(Property.LINE_CAP_ROUND),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineWidth(
                            Expression.interpolate(
                                Expression.linear(),
                                Expression.zoom(),
                                Expression.stop(13f, 3.2f),
                                Expression.stop(16f, 6.2f),
                                Expression.stop(19f, 10.0f)
                            )
                        )
                    )
                    .also { it.minZoom = 13f }
            )
        }

        if (style.getLayer(VERIFIED_LAYER_ID) == null) {
            style.addLayer(
                LineLayer(VERIFIED_LAYER_ID, SOURCE_ID)
                    .withFilter(
                        Expression.all(
                            Expression.eq(
                                Expression.get("verification"),
                                Expression.literal(QueueGoLocalRoadVerification.VERIFIED.wireName)
                            ),
                            Expression.eq(
                                Expression.get("private_access"),
                                Expression.literal(false)
                            )
                        )
                    )
                    .withProperties(
                        lineColor("#ffffff"),
                        lineOpacity(1.0f),
                        lineCap(Property.LINE_CAP_ROUND),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineWidth(
                            Expression.interpolate(
                                Expression.linear(),
                                Expression.zoom(),
                                Expression.stop(13f, 1.8f),
                                Expression.stop(16f, 4.3f),
                                Expression.stop(19f, 7.5f)
                            )
                        )
                    )
                    .also { it.minZoom = 13f }
            )
        }

        if (style.getLayer(PRIVATE_LAYER_ID) == null) {
            style.addLayer(
                LineLayer(PRIVATE_LAYER_ID, SOURCE_ID)
                    .withFilter(
                        Expression.all(
                            Expression.eq(
                                Expression.get("verification"),
                                Expression.literal(QueueGoLocalRoadVerification.VERIFIED.wireName)
                            ),
                            Expression.eq(
                                Expression.get("private_access"),
                                Expression.literal(true)
                            )
                        )
                    )
                    .withProperties(
                        lineColor("#c7a8a8"),
                        lineOpacity(0.82f),
                        lineCap(Property.LINE_CAP_ROUND),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineDasharray(arrayOf(2.0f, 1.4f)),
                        lineWidth(
                            Expression.interpolate(
                                Expression.linear(),
                                Expression.zoom(),
                                Expression.stop(14f, 1.6f),
                                Expression.stop(17f, 3.2f),
                                Expression.stop(19f, 5.0f)
                            )
                        )
                    )
                    .also { it.minZoom = 14f }
            )
        }

        if (style.getLayer(CANDIDATE_LAYER_ID) == null) {
            style.addLayer(
                LineLayer(CANDIDATE_LAYER_ID, SOURCE_ID)
                    .withFilter(
                        Expression.eq(
                            Expression.get("verification"),
                            Expression.literal(QueueGoLocalRoadVerification.CANDIDATE.wireName)
                        )
                    )
                    .withProperties(
                        lineColor("#a8adb3"),
                        lineOpacity(0.72f),
                        lineCap(Property.LINE_CAP_ROUND),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineDasharray(arrayOf(1.5f, 1.2f)),
                        lineWidth(
                            Expression.interpolate(
                                Expression.linear(),
                                Expression.zoom(),
                                Expression.stop(15f, 1.2f),
                                Expression.stop(17f, 2.2f),
                                Expression.stop(19f, 3.5f)
                            )
                        )
                    )
                    .also { it.minZoom = 15f }
            )
        }
    }
}
