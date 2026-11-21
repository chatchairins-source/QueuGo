package com.queuego.merchant

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic contract fixtures only: no merchant account or Production balance is accessed. */
class MerchantPosCreditSummaryTest {
    private fun result(kind: String = "merchant", enabled: Boolean = true) = JSONObject()
        .put("kind", kind)
        .put("enabled", enabled)
        .put("cash_minor", 12500L)
        .put("promo_minor", 250L)
        .put("held_minor", 350L)
        .put("available_minor", 12400L)

    @Test fun parsesRealMerchantCreditFieldsWithoutLocalBalanceCalculation() {
        val value = parsePosMerchantCreditSummary(result())
        assertTrue(value.enabled)
        assertEquals(12500L, value.cashMinor)
        assertEquals(250L, value.promoMinor)
        assertEquals(350L, value.heldMinor)
        assertEquals(12400L, value.availableMinor)
        assertFalse(parsePosMerchantCreditSummary(result(enabled = false)).enabled)
    }

    @Test fun rejectsWrongActorMissingOrInvalidServerNumbers() {
        assertThrows(IllegalArgumentException::class.java) {
            parsePosMerchantCreditSummary(result(kind = "rider"))
        }
        val missingBalance = result().apply { remove("available_minor") }
        assertThrows(IllegalArgumentException::class.java) {
            parsePosMerchantCreditSummary(missingBalance)
        }
        assertThrows(IllegalArgumentException::class.java) {
            parsePosMerchantCreditSummary(result().put("cash_minor", JSONObject.NULL))
        }
        assertThrows(IllegalStateException::class.java) {
            parsePosMerchantCreditSummary(result().put("promo_minor", "unknown"))
        }
    }

    @Test fun missingFeatureStatusIsNotAssumedEnabled() {
        val missing = result().apply { remove("enabled") }
        assertThrows(IllegalStateException::class.java) {
            parsePosMerchantCreditSummary(missing)
        }
    }
}
