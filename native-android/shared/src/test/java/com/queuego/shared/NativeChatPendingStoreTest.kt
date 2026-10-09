package com.queuego.shared

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NativeChatPendingStoreTest {
    private fun withRoot(test: (File) -> Unit) {
        val root = Files.createTempDirectory("chat-pending-test").toFile()
        try { test(root) } finally { root.deleteRecursively() }
    }

    @Test fun reopenedStoreRecoversOriginalTextAndIdBeforeNewInput() = withRoot { root ->
        val sent = NativeChatPendingStore(root, "user", "order").prepare("เดิม")
        val reopened = NativeChatPendingStore(root, "user", "order")
        assertEquals(sent, reopened.load())
        assertEquals(sent, reopened.prepare("พิมพ์ใหม่"))
        reopened.clear()
        assertNull(reopened.load())
        assertNotEquals(sent.id, reopened.prepare("เดิม").id)
    }

    @Test fun imageProtocolPayloadSurvivesReopenAndScopesAreIsolated() = withRoot { root ->
        val payload = "__IMG__data:image/jpeg;base64," + "A".repeat(100_000)
        val first = NativeChatPendingStore(root, "user", "order").prepare(payload)
        assertEquals(first, NativeChatPendingStore(root, "user", "order").load())
        assertNull(NativeChatPendingStore(root, "another-user", "order").load())
        assertNull(NativeChatPendingStore(root, "user", "another-order").load())
    }

    @Test fun corruptFileCannotBecomeAReplacementMessage() = withRoot { root ->
        val store = NativeChatPendingStore(root, "user", "order")
        store.prepare("เดิม")
        root.walkTopDown().first { it.extension == "json" }.writeText("broken")
        assertThrows(Exception::class.java) { store.prepare("ใหม่") }
        assertEquals("broken", root.walkTopDown().first { it.extension == "json" }.readText())
    }

    @Test fun writeFailureDoesNotProduceASendableRequest() = withRoot { root ->
        val unavailable = File(root, "file").apply { writeText("not a directory") }
        assertThrows(Exception::class.java) { NativeChatPendingStore(unavailable, "user", "order").prepare("ข้อความ") }
    }

    @Test fun transientHttpFailuresKeepPendingLikeWeb() {
        listOf(408, 409, 429, 500, 503).forEach { assertFalse(nativeChatPermanentFailure(it)) }
        listOf(400, 401, 403, 404, 422).forEach { assertTrue(nativeChatPermanentFailure(it)) }
    }
}
