package com.queuego.rider

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

internal fun riderValidatedNetworkAvailable(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
internal fun rememberRiderValidatedNetwork(context: Context): State<Boolean> {
    val state = remember(context) { mutableStateOf(riderValidatedNetworkAvailable(context)) }

    DisposableEffect(context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@DisposableEffect onDispose { }

        val handler = Handler(Looper.getMainLooper())
        fun refresh() {
            state.value = riderValidatedNetworkAvailable(context)
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh()
            override fun onLost(network: Network) = refresh()
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) = refresh()
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

internal fun riderServerMutationAllowed(networkAvailable: Boolean): Boolean = networkAvailable

internal const val RIDER_NETWORK_GUARD_MESSAGE =
    "กำลังเชื่อมต่อใหม่ · งานยังอยู่ในเครื่อง กรุณารออินเทอร์เน็ตก่อนอัปเดตสถานะ"
