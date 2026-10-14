package com.queuego.rider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderAcceptanceModeTest {
    private fun eligible(
        enabled: Boolean = true,
        networkAvailable: Boolean = true,
        online: Boolean = true,
        hasActiveWork: Boolean = false,
        actionBusy: Boolean = false,
        secondsLeft: Int = 30,
        requiredMerchantCash: Double? = 300.0,
        cashLimitBaht: Double? = 500.0,
        multiShop: Boolean = false
    ) = riderAutoAcceptEligible(
        enabled,
        networkAvailable,
        online,
        hasActiveWork,
        actionBusy,
        secondsLeft,
        requiredMerchantCash,
        cashLimitBaht,
        multiShop
    )

    @Test fun autoAcceptIsOptInAndNeedsCashCoverage() {
        assertFalse(eligible(enabled = false))
        assertTrue(eligible())
        assertFalse(eligible(cashLimitBaht = null))
        assertFalse(eligible(requiredMerchantCash = null))
        assertFalse(eligible(requiredMerchantCash = 501.0, cashLimitBaht = 500.0))
        assertTrue(eligible(requiredMerchantCash = 500.0, cashLimitBaht = 500.0))
    }

    @Test fun autoAcceptFailsClosedForUnsafeOrStaleStates() {
        assertFalse(eligible(networkAvailable = false))
        assertFalse(eligible(online = false))
        assertFalse(eligible(hasActiveWork = true))
        assertFalse(eligible(actionBusy = true))
        assertFalse(eligible(multiShop = true))
        assertFalse(eligible(secondsLeft = RIDER_AUTO_ACCEPT_MIN_SECONDS_LEFT - 1))
    }

    @Test fun invalidCashValuesNeverAutoAccept() {
        assertFalse(eligible(requiredMerchantCash = Double.NaN))
        assertFalse(eligible(cashLimitBaht = Double.NaN))
        assertFalse(eligible(requiredMerchantCash = -1.0))
        assertFalse(eligible(cashLimitBaht = -1.0))
    }

    @Test fun acceptanceWindowKeepsRaceBufferBeforeExpiry() {
        assertTrue(RIDER_AUTO_ACCEPT_MIN_SECONDS_LEFT >= 2)
        assertTrue(eligible(secondsLeft = RIDER_AUTO_ACCEPT_MIN_SECONDS_LEFT))
    }
}
