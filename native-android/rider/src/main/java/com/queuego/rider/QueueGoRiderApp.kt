package com.queuego.rider

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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
fun QueueGoRiderApp() {
    val context = LocalContext.current
    val api = remember { QueueGoApi() }
    val store = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()

    var auth by remember { mutableStateOf<QueueGoAuth?>(null) }
    var restoring by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val cached = store.load()
        if (cached != null) {
            runCatching { api.validate(cached) }
                .onSuccess {
                    store.save(it)
                    auth = it
                }
                .onFailure {
                    store.clear()
                    error = it.message
                }
        }
        restoring = false
    }

    LaunchedEffect(auth?.session?.sessionId) {
        val current = auth ?: return@LaunchedEffect
        while (true) {
            delay(25_000)
            val valid = runCatching {
                api.validate(current)
                api.touch(current.session)
            }.isSuccess
            if (!valid) {
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
                restoring -> LoadingScreen(Modifier.padding(insets))
                auth == null -> LoginScreen(
                    modifier = Modifier.padding(insets),
                    busy = busy,
                    error = error,
                    onLogin = { id, password ->
                        busy = true
                        error = null
                        scope.launch {
                            runCatching { api.signIn(id, password, store.deviceId()) }
                                .onSuccess {
                                    store.save(it)
                                    auth = it
                                }
                                .onFailure {
                                    error = it.message ?: "เข้าสู่ระบบไม่สำเร็จ"
                                }
                            busy = false
                        }
                    }
                )
                else -> RiderHome(
                    modifier = Modifier.padding(insets),
                    auth = auth!!,
                    api = api,
                    onLogout = {
                        val current = auth!!
                        scope.launch { api.revoke(current.session) }
                        store.clear()
                        auth = null
                    }
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen(modifier: Modifier) {
    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(Modifier.padding(24.dp))
    }
}

@Composable
private fun LoginScreen(
    modifier: Modifier,
    busy: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit
) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Q",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black
        )
        Text("QueueGo Rider", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Android Native · Production")
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it },
            label = { Text("เบอร์โทรศัพท์ / อีเมล") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("รหัสผ่าน") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { onLogin(identifier.trim(), password) },
            enabled = !busy && identifier.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) CircularProgressIndicator() else Text("เข้าสู่ระบบ")
        }
    }
}

@Composable
private fun RiderHome(
    modifier: Modifier,
    auth: QueueGoAuth,
    api: QueueGoApi,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<RiderSnapshot?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(auth.session.sessionId) {
        while (true) {
            runCatching { api.riderSnapshot(auth) }
                .onSuccess {
                    snapshot = it
                    loadError = null
                }
                .onFailure { loadError = it.message ?: "โหลดงานไม่สำเร็จ" }
            delay(3_000)
        }
    }

    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("QueueGo Rider", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("สวัสดี " + auth.user.name)
        Text("สถานะบัญชี: " + auth.user.status)
        Spacer(Modifier.height(18.dp))

        if (auth.user.status != "active") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("บัญชียังไม่พร้อมรับงาน", fontWeight = FontWeight.Bold)
                    Text("สถานะ: " + auth.user.status)
                }
            }
        } else {
            val current = snapshot
            if (current == null && loadError == null) {
                CircularProgressIndicator()
            } else if (loadError != null) {
                Text(loadError!!, color = MaterialTheme.colorScheme.error)
            } else if (current != null) {
                Text(
                    if (current.online) "ออนไลน์ · พร้อมรับงาน" else "ออฟไลน์",
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                val job = current.activeJob ?: current.offeredJob
                if (job == null) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("ยังไม่มีงานที่ Server เสนอให้", fontWeight = FontWeight.Bold)
                            Text("QueueGo แสดงเฉพาะงานที่จัดให้ Rider คนนี้")
                        }
                    }
                } else {
                    JobCard(job) {
                        val target = job.navigationTarget ?: return@JobCard
                        val uri = Uri.parse(
                            "google.navigation:q=" + target.first + "," + target.second + "&mode=d"
                        )
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        runCatching { context.startActivity(intent) }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("ออกจากระบบ")
        }
    }
}

@Composable
private fun JobCard(job: RiderJob, onNavigate: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(job.numberLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("สถานะ: " + job.status)
            Spacer(Modifier.height(8.dp))
            Text(job.pickupAddress ?: "ยังไม่มีที่อยู่ร้าน")
            Text("→ " + (job.deliveryAddress ?: "ยังไม่มีที่อยู่ลูกค้า"))
            job.deliveryFee?.let { Text("ค่าส่ง ฿" + "%.0f".format(it)) }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onNavigate,
                enabled = job.navigationTarget != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (job.isDelivering) "นำทางไปหาลูกค้า" else "นำทางไปร้าน")
            }
        }
    }
}
