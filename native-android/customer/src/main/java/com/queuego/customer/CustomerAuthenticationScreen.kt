package com.queuego.customer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.queuego.shared.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** index.html V.login/V.register and bind(register), using real Supabase and a fresh OS location. */
@Composable
fun CustomerAuthenticationScreen(
    entry: NativeAuthEntry,
    onBackToGuest: (() -> Unit)? = null
) {
    var registering by rememberSaveable { mutableStateOf(false) }
    var identifier by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var emailMode by rememberSaveable { mutableStateOf(false) }
    // Passwords are deliberately absent from saved instance state.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var locating by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val busy = entry.busy || locating
    val red = Color(0xFFF04455)

    fun submitLogin() {
        if (busy) return
        localError = null
        keyboard?.hide()
        entry.login(identifier.trim(), password)
    }

    fun locateAndRegister() {
        if (entry.busy) { locating = false; return }
        scope.launch {
            try {
                val position = customerRegistrationLocation(context)
                entry.registerCustomer(NativeCustomerRegistration(name.trim(), phone.trim(),
                    if (emailMode) email.trim() else null, password, confirmation,
                    position.latitude, position.longitude))
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                localError = failure.message ?: "ต้องอนุญาตตำแหน่งที่ตั้งเพื่อสมัคร"
            } finally { locating = false }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) locateAndRegister()
        else { locating = false; localError = "ต้องอนุญาตตำแหน่งที่ตั้งเพื่อสมัคร" }
    }

    fun submitRegistration() {
        if (busy) return
        localError = null
        entry.clearError()
        try {
            // Validate before requesting location, exactly as the web submission does.
            require(name.trim().isNotBlank() && (if (emailMode)
                Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim())
                else phone.filter(Char::isDigit).length >= 9)) { "กรอกข้อมูลสมัครสมาชิกให้ครบ" }
            requireNativeStrongPassword(password)
            require(password == confirmation) { "ยืนยันรหัสผ่านให้ตรงกัน" }
        } catch (failure: IllegalArgumentException) { localError = failure.message; return }
        keyboard?.hide()
        locating = true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            locateAndRegister()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LaunchedEffect(entry.customerAccountCreated) {
        if (entry.customerAccountCreated) {
            identifier = if (emailMode) email.trim() else phone.trim()
            registering = false
            confirmation = ""
        }
    }
    BackHandler(enabled = !busy) {
        if (registering) {
            registering = false
            localError = null
            entry.clearError()
        } else {
            onBackToGuest?.invoke()
        }
    }

    Column(Modifier.fillMaxSize().background(QgBg).imePadding()) {
        Row(
            Modifier.fillMaxWidth().background(Color.White)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Queue", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = QgInk)
                Text("Go", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = QgRed)
            }
            Text(
                "เข้าสู่ระบบ",
                color = QgRed,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clickable(enabled = !busy) {
                    registering = false
                    localError = null
                    entry.clearError()
                }
            )
        }
        HorizontalDivider(color = Color(0x0D000000))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val side = if (maxWidth <= 420.dp) 16.dp else 20.dp
            val cardMaxWidth = if (registering) 720.dp else 430.dp
            val cardRadius = if (registering) 20.dp else 28.dp
            val cardPadding = if (registering) 16.dp else 24.dp
            Column(
                Modifier
                    .widthIn(max = cardMaxWidth)
                    .fillMaxWidth()
                    .align(if (registering) Alignment.TopCenter else Alignment.Center)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = side, vertical = if (registering) 12.dp else 8.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, QgLine, RoundedCornerShape(cardRadius))
                        .background(Color.White, RoundedCornerShape(cardRadius))
                        .padding(cardPadding)
                ) {
                    Text(
                        if (registering) "สมัครสมาชิก" else "เข้าสู่ระบบ",
                        fontSize = if (registering) 26.sp else 29.sp,
                        fontWeight = FontWeight.Black,
                        color = QgInk
                    )
                    if (!registering) {
                        Spacer(Modifier.height(4.dp))
                        Text("ยินดีต้อนรับสู่ QueueGo", fontSize = 12.sp, color = QgMuted)
                        Spacer(Modifier.height(20.dp))
                        CustomerAuthInput(identifier, { identifier = it }, "เบอร์โทรหรืออีเมล", busy,
                            type = KeyboardType.Phone)
                        CustomerAuthInput(password, { password = it }, "รหัสผ่าน", busy,
                            password = true, done = ::submitLogin)
                    } else {
                        Spacer(Modifier.height(16.dp))
                        CustomerAuthInput(name, { name = it }, "ชื่อ-นามสกุล", busy)
                        Text("สมัครด้วย", fontSize = 16.sp, color = QgInk)
                        Box {
                            Row(Modifier.fillMaxWidth().height(52.dp).border(1.dp, QgLine, RoundedCornerShape(16.dp))
                                .clickable(enabled = !busy) { pickerOpen = true }.padding(horizontal = 15.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (emailMode) "อีเมล" else "เบอร์โทรศัพท์", fontSize = 16.sp)
                                Text("⌄", color = QgInk)
                            }
                            DropdownMenu(pickerOpen, { pickerOpen = false }) {
                                listOf(false to "เบอร์โทรศัพท์", true to "อีเมล").forEach { (mode, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { emailMode = mode; pickerOpen = false })
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (emailMode) CustomerAuthInput(email, { email = it }, "อีเมล", busy, type = KeyboardType.Email)
                        CustomerAuthInput(phone, { phone = it }, "เบอร์โทร", busy, type = KeyboardType.Phone)
                        CustomerAuthInput(password, { password = it }, "อย่างน้อย 12 ตัว: A-Z, a-z, ตัวเลข, สัญลักษณ์", busy, password = true)
                        CustomerAuthInput(confirmation, { confirmation = it }, "ยืนยันรหัสผ่าน", busy,
                            password = true, done = ::submitRegistration)
                        Text("ต้องอนุญาตตำแหน่งที่ตั้งเพื่อสมัครสมาชิก", fontSize = 10.5.sp, color = QgMuted)
                        Spacer(Modifier.height(12.dp))
                    }
                    val failure = localError ?: entry.error
                    if (!failure.isNullOrBlank()) {
                        Text(failure, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                    }
                    Button(onClick = { if (registering) submitRegistration() else submitLogin() }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(17.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = red)) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text(if (registering) "สมัครและเข้าสู่ระบบ" else "เข้าสู่ระบบ", fontWeight = FontWeight.ExtraBold)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
                        if (!registering) Text("ยังไม่มีบัญชี? ", color = QgMuted, fontSize = 16.sp)
                        Text(if (registering) "มีบัญชีแล้ว" else "สมัครสมาชิก", color = QgRed,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            modifier = Modifier.clickable(enabled = !busy) {
                                registering = !registering; localError = null; entry.clearError()
                                password = ""; confirmation = ""
                            })
                    }
                }
                if (registering) Spacer(Modifier.height(92.dp))
            }
        }
    }
}

@Composable
private fun CustomerAuthInput(value: String, change: (String) -> Unit, hint: String, busy: Boolean,
    password: Boolean = false, type: KeyboardType = KeyboardType.Text, done: (() -> Unit)? = null) {
    val focus = LocalFocusManager.current
    BasicTextField(value = value, onValueChange = change, enabled = !busy, singleLine = true,
        modifier = Modifier.fillMaxWidth().height(52.dp).semantics { contentDescription = hint },
        textStyle = TextStyle(fontSize = 16.sp, color = QgInk),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else type,
            imeAction = if (done == null) ImeAction.Next else ImeAction.Done),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onDone = { done?.invoke() }),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().border(BorderStroke(1.dp, QgLine), RoundedCornerShape(16.dp))
                .background(Color.White, RoundedCornerShape(16.dp)).padding(horizontal = 15.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(hint, color = Color(0xFF757575), fontSize = 16.sp, maxLines = 1)
                inner()
            }
        })
    Spacer(Modifier.height(10.dp))
}

private suspend fun customerRegistrationLocation(context: Context): Location = withContext(Dispatchers.Main) {
    try {
        withTimeout(15_000L) {
            suspendCancellableCoroutine { continuation ->
                val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (!continuation.isActive) return
                        manager.removeUpdates(this)
                        if (location.latitude.isFinite() && location.longitude.isFinite()) continuation.resume(location)
                        else continuation.resumeWithException(IllegalStateException("อ่านพิกัดไม่สำเร็จ กรุณาลองใหม่"))
                    }
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                    @Deprecated("Legacy Android location callback")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                try {
                    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                        .filter { manager.isProviderEnabled(it) }
                    if (providers.isEmpty()) throw IllegalStateException("กรุณาเปิดตำแหน่งที่ตั้งแล้วลองใหม่")
                    providers.forEach { manager.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper()) }
                } catch (failure: Exception) {
                    manager.removeUpdates(listener)
                    if (continuation.isActive) continuation.resumeWithException(failure)
                }
            }
        }
    } catch (failure: kotlinx.coroutines.TimeoutCancellationException) {
        throw IllegalStateException("อ่านพิกัดไม่สำเร็จ กรุณาลองใหม่", failure)
    }
}
