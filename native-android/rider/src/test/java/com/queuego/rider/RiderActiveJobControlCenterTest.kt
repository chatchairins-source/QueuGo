package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiderActiveJobControlCenterTest {
    @Test
    fun etaLabelRoundsUpToWholeMinutes() {
        assertEquals("ประมาณ 1 นาที", riderActiveJobEtaLabel(1.0))
        assertEquals("ประมาณ 2 นาที", riderActiveJobEtaLabel(61.0))
        assertEquals("ประมาณ 10 นาที", riderActiveJobEtaLabel(600.0))
    }

    @Test
    fun etaLabelRejectsInvalidOrStaleRange() {
        assertNull(riderActiveJobEtaLabel(null))
        assertNull(riderActiveJobEtaLabel(Double.NaN))
        assertNull(riderActiveJobEtaLabel(-1.0))
        assertNull(riderActiveJobEtaLabel(14_401.0))
    }
}
