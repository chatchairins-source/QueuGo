package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderMapFeedbackTest {
    @Test fun categoriesAreClosedAndHumanReadable() {
        assertEquals(
            setOf("road_closed","wrong_route","missing_road","bad_road","unsafe_turn","other"),
            riderMapFeedbackCategoryKeys
        )
        assertEquals("ถนนปิด", riderMapFeedbackCategoryLabel("road_closed"))
        assertEquals("จุดเลี้ยวไม่ปลอดภัย", riderMapFeedbackCategoryLabel("unsafe_turn"))
    }

    @Test fun unknownLabelFailsClosedToRawKeyOnly() {
        assertEquals("unknown", riderMapFeedbackCategoryLabel("unknown"))
        assertTrue("unknown" !in riderMapFeedbackCategoryKeys)
    }
}
