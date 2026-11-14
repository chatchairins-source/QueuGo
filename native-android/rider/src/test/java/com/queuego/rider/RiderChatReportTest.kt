package com.queuego.rider

import org.junit.Assert.*
import org.junit.Test

class RiderChatReportTest {
    @Test fun stripsOnlyTheChatImageMarkerForReportRpc() {
        val data = "data:image/jpeg;base64,QUJD"
        assertEquals(data, riderReportEvidence("__IMG__" + data))
        assertEquals(data, riderReportEvidence(data))
        for (format in listOf("jpeg", "png", "webp")) {
            assertEquals("data:image/$format;base64,QUJD", riderReportEvidence("data:image/$format;base64,QUJD"))
        }
    }

    @Test fun rejectsMissingUnsupportedRemoteAndMalformedEvidence() {
        for (data in listOf("", "https://example.test/a.jpg", "data:image/svg+xml;base64,QUJD",
            "data:image/jpeg;base64,", "data:image/jpeg;base64,QU JD", "data:image/jpeg;base64,<html>")) {
            try { riderReportEvidence(data); fail("Unexpected evidence accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun enforcesProductionCharacterLimitIncludingPrefix() {
        val prefix = "data:image/jpeg;base64,"
        val maximum = prefix + "A".repeat(900_000 - prefix.length)
        assertEquals(maximum, riderReportEvidence(maximum))
        try { riderReportEvidence(maximum + "A"); fail("Oversize evidence accepted") }
        catch (_: IllegalArgumentException) { }
    }
}
