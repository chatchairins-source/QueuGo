package com.queuego.customer

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import org.junit.Assert.*
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertTextContains
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft

/** Isolated Android UI harness: real picker/Longdo GL, no account, backend writes or orders. */
class CustomerLocationP0RuntimeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun appCameraMovesDoNotBecomeUserDeliverySelections() {
        val ready = AtomicBoolean(false)
        val selections = AtomicInteger(0)
        val target = mutableStateOf<com.queuego.shared.QgMapPoint?>(null)
        val token = mutableStateOf(0)
        compose.setContent {
            com.queuego.shared.QgLongdoLocationPickerMap(
                initialPoint = null, recenterPoint = target.value, recenterToken = token.value,
                modifier = Modifier.fillMaxSize().testTag("camera-owner-map"),
                onCenterChanged = { selections.incrementAndGet() }, onReady = { ready.set(it) })
        }
        compose.waitUntil(30_000) { ready.get() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("Fallback camera became a delivery selection", 0, selections.get())
            target.value = com.queuego.shared.QgMapPoint(14.995, 103.103, 0)
            token.value = 1
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("App GPS recenter echoed as a user pan", 0, selections.get())
        }
        compose.onNodeWithTag("camera-owner-map").performTouchInput { swipeLeft(durationMillis = 300) }
        compose.waitUntil(10_000) { selections.get() > 0 }
    }

    @Test fun thirtyOpenGpsSaveCloseCyclesAndEarlyBackground() {
        val visible = mutableStateOf(false)
        val saved = AtomicReference<CustomerLocation?>()
        val gps = mutableStateOf<CustomerLocation?>(null)
        compose.setContent {
            if (visible.value) CustomerLocationPickerScreen(
                location = gps.value, address = "", busy = false,
                readGps = { CustomerLocation(14.995, 103.103, "") },
                resolveAddress = { _, _ -> "Isolated Android test address" },
                onSave = { saved.set(it); visible.value = false },
                onBack = { visible.value = false }
            )
        }
        repeat(30) { index ->
            compose.runOnIdle { gps.value = null; visible.value = true }
            // Exercise GL creation/disposal while the Activity pauses, including before mapReady.
            if (index % 3 == 0) {
                compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
                compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            }
            compose.onNodeWithText("ใช้ตำแหน่ง GPS ปัจจุบัน").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasText("กำลังอ่านตำแหน่ง GPS…"))
                    .fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithText("บ้านเลขที่ หมู่บ้าน ซอย ถนน หรือจุดสังเกต")
                .performScrollTo().performTextReplacement("Cycle $index manual address")
            if (index == 0 || index == 29) {
                compose.waitUntil(30_000) {
                    compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "แผนที่พร้อมใช้งาน"))
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.activityRule.scenario.onActivity { activity ->
                    val keyboard = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                    keyboard.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
                }
                compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "แผนที่พร้อมใช้งาน"))
                    .performScrollTo()
                val directory = File(compose.activity.filesDir, "p0-evidence").apply { mkdirs() }
                requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()) { "Android screenshot was unavailable" }.let { bitmap ->
                    File(directory, "picker-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
            compose.onNodeWithText("บันทึกที่อยู่นี้").performScrollTo().performClick()
            compose.runOnIdle {
                assertFalse("Picker did not close at cycle $index", visible.value)
                assertEquals("Cycle $index manual address", saved.get()?.address)
                assertEquals(14.995, requireNotNull(saved.get()).latitude, 0.000001)
            }
        }
    }

    @Test fun selectedDraftSurvivesSavedStateRestore() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CustomerLocationPickerScreen(
                location = CustomerLocation(14.995, 103.103, "Original"),
                address = "Original", busy = false,
                readGps = { CustomerLocation(14.995, 103.103, "") },
                resolveAddress = { _, _ -> "Generated" },
                onSave = {}, onBack = {})
        }
        compose.onNodeWithText("บ้านเลขที่ หมู่บ้าน ซอย ถนน หรือจุดสังเกต")
            .performScrollTo().performTextReplacement("Restored manual address")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("บ้านเลขที่ หมู่บ้าน ซอย ถนน หรือจุดสังเกต")
            .assertTextContains("Restored manual address")
    }

    @Test fun accountBuildInfoShowsExactCompiledSource() {
        compose.setContent {
            com.queuego.shared.QgTestBuildInfo("Customer", BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE, BuildConfig.GIT_SHA, BuildConfig.BUILT_UTC, BuildConfig.BUILD_TYPE)
        }
        compose.onNodeWithText("ข้อมูลเวอร์ชันทดสอบ").performClick()
        compose.onNodeWithText("Git SHA: " + BuildConfig.GIT_SHA, substring = true).assertExists()
        compose.onNodeWithText("versionCode: " + BuildConfig.VERSION_CODE, substring = true).assertExists()
    }
}
