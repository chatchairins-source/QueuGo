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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class NativeAuthEntry(
    val busy: Boolean,
    val error: String?,
    val customerAccountCreated: Boolean,
    val merchantAccountCreated: Boolean,
    val login: (String, String) -> Unit,
    val registerCustomer: (NativeCustomerRegistration) -> Unit,
    val registerMerchant: (NativeMerchantRegistration) -> Unit,
    val clearError: () -> Unit
)

@Composable
fun QueueGoAuthHost(
    expectedRole: String,
    appLabel: String,
    entryScreen: (@Composable (NativeAuthEntry) -> Unit)? = null,
    content: @Composable (NativeAuth, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val api = remember { NativeAuthApi() }
    val store = remember { SecureRoleSessionStore(context, expectedRole) }
    val scope = rememberCoroutineScope()
    val logoutScope = remember(context) { nativeLogoutScope(context, scope) }
    var auth by remember { mutableStateOf<NativeAuth?>(null) }
    var restoring by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var restoreAttempt by remember { mutableStateOf(0) }
    var recoveryRequired by remember { mutableStateOf(false) }
    var customerAccountCreated by rememberSaveable { mutableStateOf(false) }
    var customerSignupCheckpoint by rememberSaveable { mutableStateOf("") }
    var merchantAccountCreated by rememberSaveable { mutableStateOf(false) }
    var merchantSignupCheckpoint by rememberSaveable { mutableStateOf("") }

    fun signIn(identifier: String, password: String) {
        if (busy || identifier.isBlank() || password.isBlank()) return
        busy = true
        error = null
        scope.launch {
            try {
                val signedIn = api.signIn(identifier, password, expectedRole, store.deviceId())
                store.save(signedIn)
                auth = signedIn
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = failure.message ?: "เข้าสู่ระบบไม่สำเร็จ"
            } finally { busy = false }
        }
    }

    fun registerCustomer(form: NativeCustomerRegistration) {
        if (busy || expectedRole != "customer") return
        busy = true
        error = null
        customerAccountCreated = false
        scope.launch {
            try {
                form.validate()
                val identifier = form.email?.trim() ?: form.phone.trim()
                // A lost signup reply is ambiguous. Recover through authentication,
                // including after Activity restoration, rather than replaying signup.
                val signedIn = if (customerSignupCheckpoint == identifier) {
                    api.signIn(identifier, form.password, "customer", store.deviceId())
                } else {
                    customerSignupCheckpoint = identifier
                    try { api.registerCustomer(form, store.deviceId()) }
                    catch (failure: Exception) {
                        if (failure is NativeAuthHttpException && failure.statusCode in 400..499)
                            customerSignupCheckpoint = ""
                        throw failure
                    }
                }
                customerAccountCreated = true
                store.save(signedIn)
                auth = signedIn
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (failure is NativeCustomerSignupCompletedException ||
                    failure is NativeCustomerSignupUncertainException) customerAccountCreated = true
                error = failure.message ?: "สมัครสมาชิกไม่สำเร็จ"
            } finally { busy = false }
        }
    }

    fun registerMerchant(form: NativeMerchantRegistration) {
        if (busy || expectedRole != "shop") return
        busy = true
        error = null
        merchantAccountCreated = false
        scope.launch {
            try {
                form.validate()
                val identifier = form.normalizedPhone
                val skipSignup = merchantSignupCheckpoint == identifier
                if (!skipSignup) merchantSignupCheckpoint = identifier
                val signedIn = try {
                    api.registerMerchant(form, store.deviceId(), skipSignup)
                } catch (failure: Exception) {
                    if (!skipSignup &&
                        failure is NativeAuthHttpException &&
                        failure.statusCode in 400..499
                    ) merchantSignupCheckpoint = ""
                    throw failure
                }
                merchantAccountCreated = true
                store.save(signedIn)
                auth = signedIn
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (failure is NativeMerchantSignupCompletedException ||
                    failure is NativeMerchantSignupUncertainException
                ) merchantAccountCreated = true
                error = failure.message ?: "สมัครร้านค้าไม่สำเร็จ"
            } finally { busy = false }
        }
    }

    LaunchedEffect(expectedRole, restoreAttempt) {
        restoring = true
        val cached = store.load()
        try {
            if (cached != null) {
                val validated = api.validate(cached, expectedRole) { store.save(it) }
                store.save(validated)
                auth = validated
            }
            recoveryRequired = false
            error = null
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            if (shouldClearNativeSession(failure)) {
                if (cached != null) runCatching { disableNativePush(cached, store) }
                store.clear()
                recoveryRequired = false
            } else recoveryRequired = true
            error = failure.message
        } finally {
            restoring = false
        }
    }

    LaunchedEffect(auth?.session?.sessionId) {
        if (auth == null) return@LaunchedEffect
        while (true) {
            delay(25_000)
            val current = auth ?: break
            try {
                val validated = api.validate(current, expectedRole) { refreshed ->
                    store.save(refreshed)
                    auth = refreshed
                }
                // Persist rotated refresh tokens before the independent heartbeat can fail.
                store.save(validated)
                auth = validated
                api.touch(validated.session)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (shouldClearNativeSession(failure)) {
                    runCatching { disableNativePush(current, store) }
                    store.clear()
                    auth = null
                    error = "Session นี้ถูกยกเลิก หมดอายุ หรือเปิดจากอุปกรณ์อื่น"
                    break
                }
                // A failed network read does not revoke a previously validated session.
                // Try again on the next tick; protected writes remain server-authorized.
            }
        }
    }

    LaunchedEffect(auth?.session?.sessionId, expectedRole) {
        val current = auth ?: return@LaunchedEffect
        runCatching {
            syncNativePush(
                context = context,
                auth = current,
                expectedRole = expectedRole,
                store = store,
                channelName = if (expectedRole == "shop") "QueueGo Merchant" else "QueueGo",
                channelDescription = "แจ้งเตือนออเดอร์ ข้อความ และสถานะจาก QueueGo",
                highImportance = expectedRole == "shop"
            )
        }
    }

    QueueGoTheme {
        when {
            restoring -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.padding(24.dp))
            }
            recoveryRequired -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("เชื่อมต่อไม่สำเร็จ", fontWeight = FontWeight.Bold)
                Text(error ?: "กรุณาตรวจสอบการเชื่อมต่อแล้วลองใหม่", color = QgMuted)
                Button(onClick = { restoreAttempt += 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text("ลองใหม่")
                }
            }
            auth == null -> {
                if (entryScreen != null) entryScreen(NativeAuthEntry(
                    busy = busy,
                    error = error,
                    customerAccountCreated = customerAccountCreated,
                    merchantAccountCreated = merchantAccountCreated,
                    login = ::signIn,
                    registerCustomer = ::registerCustomer,
                    registerMerchant = ::registerMerchant,
                    clearError = { if (!busy) error = null }
                )) else QueueGoLoginScreen(appLabel, busy, error, ::signIn)
            }
            else -> content(auth!!) {
                val current = auth!!
                logoutScope.launch {
                    runCatching { disableNativePush(current, store) }
                    api.revoke(current.session)
                }
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
