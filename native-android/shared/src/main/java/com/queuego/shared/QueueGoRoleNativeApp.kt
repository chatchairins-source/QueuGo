package com.queuego.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
fun QueueGoRoleNativeApp(expectedRole: String, appTitle: String, roleLabel: String) {
    val context = LocalContext.current
    val api = remember { NativeAuthApi() }
    val store = remember { SecureRoleSessionStore(context, expectedRole) }
    val scope = rememberCoroutineScope()
    val logoutScope = remember(context) { nativeLogoutScope(context, scope) }
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

    MaterialTheme {
        Scaffold { insets ->
            when {
                restoring -> Column(
                    Modifier.padding(insets).fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) { CircularProgressIndicator(Modifier.padding(24.dp)) }

                auth == null -> Login(
                    Modifier.padding(insets), appTitle, roleLabel, busy, error
                ) { id, pass ->
                    busy = true
                    error = null
                    scope.launch {
                        runCatching { api.signIn(id, pass, expectedRole, store.deviceId()) }
                            .onSuccess { store.save(it); auth = it }
                            .onFailure { error = it.message ?: "เข้าสู่ระบบไม่สำเร็จ" }
                        busy = false
                    }
                }

                else -> Home(
                    Modifier.padding(insets), appTitle, roleLabel, auth!!
                ) {
                    val current = auth!!
                    logoutScope.launch { api.revoke(current.session) }
                    store.clear()
                    auth = null
                }
            }
        }
    }
}

@Composable
private fun Login(
    modifier: Modifier,
    appTitle: String,
    roleLabel: String,
    busy: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit
) {
    var id by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Q", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
        Text(appTitle, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(roleLabel + " · Android Native")
        Spacer(Modifier.height(28.dp))
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
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { onLogin(id.trim(), password) },
            enabled = !busy && id.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) CircularProgressIndicator() else Text("เข้าสู่ระบบ")
        }
    }
}

@Composable
private fun Home(
    modifier: Modifier,
    appTitle: String,
    roleLabel: String,
    auth: NativeAuth,
    onLogout: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text(appTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("สวัสดี " + auth.user.name)
        Text("สถานะบัญชี: " + auth.user.status)
        Spacer(Modifier.height(18.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                if (auth.user.status == "active") {
                    Text("เชื่อมต่อ QueueGo Production แล้ว", fontWeight = FontWeight.Bold)
                    Text(roleLabel + " ใช้บัญชีจริง, RLS และ Session เดิมของ QueueGo")
                } else {
                    Text("บัญชียังไม่พร้อมใช้งาน", fontWeight = FontWeight.Bold)
                    Text("สถานะปัจจุบัน: " + auth.user.status)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("ออกจากระบบ") }
    }
}
