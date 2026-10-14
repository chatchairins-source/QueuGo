package com.queuego.rider

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderReverificationTest {
    @Test fun emptyStatusDoesNotInventAReverificationRequest() {
        val parsed = riderReverificationStatusFromRpc(JSONObject().put("required", false))
        assertFalse(parsed.required)
        assertEquals(null, parsed.requestId)
        assertEquals(null, parsed.status)
    }

    @Test fun rejectedRequestRemainsActionableWithReviewNote() {
        val parsed = riderReverificationStatusFromRpc(
            JSONObject()
                .put("required", true)
                .put("request_id", "11111111-1111-1111-1111-111111111111")
                .put("status", "rejected")
                .put("review_note", "กรุณาถ่ายหน้าตรงในที่สว่าง")
        )
        assertTrue(parsed.required)
        assertEquals("rejected", parsed.status)
        assertEquals("กรุณาถ่ายหน้าตรงในที่สว่าง", parsed.reviewNote)
        assertEquals("ต้องถ่ายรูปยืนยันใหม่", riderReverificationTitle(parsed.status))
    }

    @Test fun pendingRequestUsesWaitingState() {
        assertEquals("ส่งรูปแล้ว · รอตรวจสอบ", riderReverificationTitle("pending"))
    }
}
