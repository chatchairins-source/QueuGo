package com.queuego.customer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.queuego.shared.QueueGoRoleNativeApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QueueGoRoleNativeApp(
                expectedRole = "customer",
                appTitle = "QueueGo",
                roleLabel = "ลูกค้า"
            )
        }
    }
}
