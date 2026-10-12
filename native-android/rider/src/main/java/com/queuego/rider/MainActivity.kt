package com.queuego.rider

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class NativePushLaunchTarget(
    val referenceId: String,
    val type: String,
    val eventId: Long
)

class MainActivity : ComponentActivity() {
    private var pushTarget by mutableStateOf<NativePushLaunchTarget?>(null)
    private var pushEventId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pushEventId = savedInstanceState?.getLong("rider_push_event_id") ?: 0L
        savedInstanceState?.getString("rider_push_reference")?.let { reference ->
            pushTarget = NativePushLaunchTarget(reference, savedInstanceState.getString("rider_push_type").orEmpty(), pushEventId)
        }
        acceptPushIntent(intent)
        setContent { QueueGoRiderApp(pushTarget = pushTarget) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("rider_push_event_id", pushEventId)
        pushTarget?.let { target ->
            outState.putString("rider_push_reference", target.referenceId)
            outState.putString("rider_push_type", target.type)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptPushIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        RiderReturnService.stop(this)
    }

    private fun acceptPushIntent(source: Intent?) {
        if (source == null) return
        val referenceId = (
            source.getStringExtra("queuego_push_reference_id")
                ?: source.getStringExtra("referenceId")
                ?: source.getStringExtra("reference_id")
            ).orEmpty().trim()
        val type = (
            source.getStringExtra("queuego_push_type")
                ?: source.getStringExtra("type")
            ).orEmpty().trim()
        source.removeExtra("queuego_push_reference_id")
        source.removeExtra("queuego_push_type")
        source.removeExtra("referenceId")
        source.removeExtra("reference_id")
        source.removeExtra("type")
        if (referenceId.isBlank() && type.isBlank()) return
        pushEventId += 1
        pushTarget = NativePushLaunchTarget(referenceId, type, pushEventId)
    }
}
