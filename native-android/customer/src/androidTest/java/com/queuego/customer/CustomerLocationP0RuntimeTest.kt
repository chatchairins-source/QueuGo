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
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/** Isolated Android UI harness: real picker/Longdo GL, no account, backend writes or orders. */
class CustomerLocationP0RuntimeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun thirtyOpenGpsSaveCloseCyclesAndEarlyBackground() {
        val visible = mutableStateOf(false)
        val saved = AtomicReference<CustomerLocation?>()
        val gps = mutableStateOf<CustomerLocation?>(null)
        compose.setContent {
            if (visible.value) CustomerLocationPickerScreen(
                location = gps.value, address = "", busy = false,
                onGps = { gps.value = CustomerLocation(14.995, 103.103, "") },
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
            compose.onNodeWithText("บ้านเลขที่ หมู่บ้าน ซอย ถนน หรือจุดสังเกต")
                .performScrollTo().performTextReplacement("Cycle $index manual address")
            compose.onNodeWithText("บันทึกที่อยู่นี้").performScrollTo().performClick()
            compose.runOnIdle {
                assertFalse("Picker did not close at cycle $index", visible.value)
                assertEquals("Cycle $index manual address", saved.get()?.address)
                assertEquals(14.995, requireNotNull(saved.get()).latitude, 0.000001)
            }
        }
    }
}
