package com.queuego.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeOrderNumberTest {
    @Test fun followsProductionLegacyAndCurrentOrderNumbers() {
        val fixtures = mapOf(
            "QT-20261006-2141" to "QT-2141", "QT-001" to "QT-0001",
            "QT-9198" to "QT-9198", "LW-20260930-112419-e26b" to "QT-7963",
            "LW-20260930-110545-251e" to "QT-9502", "LW-20260930-110459-8543" to "QT-4115",
            "POS-32D8C1AD0747" to "QT-1863", "QR-12DBF4E490AE" to "QT-7038",
            "d889d1a6-c10d-4f61-a6c8-31312dd6cbd1" to "QT-----", "" to "QT-----",
            "QO-20261006-ABCD" to "QT-1261", "  qt-001  " to "QT-0001"
        )
        fixtures.forEach { (raw, expected) -> assertEquals(raw, expected, nativeOrderNumber(raw)) }
        assertEquals("QT-----", nativeOrderNumber(null))
    }
}
