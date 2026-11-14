package com.queuego.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderDisplayNoteTest {
    @Test fun statusOnlyDoesNotExposeProtocolText() {
        assertNull(cleanOrderDisplayNote("\n__QT_ORDER_STATUS__=completed\n"))
        assertNull(cleanOrderDisplayNote(null))
        assertNull(cleanOrderDisplayNote("  "))
    }
    @Test fun customerInstructionsSurviveProtocolLines() {
        assertEquals("ไม่ใส่พริก\nโทรเมื่อถึง",
            cleanOrderDisplayNote("\nไม่ใส่พริก\n__QT_ORDER_STATUS__=completed\nโทรเมื่อถึง\n"))
    }
    @Test fun embeddedMarkerAndOtherNotesRemainUntouched() {
        assertEquals("ข้อความ __QT_ORDER_STATUS__= ยังเป็นข้อความลูกค้า",
            cleanOrderDisplayNote("ข้อความ __QT_ORDER_STATUS__= ยังเป็นข้อความลูกค้า"))
        assertEquals("__QT_CANCEL_REASON__=ฝนตก", cleanOrderDisplayNote("__QT_CANCEL_REASON__=ฝนตก"))
    }
}
