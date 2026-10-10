package com.queuego.customer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var pushReferenceId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readPushIntent(intent)
        setContent {
            QueueGoCustomerApp(
                pushReferenceId = pushReferenceId,
                onPushConsumed = { pushReferenceId = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readPushIntent(intent)
    }

    private fun readPushIntent(intent: Intent?) {
        val reference = intent?.getStringExtra("queuego_push_reference_id")
            ?: intent?.getStringExtra("referenceId")
            ?: intent?.getStringExtra("reference_id")
        pushReferenceId = reference?.trim()?.takeIf { it.isNotEmpty() }
    }
}
