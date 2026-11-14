package com.queuego.merchant

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class MerchantPosCashMovementStoreTest {
    // Persistent test backing shared by separately constructed stores, with explicit disk failures.
    private class Preferences {
        val values = mutableMapOf<String, String>()
        var commitSucceeds = true
        val prefs: SharedPreferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> values[args!![0] as String] ?: args[1]
                "contains" -> values.containsKey(args!![0] as String)
                "edit" -> editor()
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences
        private fun editor(): SharedPreferences.Editor {
            val changes = mutableMapOf<String, String?>()
            return Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)
            ) { proxy, method, args ->
                when (method.name) {
                    "putString" -> { changes[args!![0] as String] = args[1] as String?; proxy }
                    "remove" -> { changes[args!![0] as String] = null; proxy }
                    "commit" -> {
                        if (commitSucceeds) changes.forEach { (key, value) ->
                            if (value == null) values.remove(key) else values[key] = value
                        }
                        commitSucceeds
                    }
                    else -> error("Unexpected editor call: ${method.name}")
                }
            } as SharedPreferences.Editor
        }
    }
    private fun request() = PosCashMovementPending("shop-a", "actor-a", UUID.randomUUID().toString(),
        "cash_in", 100.25, "เงินทอนต้นกะ")

    @Test fun reopeningAndOtherTabRestoreSameRequestAndCannotReplaceIt() {
        val backing = Preferences()
        val original = request()
        assertTrue(MerchantPosCashMovementStore(backing.prefs).save(original))
        val otherTab = MerchantPosCashMovementStore(backing.prefs)
        assertEquals(original, otherTab.load("shop-a", "actor-a"))
        assertFalse(otherTab.save(original.copy(requestId = UUID.randomUUID().toString())))
        assertEquals(original, otherTab.load("shop-a", "actor-a"))
        assertNull(otherTab.load("shop-a", "other-actor"))
        assertNull(otherTab.load("other-shop", "actor-a"))
    }

    @Test fun onlyMatchingServerAcknowledgementCanClearPersistedRequest() {
        val backing = Preferences()
        val store = MerchantPosCashMovementStore(backing.prefs)
        val original = request()
        assertTrue(store.save(original))
        assertFalse(store.clear(original.copy(amount = 101.25)))
        backing.commitSucceeds = false
        assertFalse(store.clear(original))
        assertEquals(original, store.load("shop-a", "actor-a"))
        backing.commitSucceeds = true
        assertTrue(store.clear(original))
        assertNull(store.load("shop-a", "actor-a"))
    }

    @Test fun corruptPendingBytesCannotBeReplacedByNewFinancialIntent() {
        val backing = Preferences()
        val original = request()
        val store = MerchantPosCashMovementStore(backing.prefs)
        assertTrue(store.save(original))
        val key = backing.values.keys.single()
        backing.values[key] = "unreadable"
        assertNull(store.load("shop-a", "actor-a"))
        assertFalse(store.save(request()))
        assertEquals("unreadable", backing.values[key])
    }

    @Test fun failedDiskCommitDoesNotReportDurablePreparation() {
        val backing = Preferences().apply { commitSucceeds = false }
        val store = MerchantPosCashMovementStore(backing.prefs)
        assertFalse(store.save(request()))
        assertNull(store.load("shop-a", "actor-a"))
    }
}
