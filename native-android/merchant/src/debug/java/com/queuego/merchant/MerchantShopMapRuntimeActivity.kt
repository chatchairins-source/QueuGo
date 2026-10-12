package com.queuego.merchant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.queuego.shared.QgLongdoLocationPickerMap
import com.queuego.shared.QueueGoTheme
import java.util.concurrent.CountDownLatch

/**
 * Instrumentation-only mount of the exact native Longdo picker used by
 * Merchant "แก้ไขข้อมูลร้าน". It creates no session, shop, order or fake business data.
 */
class MerchantShopMapRuntimeActivity : ComponentActivity() {
    val mapReady = CountDownLatch(1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QueueGoTheme {
                QgLongdoLocationPickerMap(
                    initialPoint = null,
                    recenterPoint = null,
                    recenterToken = 0,
                    modifier = Modifier.fillMaxSize(),
                    onCenterChanged = {},
                    onReady = { ready -> if (ready) mapReady.countDown() }
                )
            }
        }
    }
}
