package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderOnlineStatusCopyTest {
    @Test fun offlineNeverClaimsToBeReceivingJobs() {
        for (permission in listOf(false, true)) {
            for (service in listOf(false, true)) {
                val copy = riderOnlineStatusCopy(false, permission, service)
                assertEquals("ยังไม่พร้อมรับงาน", copy.title)
                assertFalse(copy.locationWarning)
            }
        }
    }

    @Test fun serverOnlineWithMissingPermissionShowsWarningInsteadOfReady() {
        val copy = riderOnlineStatusCopy(true, false, true)
        assertTrue(copy.locationWarning)
        assertTrue(copy.title.contains("GPS ไม่พร้อม"))
        assertTrue(copy.detail.contains("สถานะเซิร์ฟเวอร์ยังเปิดอยู่"))
        assertFalse(copy.title == "พร้อมรับงานแล้ว")
    }

    @Test fun serverOnlineWithDisabledLocationServiceShowsWarning() {
        val copy = riderOnlineStatusCopy(true, true, false)
        assertTrue(copy.locationWarning)
        assertFalse(copy.title == "พร้อมรับงานแล้ว")
    }

    @Test fun simultaneousPermissionAndLocationServiceLossStillWarns() {
        val copy = riderOnlineStatusCopy(true, false, false)
        assertTrue(copy.locationWarning)
        assertFalse(copy.title == "พร้อมรับงานแล้ว")
    }

    @Test fun onlyServerOnlineAndBothGpsChecksMayShowReady() {
        val copy = riderOnlineStatusCopy(true, true, true)
        assertEquals("พร้อมรับงานแล้ว", copy.title)
        assertFalse(copy.locationWarning)
    }

    @Test fun restoringGpsChangesOnlyPresentationNotServerLease() {
        val denied = riderOnlineStatusCopy(true, false, true)
        val restored = riderOnlineStatusCopy(true, true, true)
        val deniedAgain = riderOnlineStatusCopy(true, false, true)
        assertTrue(denied.locationWarning)
        assertFalse(restored.locationWarning)
        assertEquals(denied, deniedAgain)
    }

    private fun readyForActivation() = RiderDiagnosticsSnapshot(
        networkReady = true,
        locationPermission = true,
        locationServices = true,
        notifications = true,
        googleMaps = false,
        overlayReturn = false,
        microphone = false
    )

    @Test fun newOnlineLeaseRequiresAllFourLiveDeviceChecks() {
        val ready = readyForActivation()
        assertEquals(null, riderOnlineActivationBlocker(ready))
        assertTrue(riderOnlineActivationBlocker(ready.copy(networkReady = false))!!.contains("อินเทอร์เน็ต"))
        assertTrue(riderOnlineActivationBlocker(ready.copy(locationPermission = false))!!.contains("ตำแหน่ง"))
        assertTrue(riderOnlineActivationBlocker(ready.copy(locationServices = false))!!.contains("GPS"))
        assertTrue(riderOnlineActivationBlocker(ready.copy(notifications = false))!!.contains("แจ้งเตือน"))
    }

    @Test fun lossOfPermissionDuringAsyncPushBindFailsClosedBeforeServerMutation() {
        val beforePushBind = readyForActivation()
        assertEquals(null, riderOnlineActivationBlocker(beforePushBind))

        // The OS can revoke permission or disable location while registration
        // is suspended; the second read must not use the stale preflight value.
        val afterPushBind = beforePushBind.copy(locationPermission = false)
        assertTrue(riderOnlineActivationBlocker(afterPushBind) != null)
        assertEquals(null, riderOnlineActivationBlocker(afterPushBind.copy(locationPermission = true)))
    }

    @Test fun optionalGoogleMapsOverlayAndMicrophoneNeverBlockNativeOnlineLease() {
        val ready = readyForActivation()
        assertEquals(null, riderOnlineActivationBlocker(ready))
        assertEquals(null, riderOnlineActivationBlocker(
            ready.copy(googleMaps = true, overlayReturn = true, microphone = true)
        ))
    }
}
