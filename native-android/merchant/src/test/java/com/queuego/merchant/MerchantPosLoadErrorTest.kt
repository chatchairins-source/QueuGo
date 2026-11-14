package com.queuego.merchant

import java.net.SocketTimeoutException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantPosLoadErrorTest {
    @Test fun timeoutIsExplainedInThaiAndCanBeRetried() {
        val fromSocket = merchantPosLoadError(SocketTimeoutException("timeout"))
        val fromBackend = merchantPosLoadError(IllegalStateException("timeout"))
        for (message in listOf(fromSocket, fromBackend)) {
            assertTrue(message.contains("โหลดข้อมูล POS ไม่ทัน"))
            assertTrue(message.contains("ลองอีกครั้ง"))
            assertFalse(message.equals("timeout", ignoreCase = true))
        }
    }

    @Test fun successfulRefreshClearsOnlyPreviousReadFailure() {
        assertTrue(merchantPosIsReadFailureMessage("timeout"))
        assertTrue(merchantPosIsReadFailureMessage("โหลดข้อมูล POS ไม่ทัน ระบบหรือเครือข่ายตอบช้า"))
        assertFalse(merchantPosIsReadFailureMessage("บันทึกบิลสำเร็จ"))
        assertFalse(merchantPosIsReadFailureMessage("ออฟไลน์ · ใช้แคตตาล็อกที่บันทึกไว้สร้างร่างเท่านั้น"))
    }
}
