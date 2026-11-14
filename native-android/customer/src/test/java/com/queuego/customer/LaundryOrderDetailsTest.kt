package com.queuego.customer

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LaundryOrderDetailsTest {
    private fun row() = JSONObject().put("id", "11111111-1111-4111-8111-111111111111")
        .put("status", "at_hub").put("pricing_type_snapshot", "per_kg")
        .put("unit_price_snapshot", 12.50).put("estimated_quantity", 3)
        .put("estimated_amount", 37.50).put("pickup_fee_snapshot", 10.25).put("return_fee_snapshot", 11.50)
    @Test fun fractionalSnapshotFeesAndLegacyTotalFallbackArePreserved() {
        val parsed = parseLaundryOrderDetails(row())
        assertEquals(59.25, parsed.summary.estimatedTotal!!, 0.0)
        assertEquals(12.50, parsed.unitPrice!!, 0.0)
        assertFalse(parsed.actualQuantity)
        assertEquals(3.0, parsed.quantity!!, 0.0)
    }
    @Test fun actualZeroAndFinalZeroOverrideEstimatedValues() {
        val parsed = parseLaundryOrderDetails(row().put("actual_quantity", 0).put("final_amount", 0).put("final_total_amount", 0))
        assertTrue(parsed.actualQuantity)
        assertEquals(0.0, parsed.quantity!!, 0.0)
        assertEquals(0.0, parsed.serviceAmount!!, 0.0)
        assertEquals(0.0, parsed.summary.finalTotal!!, 0.0)
    }
    @Test fun legacyKilogramsAndExplicitDeliverySnapshotTakePrecedence() {
        val parsed = parseLaundryOrderDetails(row().put("actual_kg", 2.25).put("delivery_fee_total_snapshot", 30))
        assertTrue(parsed.actualQuantity)
        assertEquals(2.25, parsed.quantity!!, 0.0)
        assertEquals(67.50, parsed.summary.estimatedTotal!!, 0.0)
    }
}
