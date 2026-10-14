package com.queuego.rider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderEvidenceQualityTest {
    @Test fun normalCameraPhotoIsAccepted() {
        val result = riderEvidenceQuality(1920, 1080, 2L * 1024L * 1024L)
        assertTrue(result.acceptable)
        assertNull(result.message)
    }

    @Test fun lowResolutionPhotoRequiresRetake() {
        val result = riderEvidenceQuality(320, 240, 200_000L)
        assertFalse(result.acceptable)
        assertTrue(result.message.orEmpty().contains("ความละเอียดต่ำ"))
    }

    @Test fun oversizedPhotoIsRejectedBeforeUpload() {
        val result = riderEvidenceQuality(4032, 3024, 6L * 1024L * 1024L)
        assertFalse(result.acceptable)
        assertTrue(result.message.orEmpty().contains("5 MB"))
    }

    @Test fun unreadableDimensionsRequireRetake() {
        val result = riderEvidenceQuality(0, 0, null)
        assertFalse(result.acceptable)
        assertTrue(result.message.orEmpty().contains("เปิดรูปไม่สำเร็จ"))
    }

    @Test fun unknownFileSizeDoesNotBlockOtherwiseValidPhoto() {
        val result = riderEvidenceQuality(1280, 720, null)
        assertTrue(result.acceptable)
    }
}
