package com.queuego.customer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomerLocationAddressTest {
    @Test
    fun formatsLongdoAddressInWebBlueprintOrder() {
        val raw = """{
          "house_number":"12/3",
          "soi":"ซอย 5",
          "road":"ถนนหลัก",
          "subdistrict":"ในเมือง",
          "district":"เมืองบุรีรัมย์",
          "province":"บุรีรัมย์",
          "postcode":"31000"
        }"""
        assertEquals(
            "12/3 ซอย 5 ถนนหลัก ในเมือง เมืองบุรีรัมย์ บุรีรัมย์ 31000",
            formatLongdoAddress(raw)
        )
    }

    @Test
    fun ignoresBlankComponentsAndInvalidPayload() {
        assertEquals(
            "บุรีรัมย์ 31000",
            formatLongdoAddress("""{"house_number":"","province":"บุรีรัมย์","postcode":"31000"}""")
        )
        assertNull(formatLongdoAddress("not-json"))
        assertNull(formatLongdoAddress("{}"))
    }
}
