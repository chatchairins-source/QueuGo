package com.queuego.ridermaplibreosm

import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMapPackageTest {
    @Test fun packageIdRejectsPathTraversal() {
        var rejected = false
        try {
            OfflineMapPackage("../buriram", "https://example.com/buriram.pmtiles")
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test(expected = IllegalArgumentException::class)
    fun downloadMustUseHttps() {
        OfflineMapPackage("buriram", "http://example.com/buriram.pmtiles")
    }
}
