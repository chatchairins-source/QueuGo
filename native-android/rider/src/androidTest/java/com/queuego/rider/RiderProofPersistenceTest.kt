package com.queuego.rider

import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RiderProofPersistenceTest {
    @Test fun preparePendingProofForProcessKill() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.filesDir, "queuego-evidence").apply { mkdirs() }
        val file = File(dir, "proof-process-kill-test.jpg")
        val image = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.GRAY)
        file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        image.recycle()
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val store = RiderPendingProofStore(context)
        store.reserveCapture("process-kill-test", RIDER_PROOF_SLOT_DELIVERY, "test-order", "delivery", null, uri)
        assertNotNull(store.promoteCapture("process-kill-test", RIDER_PROOF_SLOT_DELIVERY))
        store.saveVerification("process-kill-test", RiderVerificationResume("test-order", "delivery", null, true))
        assertTrue(context.getSharedPreferences("proof-process-test", 0).edit()
            .putInt("seed_pid", android.os.Process.myPid()).commit())
    }

    @Test fun recoverPendingProofAfterProcessKill() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val seedPid = context.getSharedPreferences("proof-process-test", 0).getInt("seed_pid", 0)
        assertTrue(seedPid > 0)
        assertNotEquals("Must run after the original process died", seedPid, android.os.Process.myPid())
        val store = RiderPendingProofStore(context)
        try {
            val draft = store.loadConfirmedMatching("process-kill-test", RIDER_PROOF_SLOT_DELIVERY,
                "test-order", "delivery", null)
            assertNotNull(draft)
            val bitmap = context.contentResolver.openInputStream(android.net.Uri.parse(draft!!.uri))?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
            assertNotNull(bitmap)
            assertEquals(640, bitmap!!.width)
            bitmap.recycle()
            assertTrue(store.loadVerification("process-kill-test")!!.collection)
        } finally {
            store.clearConfirmedMatching("process-kill-test", RIDER_PROOF_SLOT_DELIVERY, "test-order", "delivery", null)
            store.clearVerification("process-kill-test")
            context.getSharedPreferences("proof-process-test", 0).edit().clear().commit()
        }
    }

    @Test fun savedCameraFileAndCashStageSurviveStoreRecreation() {
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
            first.saveVerification(user, RiderVerificationResume("test-order", "delivery", null, true))
            // Drop every store instance; re-read the same durable on-disk identity.
            val resumed = RiderPendingProofStore(context)
            assertEquals(uri.toString(), resumed.loadConfirmedMatching(user, RIDER_PROOF_SLOT_DELIVERY,
                "test-order", "delivery", null)?.uri)
            assertTrue(resumed.loadVerification(user)!!.collection)
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
