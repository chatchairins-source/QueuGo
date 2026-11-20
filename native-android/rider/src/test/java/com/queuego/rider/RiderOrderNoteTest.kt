package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiderOrderNoteTest {
    @Test fun hidesInternalStatusWithoutDiscardingCustomerInstructions() {
        assertEquals("ไม่ใส่พริก\nโทรเมื่อถึง", cleanRiderOrderNote("\nไม่ใส่พริก\n__QT_ORDER_STATUS__=ARRIVAL\nโทรเมื่อถึง\n"))
        assertEquals("ข้อความ __QT_ORDER_STATUS__= ยังเป็นข้อความลูกค้า", cleanRiderOrderNote("ข้อความ __QT_ORDER_STATUS__= ยังเป็นข้อความลูกค้า"))
    }

    @Test fun absentAndInternalOnlyNotesDoNotRenderASection() {
        assertNull(cleanRiderOrderNote(null))
        assertNull(cleanRiderOrderNote(" \n__QT_ORDER_STATUS__=READY\n"))
    }
}
