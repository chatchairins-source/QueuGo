package com.queuego.rider

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import java.util.concurrent.CountDownLatch

/** Mounts the actual Home map without creating any authentication or business data. */
class RiderMapRuntimeActivity : ComponentActivity() {
    val mapReady = CountDownLatch(1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RiderLongdoMap(
                modifier = Modifier.fillMaxSize(), job = null, laundryJob = null,
                marketPickups = emptyList(), recenterSignal = 0,
                onMapReady = { ready -> if (ready) mapReady.countDown() }
            )
        }
    }
}
