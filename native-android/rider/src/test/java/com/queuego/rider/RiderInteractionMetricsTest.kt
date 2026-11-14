package com.queuego.rider

import org.junit.Assert.assertTrue
import org.junit.Test

class RiderInteractionMetricsTest {
    @Test fun everyCriticalTouchTargetMeetsAndroidMinimum() {
        assertTrue(RIDER_SECONDARY_ACTION_MIN_HEIGHT_DP >= 48)
        assertTrue(RIDER_PRIMARY_ACTION_MIN_HEIGHT_DP >= 48)
        assertTrue(RIDER_PRIMARY_ACTION_MIN_HEIGHT_DP >= RIDER_SECONDARY_ACTION_MIN_HEIGHT_DP)
    }

    @Test fun actionLabelsStayReadableAtWorkSpeed() {
        assertTrue(RIDER_TOOL_TEXT_SP >= 11)
        assertTrue(RIDER_SECONDARY_ACTION_TEXT_SP >= 12)
        assertTrue(RIDER_PRIMARY_ACTION_TEXT_SP >= RIDER_SECONDARY_ACTION_TEXT_SP)
    }
}
