package com.queuetech.queuego.rider

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.queuetech.queuego.core.auth.ActiveSessionCheck
import com.queuetech.queuego.core.auth.AuthRepository
import com.queuetech.queuego.core.auth.DeviceIdentityStore
import com.queuetech.queuego.core.auth.SecureSessionStore
import com.queuetech.queuego.core.model.AppRole
import com.queuetech.queuego.core.network.QueueGoApi
import com.queuetech.queuego.core.ui.NativeAuthGate
import com.queuetech.queuego.core.ui.NativeAuthViewModel
import com.queuetech.queuego.core.ui.QueueGoTheme
import java.net.URLEncoder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var authViewModel: NativeAuthViewModel
    private lateinit var riderViewModel: RiderViewModel
    private var sessionGuardJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val api = QueueGoApi()
        val sessionStore = SecureSessionStore(applicationContext)
        val authRepository = AuthRepository(
            api = api,
            sessionStore = sessionStore,
            deviceIdentityStore = DeviceIdentityStore(applicationContext),
        )
        val riderRepository = RiderRepository(
            api = api,
            sessionStore = sessionStore,
        )

        authViewModel = ViewModelProvider(
            this,
            NativeAuthViewModel.factory(AppRole.RIDER, authRepository),
        )[NativeAuthViewModel::class.java]

        riderViewModel = ViewModelProvider(
            this,
            RiderViewModel.factory(
                repository = riderRepository,
                locationProvider = RiderLocationProvider(applicationContext),
            ),
        )[RiderViewModel::class.java]

        setContent {
            QueueGoTheme {
                NativeAuthGate("QueueGo Rider", authViewModel) { user, logout ->
                    DisposableEffect(user.id) {
                        riderViewModel.bind(user)
                        onDispose { riderViewModel.unbind() }
                    }

                    RiderHomeScreen(
                        viewModel = riderViewModel,
                        onLogout = {
                            riderViewModel.unbind()
                            logout()
                        },
                        onNavigate = ::openNavigation,
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

    private fun openNavigation(latitude: Double, longitude: Double, label: String) {
        val google = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=$latitude,$longitude&mode=d"),
        ).apply {
            setPackage("com.google.android.apps.maps")
        }

        try {
            startActivity(google)
        } catch (_: ActivityNotFoundException) {
            val encodedLabel = URLEncoder.encode(
                label.ifBlank { "ปลายทาง" },
                Charsets.UTF_8.name(),
            )
            val fallback = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($encodedLabel)"),
            )
            startActivity(fallback)
        }
    }

    private companion object {
        const val SESSION_GUARD_MS = 25_000L
    }
}
