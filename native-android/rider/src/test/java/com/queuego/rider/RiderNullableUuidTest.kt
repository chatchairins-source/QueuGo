package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiderNullableUuidTest {
    @Test
    fun jsonNullLikeValuesNeverBecomeUuid() {
        assertNull(riderNullableUuid(null))
        assertNull(riderNullableUuid(""))
        assertNull(riderNullableUuid("   "))
        assertNull(riderNullableUuid("null"))
        assertNull(riderNullableUuid("NULL"))
        assertNull(riderNullableUuid("not-a-uuid"))
    }

    @Test
    fun validUuidSurvivesParsing() {
        val id = "5f68cef3-95c6-43a5-aa1f-85cf588b53ed"
        assertEquals(id, riderNullableUuid(id))
        assertEquals(id, riderNullableUuid("  $id  "))
    }

    @Test
    fun nullableTextRejectsJsonNullLiteral() {
        assertNull(riderNullableText(null))
        assertNull(riderNullableText("null"))
        assertEquals("QueueGo", riderNullableText(" QueueGo "))
    }
}
