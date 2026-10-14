package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSignalMetricsTest {
    private val route = NavigationRouteSegment(
        id = "active",
        toStopId = "shop",
        points = listOf(
            NavigationMapPoint(15.0000, 103.0000),
            NavigationMapPoint(15.0000, 103.0100)
        ),
        style = NavigationRouteStyle.ACTIVE
    )

    @Test
    fun shadowMetricsAggregateCountsAndOffsetsWithoutPersistingCoordinates() {
        val pipeline = NavigationSignalPipeline()

        pipeline.update(
            NavigationMapFix(
                point = NavigationMapPoint(15.00005, 103.0020),
                bearingDegrees = 90f,
                accuracyMeters = 8f,
                speedMetersPerSecond = 6f,
                elapsedRealtimeNanos = 1_000_000_000L,
                bearingAccuracyDegrees = 8f
            ),
            route
        )
        pipeline.update(
            NavigationMapFix(
                point = NavigationMapPoint(15.00020, 103.0020),
                bearingDegrees = 40f,
                accuracyMeters = 75f,
                speedMetersPerSecond = 0.4f,
                elapsedRealtimeNanos = 2_000_000_000L,
                bearingAccuracyDegrees = 70f
            ),
            route
        )
        pipeline.update(
            NavigationMapFix(
                point = NavigationMapPoint(15.0300, 103.0020),
                bearingDegrees = 90f,
                accuracyMeters = 8f,
                speedMetersPerSecond = 6f,
                elapsedRealtimeNanos = 3_000_000_000L,
                bearingAccuracyDegrees = 8f
            ),
            route
        )

        val metrics = pipeline.metricsSnapshot()

        assertEquals(3L, metrics.totalSamples)
        assertEquals(2L, metrics.acceptedSamples)
        assertEquals(1L, metrics.rejectedSamples)
        assertTrue(metrics.snappedSamples >= 1L)
        assertTrue(metrics.weakGpsSamples >= 1L)
        assertTrue(metrics.heldSamples >= 1L)
        assertTrue(metrics.averageRawDisplayOffsetMeters >= 0.0)
        assertTrue(metrics.maxRawDisplayOffsetMeters >= metrics.averageRawDisplayOffsetMeters)
    }

    @Test
    fun resetClearsAllRolloutAggregates() {
        val pipeline = NavigationSignalPipeline()
        pipeline.update(
            NavigationMapFix(
                point = NavigationMapPoint(15.0000, 103.0020),
                accuracyMeters = 8f,
                speedMetersPerSecond = 4f,
                elapsedRealtimeNanos = 1_000_000_000L
            ),
            route
        )

        pipeline.reset()
        val metrics = pipeline.metricsSnapshot()

        assertEquals(NavigationSignalMetricsSnapshot(), metrics)
    }
}
