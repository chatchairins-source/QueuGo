package com.queuego.ridermaplibreosm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueGoLocalRoadsTest {
    private fun road(
        id: String,
        verified: Boolean = true,
        access: QueueGoLocalRoadAccess = QueueGoLocalRoadAccess.MOTORCYCLE_AND_CAR
    ) = QueueGoLocalRoad(
        id = id,
        points = listOf(
            QueueGoLocalRoadPoint(15.0000, 103.0000),
            QueueGoLocalRoadPoint(15.0005, 103.0007)
        ),
        source = QueueGoLocalRoadSource.FIELD_SURVEY,
        verified = verified,
        access = access,
        oneWay = true,
        surface = QueueGoLocalRoadSurface.PAVED
    )

    @Test
    fun onlyVerifiedNonPrivateRoadsBecomeMapFeatures() {
        val features = queueGoVerifiedLocalRoadFeatures(
            listOf(
                road("verified"),
                road("candidate", verified = false),
                road("private", access = QueueGoLocalRoadAccess.PRIVATE_RESTRICTED)
            )
        ).features().orEmpty()

        assertEquals(1, features.size)
        assertEquals("verified", features.single().getStringProperty("road_id"))
        assertTrue(features.single().getBooleanProperty("one_way"))
    }

    @Test
    fun duplicateIdsCannotRenderTwice() {
        val features = queueGoVerifiedLocalRoadFeatures(
            listOf(road("same"), road("same"))
        ).features().orEmpty()

        assertEquals(1, features.size)
    }

    @Test
    fun proprietaryCompetitorGeometryIsNotAnAcceptedProvenanceType() {
        val accepted = QueueGoLocalRoadSource.entries.map { it.name.lowercase() }
        assertFalse(accepted.any { it.contains("grab") })
        assertEquals(
            setOf(
                "openstreetmap_verified",
                "public_data",
                "rider_gps_trace_verified",
                "merchant_confirmed",
                "field_survey"
            ),
            accepted.toSet()
        )
    }
}
