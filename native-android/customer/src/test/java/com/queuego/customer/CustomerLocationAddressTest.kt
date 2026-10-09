package com.queuego.customer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomerLocationAddressTest {
    @Test
    fun formatsLongdoAddressInWebBlueprintOrder() {
        assertEquals(
            "12/3 ซอย 5 ถนนหลัก ในเมือง เมืองบุรีรัมย์ บุรีรัมย์ 31000",
            formatLongdoAddressParts(
                mapOf(
                    "house_number" to "12/3",
                    "soi" to "ซอย 5",
                    "road" to "ถนนหลัก",
                    "subdistrict" to "ในเมือง",
                    "district" to "เมืองบุรีรัมย์",
                    "province" to "บุรีรัมย์",
                    "postcode" to "31000"
                )
            )
        )
    }

    @Test
    fun ignoresBlankAddressComponents() {
        assertEquals(
            "บุรีรัมย์ 31000",
            formatLongdoAddressParts(
                mapOf(
                    "house_number" to "",
                    "province" to "บุรีรัมย์",
                    "postcode" to "31000"
                )
            )
        )
        assertNull(formatLongdoAddressParts(emptyMap()))
    }
}
