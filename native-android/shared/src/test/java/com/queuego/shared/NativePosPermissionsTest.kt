package com.queuego.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePosPermissionsTest {
    @Test fun roleDefaultsStaySeparated() {
        val roles = listOf("WAITER", "CASHIER", "KITCHEN")
        val expected = mapOf(
            "receive_order" to "WAITER", "send_kitchen" to "WAITER",
            "serve_order" to "WAITER", "cook_order" to "KITCHEN",
            "ready_order" to "KITCHEN", "close_bill" to "CASHIER"
        )
        for ((permission, allowedRole) in expected) for (role in roles) {
            val actual = nativePosPermissionAllowed(true, role, permission, emptySet(), emptySet())
            if (role == allowedRole) assertTrue(actual) else assertFalse(actual)
        }
    }
    @Test fun explicitDenialOverridesRoleAndGrant() {
        assertFalse(nativePosPermissionAllowed(true, "CASHIER", "close_bill", emptySet(), setOf("close_bill")))
        assertFalse(nativePosPermissionAllowed(true, "WAITER", "refund", setOf("refund"), setOf("refund")))
    }
    @Test fun inactiveStaffCannotUseDefaultsOrGrants() {
        assertFalse(nativePosPermissionAllowed(false, "WAITER", "receive_order", emptySet(), emptySet()))
        assertFalse(nativePosPermissionAllowed(false, "CASHIER", "refund", setOf("refund"), emptySet()))
    }
    @Test fun privilegedActionsRequireExplicitGrant() {
        for (permission in listOf("refund", "cancel_bill", "discount", "edit_price", "manage_staff")) {
            assertFalse(nativePosPermissionAllowed(true, "CASHIER", permission, emptySet(), emptySet()))
            assertTrue(nativePosPermissionAllowed(true, "CASHIER", permission, setOf(permission), emptySet()))
        }
        assertFalse(nativePosPermissionAllowed(true, "UNKNOWN", "close_bill", emptySet(), emptySet()))
    }
}
