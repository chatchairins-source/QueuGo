package com.queuego.ridermaplibreosm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortbreadVillageStylePolicyTest {
    @Test fun villageRoadClassesArePresentAtNeighborhoodZooms() {
        val byKind = ShortbreadVillageStyle.roadPolicies.associateBy { it.kind }
        listOf("residential", "unclassified", "living_street", "service").forEach { kind ->
            assertTrue("Missing village road class: $kind", byKind.containsKey(kind))
        }
        assertTrue(byKind.getValue("residential").minZoom <= 12f)
        assertTrue(byKind.getValue("unclassified").minZoom <= 12f)
        assertTrue(byKind.getValue("living_street").minZoom <= 13f)
        assertTrue(byKind.getValue("service").minZoom <= 13f)
        assertFalse(
            "Shortbread does not guarantee a land source-layer",
            ShortbreadVillageStyle::class.java.declaredFields.any { it.name == "SOURCE_LAYER_LAND" }
        )
    }

    @Test fun localRoadsGrowAtCloseNavigationZoom() {
        val residential = ShortbreadVillageStyle.roadPolicies.first { it.kind == "residential" }
        assertTrue(residential.widthAt16 > residential.widthAtMinZoom)
        assertTrue(residential.widthAt19 > residential.widthAt16)
        assertEquals("#ffffff", residential.surfaceColor)
    }
}
