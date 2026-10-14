package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiderSafetyReportOverviewTest {
    @Test fun supportStatusesMapToRiderFacingLabelsWithoutInventingSla() {
        assertEquals("รับเรื่องแล้ว", riderSafetyReportStatusLabel("OPEN"))
        assertEquals("กำลังตรวจสอบ", riderSafetyReportStatusLabel("IN_REVIEW"))
        assertEquals("รอข้อมูลเพิ่มเติม", riderSafetyReportStatusLabel("WAITING_USER"))
        assertEquals("แก้ไขแล้ว", riderSafetyReportStatusLabel("RESOLVED"))
        assertEquals("ปิดเคส", riderSafetyReportStatusLabel("CLOSED"))
        assertEquals("ปิดเคส", riderSafetyReportStatusLabel("REJECTED"))
    }

    @Test fun reasonComesFromExistingSafetyIncidentContract() {
        assertEquals(
            "พื้นที่หรือจุดส่งไม่ปลอดภัย",
            riderSafetyReportReasonLabel(
                "[SAFETY_INCIDENT:unsafe_location] QT-4321 · พื้นที่หรือจุดส่งไม่ปลอดภัย"
            )
        )
    }

    @Test fun orderLabelIsShownOnlyForQueueGoOrderNumbers() {
        assertEquals(
            "QT-4321",
            riderSafetyReportOrderLabel(
                "[SAFETY_INCIDENT:road_hazard] QT-4321 · อันตรายบนถนน / เส้นทาง"
            )
        )
        assertNull(
            riderSafetyReportOrderLabel(
                "[SAFETY_INCIDENT:other] ไม่มีออเดอร์ · เหตุการณ์อื่น"
            )
        )
    }

    @Test fun unknownReasonFailsClosedToGenericSafetyLabel() {
        assertEquals(
            "เหตุการณ์ความปลอดภัย",
            riderSafetyReportReasonLabel(
                "[SAFETY_INCIDENT:new_server_reason] QT-9999 · server copy"
            )
        )
    }
}
