package com.queuego.merchant

import java.nio.file.Files
import java.util.UUID
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class MerchantPosPendingStoreTest {
    private fun intent() = PosEditIntent(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
        "TAKEAWAY", null, UUID.randomUUID().toString(), 1, "ไม่เผ็ด")

    @Test fun acknowledgementAcceptsRealUuidShapesAndRejectsWrongOrMalformedResults() {
        val id = UUID.randomUUID().toString()
        for (raw in listOf(id, JSONObject().put("pos_edit_bill_once", id),
            JSONArray().put(JSONObject().put("pos_create_bill_once", id)))) {
            assertEquals(id, merchantPosReplayOrderId(raw, id))
        }
        for (raw in listOf<Any>(false, "null", "true", JSONObject(), UUID.randomUUID().toString())) {
            assertThrows(IllegalStateException::class.java) { merchantPosReplayOrderId(raw, id) }
        }
    }

    @Test fun reopeningStoreReusesOriginalRequestAndWirePayload() {
        val root = Files.createTempDirectory("pos-pending-test").toFile()
        try {
            val original = intent()
            MerchantPosPendingStore(root).prepare("actor", original)
            val restored = MerchantPosPendingStore(root).prepare("actor", original.copy(requestId = UUID.randomUUID().toString()))
            assertEquals(original, restored)
            assertEquals(original.requestId, restored.body().getString("p_request"))
            assertTrue(restored.body().has("p_table"))
            assertTrue(restored.body().isNull("p_table"))
        } finally { root.deleteRecursively() }
    }

    @Test fun newBillSurvivesProcessRecreationWithTheSameRequest() {
        val root = Files.createTempDirectory("pos-pending-test").toFile()
        try {
            val original = intent().copy(billId = null)
            MerchantPosPendingStore(root).prepare("actor", original)
            val restored = MerchantPosPendingStore(root).load("actor")!!
            assertEquals(original, restored)
            assertTrue(restored.body().isNull("p_order"))
            assertThrows(IllegalArgumentException::class.java) {
                MerchantPosPendingStore(root).prepare("other", original.copy(quantity = -1))
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun changedMutationCannotReplaceUnresolvedRequest() {
        val root = Files.createTempDirectory("pos-pending-test").toFile()
        try {
            val store = MerchantPosPendingStore(root)
            val original = intent()
            store.prepare("actor", original)
            assertThrows(IllegalStateException::class.java) { store.prepare("actor", original.copy(quantity = -1)) }
            assertThrows(IllegalStateException::class.java) { store.prepare("actor", original.copy(note = "different")) }
            assertEquals(original, store.load("actor"))
        } finally { root.deleteRecursively() }
    }

    @Test fun actorIsolationAndAcknowledgementCannotClearAnotherRequest() {
        val root = Files.createTempDirectory("pos-pending-test").toFile()
        try {
            val store = MerchantPosPendingStore(root)
            val original = intent()
            store.prepare("actor", original)
            assertNull(store.load("other"))
            assertThrows(IllegalStateException::class.java) { store.clear("actor", UUID.randomUUID().toString()) }
            assertEquals(original, store.load("actor"))
            store.clear("actor", original.requestId)
            assertNull(MerchantPosPendingStore(root).load("actor"))
        } finally { root.deleteRecursively() }
    }

    @Test fun corruptPendingFileStaysBlockedInsteadOfGeneratingANewRequest() {
        val root = Files.createTempDirectory("pos-pending-test").toFile()
        try {
            val store = MerchantPosPendingStore(root)
            store.prepare("actor", intent())
            root.listFiles()!!.single().writeText("invalid JSON")
            assertThrows(Exception::class.java) { store.prepare("actor", intent()) }
            assertEquals("invalid JSON", root.listFiles()!!.single().readText())
        } finally { root.deleteRecursively() }
    }
}
