package com.queuetech.queuego.rider

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.queuetech.queuego.core.auth.ActiveSessionCheck
import com.queuetech.queuego.core.auth.AuthRepository
import com.queuetech.queuego.core.auth.DeviceIdentityStore
import com.queuetech.queuego.core.auth.SecureSessionStore
import com.queuetech.queuego.core.model.AppRole
import com.queuetech.queuego.core.network.QueueGoApi
import com.queuetech.queuego.core.ui.NativeAccountHome
import com.queuetech.queuego.core.ui.NativeAuthGate
import com.queuetech.queuego.core.ui.NativeAuthViewModel
import com.queuetech.queuego.core.ui.QueueGoTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var authViewModel: NativeAuthViewModel
    private var sessionGuardJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = AuthRepository(
            api = QueueGoApi(),
            sessionStore = SecureSessionStore(applicationContext),
            deviceIdentityStore = DeviceIdentityStore(applicationContext),
        )
        authViewModel = ViewModelProvider(
            this,
            NativeAuthViewModel.factory(AppRole.RIDER, repository),
        )[NativeAuthViewModel::class.java]

        setContent {
            QueueGoTheme {
                NativeAuthGate("QueueGo Rider", authViewModel) { user, logout ->
                    NativeAccountHome(
                        "QueueGo Rider",
                        user,
                        "Native · Supabase Production",
                        logout,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        sessionGuardJob?.cancel()
        sessionGuardJob = lifecycleScope.launch {
            while (isActive) {
                if (authViewModel.verifyRiderSession() == ActiveSessionCheck.REPLACED) break
                delay(SESSION_GUARD_MS)
            }
        }
    }

    override fun onStop() {
        sessionGuardJob?.cancel()
        sessionGuardJob = null
        super.onStop()
    }

    private companion object {
        const val SESSION_GUARD_MS = 25_000L
    }
}
