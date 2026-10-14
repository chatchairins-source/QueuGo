package com.queuego.ridermaplibreosm

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflinePmTilesDownloadPolicyTest {
    @Test fun validRangeResponseAppendsAtExactExistingByte() {
        val plan = downloadWritePlan(
            existingBytes = 1_000L,
            statusCode = 206,
            contentLength = 4_000L,
            contentRange = "bytes 1000-4999/5000"
        )
        assertEquals(DownloadWriteMode.APPEND, plan.mode)
        assertEquals(5_000L, plan.totalBytes)
    }

    @Test fun serverIgnoringRangeRestartsInsteadOfDuplicatingBytes() {
        val plan = downloadWritePlan(
            existingBytes = 1_000L,
            statusCode = 200,
            contentLength = 5_000L,
            contentRange = null
        )
        assertEquals(DownloadWriteMode.RESTART, plan.mode)
        assertEquals(5_000L, plan.totalBytes)
    }

    @Test fun complete416CanFinalizeExistingPartial() {
        val plan = downloadWritePlan(
            existingBytes = 5_000L,
            statusCode = 416,
            contentLength = null,
            contentRange = "bytes */5000"
        )
        assertEquals(DownloadWriteMode.COMPLETE, plan.mode)
    }

    @Test fun mismatchedRangeForcesCleanRetry() {
        val plan = downloadWritePlan(
            existingBytes = 1_000L,
            statusCode = 206,
            contentLength = 4_500L,
            contentRange = "bytes 500-4999/5000"
        )
        assertEquals(DownloadWriteMode.RETRY, plan.mode)
    }

    @Test fun transientHttpErrorsRetryAndPermanentClientErrorsFail() {
        assertEquals(DownloadWriteMode.RETRY, downloadWritePlan(0, 503, null, null).mode)
        assertEquals(DownloadWriteMode.RETRY, downloadWritePlan(0, 429, null, null).mode)
        assertEquals(DownloadWriteMode.FAIL, downloadWritePlan(0, 404, null, null).mode)
    }
}
