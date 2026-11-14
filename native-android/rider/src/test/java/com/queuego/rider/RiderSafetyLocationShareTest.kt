package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderSafetyLocationShareTest {
    @Test fun shareUsesOnlyOneCurrentCoordinateSnapshot() {
        val location = riderSafetyLocationOrNull(15.1234567, 104.9876543)!!
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=15.123457,104.987654",
            riderSafetyLocationUrl(location)
        )
        val text = riderSafetyLocationShareText(location)
        assertTrue(text.contains("ตำแหน่งปัจจุบันของฉัน"))
        assertTrue(text.contains("ไม่ใช่การติดตามแบบเรียลไทม์"))
        assertTrue(!text.contains("ออเดอร์"))
        assertTrue(!text.contains("ลูกค้า"))
    }

    @Test fun invalidCoordinatesAreRejected() {
        assertNull(riderSafetyLocationOrNull(Double.NaN, 104.0))
        assertNull(riderSafetyLocationOrNull(91.0, 104.0))
        assertNull(riderSafetyLocationOrNull(15.0, 181.0))
    }
}
