package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiderProfilePhotoTest {
    @Test fun acceptsOnlySupportedProfileImageTypes() {
        assertEquals("image/jpeg", riderProfilePhotoMimeType("image/jpeg"))
        assertEquals("image/jpeg", riderProfilePhotoMimeType("IMAGE/JPG"))
        assertEquals("image/png", riderProfilePhotoMimeType("image/png"))
        assertEquals("image/webp", riderProfilePhotoMimeType("image/webp"))
        assertNull(riderProfilePhotoMimeType("application/pdf"))
        assertNull(riderProfilePhotoMimeType(null))
    }
}
