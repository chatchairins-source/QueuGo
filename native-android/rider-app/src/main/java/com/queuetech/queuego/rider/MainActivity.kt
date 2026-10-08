package com.queuetech.queuego.rider

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.queuetech.queuego.core.auth.AuthRepository
import com.queuetech.queuego.core.auth.SecureSessionStore
import com.queuetech.queuego.core.model.AppRole
import com.queuetech.queuego.core.network.QueueGoApi
import com.queuetech.queuego.core.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = AuthRepository(QueueGoApi(), SecureSessionStore(applicationContext))
        val vm = ViewModelProvider(
            this,
            NativeAuthViewModel.factory(AppRole.RIDER, repository),
        )[NativeAuthViewModel::class.java]

        setContent {
            QueueGoTheme {
                NativeAuthGate("QueueGo Rider", vm) { user, logout ->
                    NativeAccountHome("QueueGo Rider", user, "Native · Supabase Production", logout)
                }
            }
        }
    }
}
