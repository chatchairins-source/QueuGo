package com.queuego.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun QueueGoAuthHost(
    expectedRole: String,
    appLabel: String,
    content: @Composable (NativeAuth, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val api = remember { NativeAuthApi() }
    val store = remember { SecureRoleSessionStore(context, expectedRole) }
    val scope = rememberCoroutineScope()
    var auth by remember { mutableStateOf<NativeAuth?>(null) }
    var restoring by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(expectedRole) {
        val cached = store.load()
        if (cached != null) {
            runCatching { api.validate(cached, expectedRole) }
                .onSuccess { store.save(it); auth = it }
                .onFailure { store.clear(); error = it.message }
        }
        restoring = false
    }

    LaunchedEffect(auth?.session?.sessionId) {
        val current = auth ?: return@LaunchedEffect
        while (true) {
            delay(25_000)
            val ok = runCatching {
                api.validate(current, expectedRole)
                api.touch(current.session)
            }.isSuccess
            if (!ok) {
                store.clear()
                auth = null
                error = "Session นี้ถูกยกเลิกหรือเปิดจากอุปกรณ์อื่น"
                break
            }
        }
    }

    QueueGoTheme {
        when {
            restoring -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.padding(24.dp))
            }
            auth == null -> QueueGoLoginScreen(appLabel, busy, error) { id, pass ->
                busy = true
                error = null
                scope.launch {
                    runCatching { api.signIn(id, pass, expectedRole, store.deviceId()) }
                        .onSuccess { store.save(it); auth = it }
                        .onFailure { error = it.message ?: "เข้าสู่ระบบไม่สำเร็จ" }
                    busy = false
                }
            }
            else -> content(auth!!) {
                val current = auth!!
                scope.launch { api.revoke(current.session) }
                store.clear()
                auth = null
            }
        }
    }
}

@Composable
private fun QueueGoLoginScreen(
    appLabel: String,
    busy: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit
) {
    var id by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        QueueGoBrand(suffix = appLabel)
        Spacer(Modifier.height(8.dp))
        Text("เข้าสู่ระบบ QueueGo", fontWeight = FontWeight.ExtraBold)
        Text("ใช้บัญชี Production เดิมของคุณ", color = QgMuted)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = id, onValueChange = { id = it },
            label = { Text("เบอร์โทรศัพท์ / อีเมล") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("รหัสผ่าน") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onLogin(id.trim(), password) },
            enabled = !busy && id.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("เข้าสู่ระบบ")
        }
    }
}
