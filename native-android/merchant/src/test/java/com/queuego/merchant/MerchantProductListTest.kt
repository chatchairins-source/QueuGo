package com.queuego.merchant

import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantProductListTest {
    @Test fun foodAndCafeUseMenuCopy() {
        assertEquals("เพิ่มเมนู", merchantProductAddLabel("food"))
        assertEquals("เพิ่มเมนู", merchantProductAddLabel("cafe"))
    }

    @Test fun productShopsKeepProductCopy() {
        assertEquals("เพิ่มสินค้า", merchantProductAddLabel("shopping"))
        assertEquals("เพิ่มสินค้า", merchantProductAddLabel("other"))
        assertEquals("เพิ่มสินค้า", merchantProductAddLabel("market"))
    }

    @Test fun foodCategoryMatchingIsCaseInsensitive() {
        assertEquals("เพิ่มเมนู", merchantProductAddLabel("Food"))
        assertEquals("เพิ่มเมนู", merchantProductAddLabel("CAFE"))
    }
}
