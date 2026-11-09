package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class BundledOfflineRoutingTextTest {
    @Test fun boundsActualBytesEvenWhenAvailableReportsZero() {
        val bytes = "ภาษาไทย".toByteArray(Charsets.UTF_8)
        val stream = object : ByteArrayInputStream(bytes) {
            override fun available() = 0
        }
        assertEquals("ภาษาไทย", readBoundedRoutingText(stream, bytes.size))
        assertThrows(IllegalArgumentException::class.java) {
            readBoundedRoutingText(ByteArrayInputStream(bytes), bytes.size - 1)
        }
    }
}
