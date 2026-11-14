package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationViewportInsetsTest {
    @Test
    fun negativeInsetsAreClampedBeforeRendererUse() {
        val safe = NavigationViewportInsets(
            leftPx = -10,
            topPx = 24,
            rightPx = -1,
            bottomPx = 320
        ).normalized()

        assertEquals(0, safe.leftPx)
        assertEquals(24, safe.topPx)
        assertEquals(0, safe.rightPx)
        assertEquals(320, safe.bottomPx)
        assertFalse(safe.isEmpty)
        assertEquals("0:24:0:320", navigationViewportInsetsSignature(safe))
    }

    @Test
    fun zeroInsetsRemainEmpty() {
        assertTrue(NavigationViewportInsets().isEmpty)
    }
}
