package com.queuego.rider

import org.junit.Assert.*
import org.junit.Test

class RiderRegistrationFormTest {
    private val complete=RiderRegistrationForm(name="สมชาย",phone="081-234 5678",plate="กข 1234",province="บุรีรัมย์",district="เมืองบุรีรัมย์")
    private val docs=RIDER_DOCUMENTS.keys
    @Test fun requiresStrongPasswordAndNormalizedThaiPhone() {
        assertEquals("0812345678",complete.normalizedPhone)
        assertNull(complete.validate(0,"QueueGo1234!x",docs))
        assertNotNull(complete.validate(0,"123456789012",docs))
        assertNotNull(complete.copy(phone="1234567890").validate(0,"QueueGo1234!x",docs))
        assertNotNull(complete.copy(email="invalid@").validate(0,"QueueGo1234!x",docs))
    }
    @Test fun existingAuthenticatedDraftDoesNotRequirePersistedPassword() {
        assertNull(complete.validate(0,"",docs,true))
        assertNotNull(complete.validate(0,"",docs,false))
        assertNotNull(complete.validate(0,"",emptySet(),true))
    }
    @Test fun capacityAndEveryRequiredDocumentAreValidated() {
        assertNull(complete.validate(1,"",docs))
        listOf("NaN","Infinity","1001","-1","bad").forEach { capacity ->
            assertNotNull(complete.copy(capacity=capacity).validate(1,"",docs))
        }
        for (key in docs) assertNotNull(complete.validate(2,"",docs-key))
        assertNull(complete.validate(2,"",docs))
    }
}
