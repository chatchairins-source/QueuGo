package com.queuego.ridermaplibreosm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueGoLocalRoadGeoJsonTest {
    @Test
    fun encodedFeatureCarriesRoadFactsAndProvenanceWithoutRiderOrOrderIdentity() {
        val segment = QueueGoLocalRoadSegment(
            id = "osm:way:123",
            points = listOf(
                QueueGoLocalRoadPoint(14.9800, 103.1000),
                QueueGoLocalRoadPoint(14.9810, 103.1010)
            ),
            roadClass = QueueGoLocalRoadClass.RESIDENTIAL,
            source = QueueGoLocalRoadSource.OPENSTREETMAP,
            verification = QueueGoLocalRoadVerification.VERIFIED,
            access = QueueGoLocalRoadAccess.MOTOR_VEHICLE,
            oneWay = true,
            widthMeters = 4.5,
            sourceReference = "osm:way:123",
            verifiedAtEpochMs = 1_797_000_000_000L
        )

        val json = queueGoLocalRoadGeoJson(listOf(segment))

        assertTrue(json.contains("\"type\":\"FeatureCollection\""))
        assertTrue(json.contains("\"road_class\":\"residential\""))
        assertTrue(json.contains("\"source\":\"openstreetmap\""))
        assertTrue(json.contains("\"verification\":\"verified\""))
        assertTrue(json.contains("\"one_way\":true"))
        assertTrue(json.contains("[103.1,14.98]"))
        assertFalse(json.contains("rider_id"))
        assertFalse(json.contains("order_id"))
        assertFalse(json.contains("phone"))
    }

    @Test
    fun retiredRoadIsNotRendered() {
        val retired = QueueGoLocalRoadSegment(
            id = "retired:1",
            points = listOf(
                QueueGoLocalRoadPoint(14.9800, 103.1000),
                QueueGoLocalRoadPoint(14.9810, 103.1010)
            ),
            roadClass = QueueGoLocalRoadClass.SERVICE,
            source = QueueGoLocalRoadSource.FIELD_SURVEY,
            verification = QueueGoLocalRoadVerification.RETIRED,
            sourceReference = "survey:retired:1"
        )

        val json = queueGoLocalRoadGeoJson(listOf(retired))
        assertFalse(json.contains("retired:1"))
    }
}
