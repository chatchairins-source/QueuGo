package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderConnectivityNoticeTest {
    @Test fun explicitNetworkLossShowsImmediatelyWithSafeSnapshot() {
        val model = riderConnectivityNotice(
            consecutiveRefreshFailures = 0,
            hasSnapshot = true,
            hasActiveWork = true,
            networkAvailable = false
        )
        requireNotNull(model)
        assertTrue(model.detail.contains("งานยังอยู่ในเครื่อง"))
    }

    @Test fun transientSingleMissStaysSilent() {
        assertNull(
            riderConnectivityNotice(
                consecutiveRefreshFailures = 1,
                hasSnapshot = true,
                hasActiveWork = true
            )
        )
    }

    @Test fun noCachedStateUsesExistingFirstLoadRecoveryInstead() {
        assertNull(
            riderConnectivityNotice(
                consecutiveRefreshFailures = 5,
                hasSnapshot = false,
                hasActiveWork = false
            )
        )
    }

    @Test fun activeWorkExplainsThatJobRemainsAvailable() {
        val model = riderConnectivityNotice(
            consecutiveRefreshFailures = 2,
            hasSnapshot = true,
            hasActiveWork = true
        )
        requireNotNull(model)
        assertEquals("สัญญาณขัดข้อง", model.title)
        assertTrue(model.detail.contains("งานยังอยู่ในเครื่อง"))
        assertTrue(model.detail.contains("กำลังเชื่อมต่อใหม่"))
    }

    @Test fun idleStateUsesCompactReconnectCopy() {
        val model = riderConnectivityNotice(
            consecutiveRefreshFailures = 3,
            hasSnapshot = true,
            hasActiveWork = false
        )
        requireNotNull(model)
        assertEquals("กำลังเชื่อมต่อ QueueGo ใหม่", model.detail)
    }
}
