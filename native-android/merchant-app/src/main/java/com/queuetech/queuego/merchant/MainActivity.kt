package com.queuetech.queuego.merchant

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
            NativeAuthViewModel.factory(AppRole.MERCHANT, repository),
        )[NativeAuthViewModel::class.java]

        setContent {
            QueueGoTheme {
                NativeAuthGate("QueueGo Merchant", vm) { user, logout ->
                    NativeAccountHome("QueueGo Merchant", user, "Native · Supabase Production", logout)
                }
            }
        }
    }
}
