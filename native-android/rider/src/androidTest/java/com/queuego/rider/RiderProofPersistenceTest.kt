package com.queuego.rider

import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RiderProofPersistenceTest {
    private fun stage(): String = InstrumentationRegistry.getArguments().getString("stage") ?: "photo_captured"

    private fun isolatedContext(): android.content.Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        return object : android.content.ContextWrapper(base) {
            override fun getApplicationContext(): android.content.Context = this
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences =
                base.getSharedPreferences(name + "_process_runtime_test", mode)
        }
    }

    private fun expectedJob(stage: String): RiderJob {
        val status = when (stage) {
            "navigating_merchant" -> "rider_assigned"
            "arrived_merchant" -> "preparing"
            "pickup_verification" -> "ready"
            "picked_up" -> "picked_up"
            else -> "in_progress"
        }
        return RiderJob(id = "test-order", orderNumber = "QT-1234", status = status,
            pickupAddress = "test pickup", pickupLat = 14.99, pickupLng = 103.10,
            deliveryAddress = "test delivery", deliveryLat = 14.98, deliveryLng = 103.11,
            deliveryFee = 25.0, marketOrderId = null,
            arrivedShopAt = if (stage == "navigating_merchant") null else "2026-10-14T00:00:00Z",
            arrivedCustomerAt = if (stage in setOf("arrived_customer", "photo_captured", "photo_captured")) "2026-10-14T00:01:00Z" else null,
            shopCash = 100.0, customerCash = 125.0)
    }

    @Test fun preparePendingProofForProcessKill() {
        val context = isolatedContext()
        val stage = stage()
        val job = expectedJob(stage)
        assertTrue(SessionStore(context).saveHomeSnapshot("process-kill-test",
            RiderSnapshot(online = true, activeJob = job, offeredJob = null), durable = true))
        assertTrue(context.getSharedPreferences("rider_navigation", 0).edit()
            .putBoolean(riderNavigationKey(job), stage in setOf("navigating_merchant", "navigating_customer"))
            .commit())
        val store = RiderPendingProofStore(context)
        if (stage in setOf("pickup_verification", "photo_captured", "photo_captured")) {
            store.saveVerification("process-kill-test", RiderVerificationResume("test-order",
                if (stage == "pickup_verification") "pickup" else "delivery", null, false))
        }
        if (stage in setOf("photo_captured", "photo_captured")) {
            val dir = File(context.filesDir, "queuego-evidence").apply { mkdirs() }
            val file = File(dir, "proof-process-kill-test.jpg")
            val image = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
            image.eraseColor(android.graphics.Color.GRAY)
            file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            image.recycle()
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            store.reserveCapture("process-kill-test", RIDER_PROOF_SLOT_DELIVERY, "test-order", "delivery", null, uri)
            assertNotNull(store.promoteCapture("process-kill-test", RIDER_PROOF_SLOT_DELIVERY))
        }
        assertTrue(context.getSharedPreferences("proof-process-test", 0).edit()
            .putInt("seed_pid", android.os.Process.myPid()).putString("stage", stage).commit())
    }

    @Test fun recoverPendingProofAfterProcessKill() {
        val context = isolatedContext()
        val stage = stage()
        val meta = context.getSharedPreferences("proof-process-test", 0)
        val seedPid = meta.getInt("seed_pid", 0)
        assertTrue(seedPid > 0)
        assertNotEquals("Must run after the original process died", seedPid, android.os.Process.myPid())
        assertEquals(stage, meta.getString("stage", null))
        val store = RiderPendingProofStore(context)
        try {
            val snapshot = SessionStore(context).loadHomeSnapshot("process-kill-test")
            assertEquals(expectedJob(stage), snapshot!!.activeJob)
            assertNull(SessionStore(context).loadHomeSnapshot("another-rider"))
            assertEquals(stage in setOf("navigating_merchant", "navigating_customer"),
                context.getSharedPreferences("rider_navigation", 0).getBoolean(riderNavigationKey(snapshot.activeJob!!), false))
            val workflow = store.loadVerification("process-kill-test")
            if (stage == "pickup_verification") assertEquals("pickup", workflow!!.mode)
            if (stage in setOf("photo_captured", "photo_captured")) {
                                val draft = store.loadConfirmedMatching("process-kill-test", RIDER_PROOF_SLOT_DELIVERY,
                    "test-order", "delivery", null)
                assertNotNull(draft)
                val bitmap = context.contentResolver.openInputStream(android.net.Uri.parse(draft!!.uri))?.use {
                    android.graphics.BitmapFactory.decodeStream(it)
                }
                assertNotNull(bitmap)
                assertEquals(640, bitmap!!.width)
                bitmap.recycle()
            } else {
                assertNull(store.loadConfirmedMatching("process-kill-test", RIDER_PROOF_SLOT_DELIVERY, "test-order", "delivery", null))
            }
        } finally {
            store.clearConfirmedMatching("process-kill-test", RIDER_PROOF_SLOT_DELIVERY, "test-order", "delivery", null)
            store.clearVerification("process-kill-test")
            context.getSharedPreferences("queuego_rider_native", 0).edit().clear().commit()
            context.getSharedPreferences("rider_navigation", 0).edit().clear().commit()
            meta.edit().clear().commit()
        }
    }

    @Test fun savedCameraFileAndSingleProofWorkflowSurviveStoreRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val user = "proof-persistence-test"
        val dir = File(context.filesDir, "queuego-evidence").apply { mkdirs() }
        val file = File.createTempFile("proof-runtime-test-", ".jpg", dir)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val first = RiderPendingProofStore(context)
        try {
            first.reserveCapture(user, RIDER_PROOF_SLOT_DELIVERY, "test-order", "delivery", null, uri)
            val image = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
            image.eraseColor(android.graphics.Color.GRAY)
            file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            image.recycle()
            assertNotNull(RiderPendingProofStore(context).promoteCapture(user, RIDER_PROOF_SLOT_DELIVERY))
            first.saveVerification(user, RiderVerificationResume("test-order", "delivery", null))
            // Drop every store instance; re-read the same durable on-disk identity.
            val resumed = RiderPendingProofStore(context)
            assertEquals(uri.toString(), resumed.loadConfirmedMatching(user, RIDER_PROOF_SLOT_DELIVERY,
                "test-order", "delivery", null)?.uri)
            assertEquals("delivery", resumed.loadVerification(user)!!.mode)
            assertNull(resumed.loadConfirmedMatching(user, RIDER_PROOF_SLOT_DELIVERY, "another-order", "delivery", null))
            // Clearing Android cache cannot remove the confirmed filesDir photo.
            File(context.cacheDir, "queuego-evidence").deleteRecursively()
            assertTrue(file.exists())
            resumed.clearConfirmedMatching(user, RIDER_PROOF_SLOT_DELIVERY, "test-order", "delivery", null)
            assertFalse(file.exists())
        } finally {
            first.cancelCapture(user, RIDER_PROOF_SLOT_DELIVERY)
            first.clearConfirmedMatching(user, RIDER_PROOF_SLOT_DELIVERY, "test-order", "delivery", null)
            first.clearVerification(user)
            file.delete()
        }
    }
}
