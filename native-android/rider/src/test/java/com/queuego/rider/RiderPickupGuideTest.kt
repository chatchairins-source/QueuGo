package com.queuego.rider

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiderPickupGuideTest {
    @Test fun parserKeepsOnlyExplicitPickupGuideFields() {
        val guide = riderPickupGuideFromRpc(
            JSONObject()
                .put("pickup_instructions", "รับของที่เคาน์เตอร์ขวา")
                .put("parking_instructions", "จอดหน้าร้าน 5 นาที")
                .put("market_zone", "A")
                .put("market_stall_no", "12")
        )

        assertEquals("รับของที่เคาน์เตอร์ขวา", guide.pickupInstructions)
        assertEquals("จอดหน้าร้าน 5 นาที", guide.parkingInstructions)
        assertEquals("A", guide.marketZone)
        assertEquals("12", guide.marketStallNo)
        assertEquals("โซน A · แผง 12", riderPickupAreaLabel(guide.marketZone, guide.marketStallNo))
    }

    @Test fun emptyGuideDoesNotInventPickupInstructions() {
        val guide = riderPickupGuideFromRpc(JSONObject())
        assertNull(guide.pickupInstructions)
        assertNull(guide.parkingInstructions)
        assertNull(riderPickupAreaLabel(null, null))
    }
}
