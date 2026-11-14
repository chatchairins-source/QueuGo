package com.queuego.rider

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class RiderMoneyTest {
    @Test fun offerProofAndSuccessPreserveFractionalBahtAndGrouping() {
        assertEquals("฿125.5", riderMoneyBaht(125.5))
        assertEquals("฿1,234.567", riderMoneyBaht(1234.567))
        assertEquals("฿1,234.568", riderMoneyBaht(1234.5675))
        assertEquals("฿0", riderMoneyBaht(0.0))
        assertEquals("฿-25.5", riderMoneyBaht(-25.5))
    }

    @Test fun ledgerMatchesWebTwoDecimalHalfUpFormatting() {
        assertEquals("฿1,234.50", riderMoneyBaht(1234.5, true))
        assertEquals("฿1.01", riderMoneyBaht(1.005, true))
        assertEquals("฿0.00", riderMoneyBaht(0.0, true))
        assertEquals("฿-25.50", riderMoneyBaht(-25.5, true))
    }

    @Test fun missingInvalidAndDeviceLocaleDoNotInventAmounts() {
        assertEquals("฿—", riderMoneyBaht(null))
        assertEquals("฿—", riderMoneyBaht(Double.NaN))
        assertEquals("฿—", riderMoneyBaht(Double.POSITIVE_INFINITY))
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("฿1,234.50", riderMoneyBaht(1234.5, true))
        } finally { Locale.setDefault(previous) }
    }
}
