package com.queuego.rider

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun riderNetworkAvailable(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
internal fun rememberRiderNetworkAvailable(context: Context): State<Boolean> {
    val state = remember(context) { mutableStateOf(riderNetworkAvailable(context)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (manager == null) return@DisposableEffect onDispose { }

        val handler = Handler(Looper.getMainLooper())
        fun refresh() {
            state.value = riderNetworkAvailable(context)
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh()
            override fun onLost(network: Network) = refresh()
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = refresh()
        }

        runCatching { manager.registerDefaultNetworkCallback(callback, handler) }
            .onFailure { refresh() }

        refresh()
        onDispose {
            runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }
    return state
}

internal fun riderOfflineContinuityActive(
    activeWork: Boolean,
    networkAvailable: Boolean
): Boolean = activeWork && !networkAvailable

internal fun riderWorkMutationAllowed(networkAvailable: Boolean): Boolean = networkAvailable

internal const val RIDER_OFFLINE_ACTION_MESSAGE =
    "อินเทอร์เน็ตขาดหาย · งานยังอยู่ใน QueueGo กรุณารอให้เชื่อมต่อก่อนอัปเดตสถานะ"

@Composable
internal fun RiderOfflineContinuityBanner(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E8)),
        border = BorderStroke(1.dp, Color(0xFFFFD28A))
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(
                "กำลังใช้งานแบบออฟไลน์",
                color = Color(0xFF6B4710),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "งานและข้อมูลล่าสุดยังอยู่บนเครื่อง · ดูที่อยู่และเปิดนำทางได้ แต่การอัปเดตสถานะจะรอจนอินเทอร์เน็ตกลับมา",
                color = Color(0xFF7A6545),
                fontSize = 8.5.sp,
                lineHeight = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
