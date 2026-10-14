package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Test

class RiderSupportPrefillTest {
    @Test fun appealPrefillUsesOnlyExistingSupportCategories() {
        assertEquals("other", riderSupportInitialCategory("other"))
        assertEquals("cash", riderSupportInitialCategory("cash"))
        assertEquals("missing_item", riderSupportInitialCategory("not-a-category"))
        assertEquals("missing_item", riderSupportInitialCategory(null))
    }
}
