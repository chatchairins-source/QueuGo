package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.queuego.ridernavigationmap.NavigationGuidanceState

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

    @Test
    fun etaIsHiddenWhenNavigationEvidenceIsUnreliable() {
        assertEquals(
            300.0,
            riderActiveJobEtaSeconds(NavigationGuidanceState(etaSeconds = 300.0)),
            0.0
        )
        assertNull(riderActiveJobEtaSeconds(NavigationGuidanceState(etaSeconds = 300.0, weakGps = true)))
        assertNull(riderActiveJobEtaSeconds(NavigationGuidanceState(etaSeconds = 300.0, offRoute = true)))
    }

    @Test
    fun contactLabelFollowsCurrentLeg() {
        assertEquals("โทรร้าน", riderActiveJobContactLabel(true))
        assertEquals("โทรลูกค้า", riderActiveJobContactLabel(false))
    }
}
