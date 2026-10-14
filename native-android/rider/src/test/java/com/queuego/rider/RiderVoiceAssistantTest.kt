package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RiderVoiceAssistantTest {
    private fun job(
        status: String,
        arrivedShopAt: String? = null,
        arrivedCustomerAt: String? = null
    ) = RiderJob(
        id = "order-1",
        orderNumber = "QT-1001",
        status = status,
        pickupAddress = null,
        pickupLat = null,
        pickupLng = null,
        deliveryAddress = null,
        deliveryLat = null,
        deliveryLng = null,
        deliveryFee = 25.0,
        marketOrderId = null,
        arrivedShopAt = arrivedShopAt,
        arrivedCustomerAt = arrivedCustomerAt
    )

    @Test fun offerAnnouncementContainsNoPrivateTripData() {
        assertEquals(
            "มีงานใหม่ กรุณาตรวจสอบและตอบรับภายในสามสิบวินาที",
            riderVoiceOfferAnnouncement()
        )
    }

    @Test fun jobAnnouncementsFollowAuthoritativeStage() {
        assertEquals("รับงานแล้ว กำลังไปที่ร้าน", riderVoiceJobAnnouncement(job("rider_assigned")))
        assertEquals(
            "ถึงร้านแล้ว กรุณาตรวจสอบรายการและรอรับสินค้า",
            riderVoiceJobAnnouncement(job("rider_assigned", arrivedShopAt = "now"))
        )
        assertEquals("ร้านกำลังเตรียมสินค้า", riderVoiceJobAnnouncement(job("preparing")))
        assertEquals("ร้านเตรียมสินค้าเสร็จแล้ว กรุณาไปรับสินค้า", riderVoiceJobAnnouncement(job("ready")))
        assertEquals("รับสินค้าแล้ว กำลังนำส่งลูกค้า", riderVoiceJobAnnouncement(job("in_progress")))
        assertEquals(
            "ถึงจุดส่งแล้ว กรุณาตรวจสอบยอดในหน้าจอและส่งสินค้า",
            riderVoiceJobAnnouncement(job("in_progress", arrivedCustomerAt = "now"))
        )
        assertNull(riderVoiceJobAnnouncement(job("searching_rider")))
    }

    @Test fun arrivalChangesStageKeyEvenWhenOrderStatusDoesNotChange() {
        val before = riderVoiceJobStageKey(job("rider_assigned"))
        val after = riderVoiceJobStageKey(job("rider_assigned", arrivedShopAt = "now"))
        assertNotEquals(before, after)
    }
}
