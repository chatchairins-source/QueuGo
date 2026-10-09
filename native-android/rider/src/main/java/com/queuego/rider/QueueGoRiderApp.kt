package com.queuego.rider

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.queuego.shared.QueueGoTheme
import com.queuego.shared.QgAccountDeletionSection
import com.queuego.shared.QueueGoBrand
import com.queuego.shared.QgStatusPill
import com.queuego.shared.QgRed
import com.queuego.shared.QgMuted
import com.queuego.shared.QgBg
import com.queuego.shared.QgIcon
import com.queuego.shared.QgLine
import com.queuego.shared.QgRedSoft
import com.queuego.shared.QgRiderBg
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.queuego.shared.NativeOrderRealtime
import com.queuego.shared.riderRealtimeSubscriptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

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
        if (auth == null) return@LaunchedEffect
        while (true) {
            delay(25_000)
            val current = auth ?: break
            val result = runCatching {
                val validated = api.validate(current)
                api.touch(validated.session)
                validated
            }
            result.onSuccess { validated ->
                store.save(validated)
                auth = validated
            }.onFailure {
                RiderReturnService.stop(context)
                store.clear()
                auth = null
                error = "Session นี้ถูกยกเลิก หมดอายุ หรือเปิดจากอุปกรณ์อื่น"
            }
            if (result.isFailure) break
        }
    }

    QueueGoTheme {
        Scaffold(containerColor = QgBg) { insets ->
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
                        RiderReturnService.stop(context)
                        scope.launch {
                            runCatching { api.setOnline(current, false, null, null) }
                            runCatching { disableRiderNativePush(current, api, store) }
                            runCatching { api.revoke(current.session) }
                        }
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
        modifier
            .fillMaxSize()
            .background(QgRiderBg)
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(QgRedSoft)
                .padding(horizontal = 24.dp, vertical = 34.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(QgRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Q", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    QueueGoBrand()
                    Text("Rider", color = QgMuted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(26.dp))
            Text("สำหรับไรเดอร์ QueueGo", color = QgRed, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(7.dp))
            Text(
                "รับงานง่าย\nเห็นข้อมูลชัด\nทำงานได้เร็ว",
                color = Color(0xFF17171B),
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "ดูงาน เส้นทาง รายได้ และสถานะงานทั้งหมดในที่เดียว",
                color = QgMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Card(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, QgLine)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("เข้าสู่ระบบ", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                Text("ใช้เบอร์โทรศัพท์หรืออีเมลที่สมัครไว้", color = QgMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(18.dp))
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
                    Spacer(Modifier.height(10.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onLogin(identifier.trim(), password) },
                    enabled = !busy && identifier.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Text("เข้าสู่ระบบ", fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "QueueGo Rider • Native",
                    color = QgMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
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
    val scope = rememberCoroutineScope()
    val pushStore = remember(context) { SessionStore(context) }
    val realtime = remember { NativeOrderRealtime() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val snapshotMutex = remember { Mutex() }

    var snapshot by remember { mutableStateOf<RiderSnapshot?>(null) }
    var todaySummary by remember { mutableStateOf<RiderPeriodSummary?>(null) }
    var recentHistory by remember { mutableStateOf<List<RiderHistoryOrder>>(emptyList()) }
    var lastSummaryAt by remember { mutableStateOf(0L) }
    var lastHistoryAt by remember { mutableStateOf(0L) }
    var lastLocationPushAt by remember { mutableStateOf(0L) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var chatJob by remember { mutableStateOf<RiderJob?>(null) }
    var verifyMode by remember { mutableStateOf<String?>(null) }
    var verifyJob by remember { mutableStateOf<RiderJob?>(null) }
    var verifyItems by remember { mutableStateOf<List<RiderItem>>(emptyList()) }
    var verifyMarketPickup by remember { mutableStateOf<MarketPickup?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var lastOfferAlertKey by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableStateOf("home") }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        actionMessage = if (grants.values.any { it }) {
            "อนุญาตตำแหน่งแล้ว กดปุ่มเดิมอีกครั้ง"
        } else {
            "ยังไม่ได้รับสิทธิ์ตำแหน่ง"
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        actionMessage = if (granted) {
            "อนุญาตการแจ้งเตือนแล้ว"
        } else {
            "ยังไม่ได้อนุญาตการแจ้งเตือน งานใหม่จะไม่เด้งเมื่อแอปอยู่เบื้องหลัง"
        }
    }

    LaunchedEffect(auth.user.id) {
        ensureRiderOrderChannel(context)
        runCatching {
            syncRiderNativePush(context, auth, api, pushStore)
        }.onFailure {
            actionMessage = "เชื่อมการแจ้งเตือนเบื้องหลังไม่สำเร็จ: " +
                (it.message ?: "กรุณาลองใหม่")
        }
        if (
            Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri = pendingCameraUri
            actionMessage = "ถ่ายรูปหลักฐานแล้ว"
        } else {
            pendingCameraUri = null
        }
    }

    fun ensureLocationPermission(): Boolean {
        if (hasLocationPermission(context)) return true
        locationPermission.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        return false
    }

    fun configureReturnControl() {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.packageName)
            )
            context.startActivity(intent)
            actionMessage = "เปิดสิทธิ์ 'แสดงทับแอปอื่น' เพื่อใช้ปุ่ม Q ระหว่างนำทาง"
            return
        }
        actionMessage = "ปุ่ม Q กลับ QueueGo พร้อมใช้งานแล้ว"
    }

    fun openCamera() {
        val uri = createEvidenceUri(context)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    fun openVerification(mode: String, job: RiderJob) {
        actionBusy = true
        actionMessage = null
        scope.launch {
            runCatching { api.orderItems(auth, job.id) }
                .onSuccess {
                    verifyMode = mode
                    verifyJob = job
                    verifyItems = it
                    photoUri = null
                    pendingCameraUri = null
                }
                .onFailure { actionMessage = it.message ?: "โหลดรายการไม่สำเร็จ" }
            actionBusy = false
        }
    }

    fun openMarketPickupVerification(job: RiderJob, pickup: MarketPickup) {
        verifyMode = "marketPickup"
        verifyJob = job
        verifyMarketPickup = pickup
        verifyItems = emptyList()
        photoUri = null
        pendingCameraUri = null
        actionMessage = null
    }

    fun closeVerification() {
        verifyMode = null
        verifyJob = null
        verifyItems = emptyList()
        verifyMarketPickup = null
        photoUri = null
        pendingCameraUri = null
    }

    fun playRiderOfferAlert() {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 650)
        scope.launch {
            delay(720)
            runCatching {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 450)
                delay(520)
                tone.release()
            }
        }
    }

    suspend fun refreshSnapshot() = snapshotMutex.withLock {
        runCatching { api.riderSnapshot(auth) }
            .onSuccess {
                val offerKey = it.offeredJob?.let { job ->
                    job.id + ":" + (job.offerExpiresAt ?: "")
                }
                if (offerKey != null && offerKey != lastOfferAlertKey) {
                    lastOfferAlertKey = offerKey
                    playRiderOfferAlert()
                    actionMessage = "มีงานใหม่ที่ระบบจัดให้ กรุณาตอบรับภายใน 30 วินาที"
                    // A selected 30-second offer must be visible immediately even if the Rider
                    // was viewing earnings/profile/messages. There is no shared job pool.
                    chatJob = null
                    activeTab = "home"
                }
                snapshot = it
                loadError = null
                if (it.activeJob == null) RiderReturnService.stop(context)
                val now = System.currentTimeMillis()
                if (it.online && now - lastLocationPushAt >= 10_000L) {
                    val loc = lastKnownLocation(context)
                    if (loc != null) {
                        runCatching { api.updateLocation(auth, loc.first, loc.second) }
                            .onFailure { if (it is CancellationException) throw it }
                        lastLocationPushAt = now
                    }
                }
                if (now - lastSummaryAt >= 60_000L || todaySummary == null) {
                    runCatching { api.periodSummary(auth, 1) }
                        .onFailure { if (it is CancellationException) throw it }.onSuccess { summary ->
                        todaySummary = summary
                        lastSummaryAt = now
                    }
                }
                if (now - lastHistoryAt >= 60_000L || recentHistory.isEmpty()) {
                    runCatching { api.history(auth, 20) }
                        .onFailure { if (it is CancellationException) throw it }.onSuccess { rows ->
                        recentHistory = rows
                        lastHistoryAt = now
                    }
                }
            }
            .onFailure {
                if (it is CancellationException) throw it
                loadError = it.message ?: "โหลดงานไม่สำเร็จ"
            }
    }

    BackHandler(enabled = activeTab != "home" && chatJob == null && verifyMode == null) {
        activeTab = "home"
    }

    val subscriptions = riderRealtimeSubscriptions(auth.user.id, snapshot?.riderProfileId)
    LaunchedEffect(auth.session.accessToken, subscriptions, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            realtime.changes(auth.session.accessToken, subscriptions).collect { refreshSnapshot() }
        }
    }
    LaunchedEffect(auth.session.accessToken, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                refreshSnapshot()
                delay(3_000)
            }
        }
    }

    if (chatJob != null) {
        RiderChatScreen(
            auth = auth,
            job = chatJob!!,
            onBack = { chatJob = null }
        )
        return
    }

    if (verifyMode != null && verifyJob != null) {
        VerificationScreen(
            modifier = modifier,
            mode = verifyMode!!,
            job = verifyJob!!,
            marketPickup = verifyMarketPickup,
            items = verifyItems,
            photoReady = photoUri != null,
            busy = actionBusy,
            message = actionMessage,
            onBack = { closeVerification() },
            onCamera = { openCamera() },
            onConfirm = {
                val job = verifyJob ?: return@VerificationScreen
                val uri = photoUri ?: run {
                    actionMessage = "กรุณาถ่ายรูปหลักฐานก่อน"
                    return@VerificationScreen
                }
                val location = lastKnownLocation(context)
                actionBusy = true
                actionMessage = null
                scope.launch {
                    val result = runCatching {
                        when (verifyMode) {
                            "pickup" -> api.pickupWithPhoto(
                                context,
                                auth,
                                job.id,
                                uri,
                                location?.first,
                                location?.second
                            )
                            "marketPickup" -> api.marketPickupWithPhoto(
                                context,
                                auth,
                                verifyMarketPickup ?: error("ไม่พบจุดรับตลาด"),
                                uri,
                                location?.first,
                                location?.second
                            )
                            else -> api.completeWithPhoto(
                                context,
                                auth,
                                job,
                                uri,
                                location?.first,
                                location?.second
                            )
                        }
                    }
                    result.onSuccess {
                        val completedMode = verifyMode
                        actionMessage = when (completedMode) {
                            "pickup" -> "รับสินค้าแล้ว · กำลังเปิดนำทางไปลูกค้า"
                            "marketPickup" -> "รับสินค้าจุดนี้แล้ว"
                            else -> "จัดส่งสำเร็จ"
                        }
                        if (completedMode == "delivery") RiderReturnService.stop(context)
                        closeVerification()
                        val updated = runCatching { api.riderSnapshot(auth) }.getOrNull()
                        if (updated != null) snapshot = updated
                        if (completedMode == "pickup") {
                            updated?.activeJob?.let { pickedUp ->
                                openNavigation(context, pickedUp)
                            }
                        }
                    }.onFailure {
                        actionMessage = it.message ?: "บันทึกหลักฐานไม่สำเร็จ"
                    }
                    actionBusy = false
                }
            }
        )
        return
    }

    if (activeTab != "home") {
        Box(
            modifier
                .fillMaxSize()
                .background(QgRiderBg)
        ) {
            when (activeTab) {
                "chat" -> RiderMessagesScreen(
                    auth = auth,
                    activeJob = snapshot?.activeJob,
                    history = recentHistory,
                    onOpenChat = { chatJob = it },
                    modifier = Modifier.fillMaxSize()
                )
                "earn" -> RiderEarningsScreen(
                    auth = auth,
                    api = api,
                    modifier = Modifier.fillMaxSize()
                )
                "profile" -> RiderProfileScreen(
                    auth = auth,
                    snapshot = snapshot,
                    history = recentHistory,
                    api = api,
                    onSnapshot = { snapshot = it },
                    onLogout = onLogout,
                    modifier = Modifier.fillMaxSize()
                )
            }
            RiderBottomNavigation(
                activeTab = activeTab,
                onSelect = { activeTab = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        return
    }

    var recenterSignal by remember { mutableStateOf(0) }
    var mapReady by remember { mutableStateOf(false) }
    val mapJob = snapshot?.activeJob ?: snapshot?.offeredJob
    val mapLaundryJob = if (snapshot?.activeJob == null) snapshot?.laundry?.activeJob else null

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(QgRiderBg)
    ) {
        RiderLongdoMap(
            modifier = Modifier.fillMaxSize(),
            job = mapJob,
            laundryJob = mapLaundryJob,
            marketPickups = snapshot?.marketPickups.orEmpty(),
            recenterSignal = recenterSignal,
            onMapReady = { mapReady = it }
        )

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            RiderBlueprintTopBar(
                name = auth.user.name,
                active = auth.user.status == "active",
                online = snapshot?.online == true
            )
            if (actionMessage != null) {
                Spacer(Modifier.height(7.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE171519))
                ) {
                    Text(
                        actionMessage!!,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (!mapReady) {
                Spacer(Modifier.height(7.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xEEFFFFFF))
                ) {
                    Text(
                        "กำลังเชื่อมต่อ Longdo Map…",
                        color = QgMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 70.dp, end = 10.dp)
                .size(44.dp)
                .clickable { recenterSignal += 1 },
            shape = androidx.compose.foundation.shape.CircleShape,
            colors = CardDefaults.cardColors(containerColor = Color(0xF8FFFFFF)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE5E8)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                QgIcon("location", Modifier.size(23.dp), QgRed)
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.62f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .padding(bottom = 66.dp)
        ) {
        if (auth.user.status != "active") {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        if (current.online) "ออนไลน์ · พร้อมรับงาน" else "ออฟไลน์",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    QgStatusPill(if (current.online) "ONLINE" else "OFFLINE", current.online)
                }
                Spacer(Modifier.height(8.dp))
                if (current.activeJob == null && current.laundry.activeJob == null) {
                    if (current.online) {
                        OutlinedButton(
                            onClick = {
                                if (actionBusy) return@OutlinedButton
                                actionBusy = true
                                scope.launch {
                                    val loc = lastKnownLocation(context)
                                    runCatching { api.setOnline(auth, false, loc?.first, loc?.second) }
                                        .onSuccess {
                                            actionMessage = "ปิดรับงานแล้ว"
                                            snapshot = runCatching { api.riderSnapshot(auth) }.getOrNull()
                                        }
                                        .onFailure { actionMessage = it.message ?: "ปิดรับงานไม่สำเร็จ" }
                                    actionBusy = false
                                }
                            },
                            enabled = !actionBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("ปิดรับงาน") }
                    } else {
                        Button(
                            onClick = {
                                if (!ensureLocationPermission()) return@Button
                                if (actionBusy) return@Button
                                actionBusy = true
                                scope.launch {
                                    val loc = lastKnownLocation(context)
                                    runCatching { api.setOnline(auth, true, loc?.first, loc?.second) }
                                        .onSuccess {
                                            actionMessage = "เปิดรับงานแล้ว"
                                            snapshot = runCatching { api.riderSnapshot(auth) }.getOrNull()
                                        }
                                        .onFailure { actionMessage = it.message ?: "เปิดรับงานไม่สำเร็จ" }
                                    actionBusy = false
                                }
                            },
                            enabled = !actionBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("เปิดรับงาน", fontWeight = FontWeight.Black) }
                    }
                    val summary = todaySummary
                    if (summary != null) {
                        Spacer(Modifier.height(10.dp))
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("วันนี้", color = QgMuted, style = MaterialTheme.typography.labelSmall)
                                    Text(summary.jobs.toString() + " งาน", fontWeight = FontWeight.Black)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("รายได้", color = QgMuted, style = MaterialTheme.typography.labelSmall)
                                    Text("฿" + "%.0f".format(summary.income), fontWeight = FontWeight.Black)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("ออนไลน์", color = QgMuted, style = MaterialTheme.typography.labelSmall)
                                    Text("%.1f ชม.".format(summary.onlineHours), fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                when {
                    current.activeJob != null -> {
                        val activeJob = current.activeJob
                        ReturnControlCard(
                            overlayAllowed = Settings.canDrawOverlays(context),
                            onConfigure = { configureReturnControl() }
                        )
                        Spacer(Modifier.height(10.dp))
                        if (activeJob.marketOrderId != null && activeJob.status != "in_progress") {
                            MarketPickupPanel(
                                job = activeJob,
                                pickups = current.marketPickups,
                                busy = actionBusy,
                                onNavigate = { pickup ->
                                    openNavigation(
                                        context,
                                        activeJob.copy(
                                            status = "ready",
                                            pickupAddress = pickup.shopAddress,
                                            pickupLat = pickup.latitude,
                                            pickupLng = pickup.longitude
                                        )
                                    )
                                },
                                onVerify = { pickup ->
                                    openMarketPickupVerification(activeJob, pickup)
                                },
                                onStartDelivery = {
                                    actionBusy = true
                                    scope.launch {
                                        runCatching { api.startDelivery(auth, activeJob) }
                                            .onSuccess {
                                                actionMessage = "รับของครบแล้ว · เริ่มจัดส่ง"
                                                val updated = runCatching { api.riderSnapshot(auth) }.getOrNull()
                                                if (updated != null) snapshot = updated
                                                openNavigation(context, activeJob.copy(status = "in_progress"))
                                            }
                                            .onFailure {
                                                actionMessage = it.message ?: "เริ่มจัดส่งไม่สำเร็จ"
                                            }
                                        actionBusy = false
                                    }
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        ActiveJobCard(
                            job = if (
                                activeJob.marketOrderId != null &&
                                activeJob.status != "in_progress" &&
                                current.marketPickups.isNotEmpty() &&
                                current.marketPickups.all { it.done }
                            ) activeJob.copy(status = "picked_up") else activeJob,
                            busy = actionBusy,
                            onChat = { chatJob = current.activeJob },
                            onNavigate = { openNavigation(context, current.activeJob) },
                            onArriveShop = {
                                if (!ensureLocationPermission()) return@ActiveJobCard
                                val loc = lastKnownLocation(context)
                                actionBusy = true
                                scope.launch {
                                    runCatching {
                                        api.markArrival(
                                            auth,
                                            current.activeJob.id,
                                            "shop",
                                            loc?.first,
                                            loc?.second
                                        )
                                    }.onSuccess {
                                        actionMessage = "บันทึกถึงร้านแล้ว"
                                        snapshot = runCatching { api.riderSnapshot(auth) }.getOrNull()
                                    }.onFailure {
                                        actionMessage = it.message ?: "บันทึกถึงร้านไม่สำเร็จ"
                                    }
                                    actionBusy = false
                                }
                            },
                            onPickupVerify = {
                                openVerification("pickup", current.activeJob)
                            },
                            onStartDelivery = {
                                actionBusy = true
                                scope.launch {
                                    runCatching { api.startDelivery(auth, current.activeJob) }
                                        .onSuccess {
                                            actionMessage = "เริ่มจัดส่งแล้ว"
                                            val updated = runCatching { api.riderSnapshot(auth) }.getOrNull()
                                            if (updated != null) snapshot = updated
                                            openNavigation(context, current.activeJob.copy(status = "in_progress"))
                                        }
                                        .onFailure {
                                            actionMessage = it.message ?: "เริ่มจัดส่งไม่สำเร็จ"
                                        }
                                    actionBusy = false
                                }
                            },
                            onArriveCustomer = {
                                if (!ensureLocationPermission()) return@ActiveJobCard
                                val loc = lastKnownLocation(context)
                                actionBusy = true
                                scope.launch {
                                    runCatching {
                                        api.markArrival(
                                            auth,
                                            current.activeJob.id,
                                            "customer",
                                            loc?.first,
                                            loc?.second
                                        )
                                    }.onSuccess {
                                        actionMessage = "บันทึกถึงลูกค้าแล้ว"
                                        snapshot = runCatching { api.riderSnapshot(auth) }.getOrNull()
                                    }.onFailure {
                                        actionMessage = it.message ?: "บันทึกถึงลูกค้าไม่สำเร็จ"
                                    }
                                    actionBusy = false
                                }
                            },
                            onDeliveryVerify = {
                                openVerification("delivery", current.activeJob)
                            }
                        )
                    }
                    current.laundry.activeJob != null -> {
                        val laundryJob = current.laundry.activeJob
                        ReturnControlCard(
                            overlayAllowed = Settings.canDrawOverlays(context),
                            onConfigure = { configureReturnControl() }
                        )
                        Spacer(Modifier.height(10.dp))
                        RiderLaundryActiveCard(
                            job = laundryJob,
                            busy = actionBusy,
                            onNavigate = { openLaundryNavigation(context, laundryJob) },
                            onAction = { action ->
                                if (actionBusy) return@RiderLaundryActiveCard
                                actionBusy = true
                                scope.launch {
                                    runCatching { api.laundryAction(auth, laundryJob.jobId, action) }
                                        .onSuccess {
                                            actionMessage = when (action) {
                                                "arrive" -> "ถึงจุดรับแล้ว"
                                                "collect" -> "รับผ้าแล้ว · กำลังเปิดนำทางไปปลายทาง"
                                                else -> "ส่งมอบงานฝากซักเรียบร้อย"
                                            }
                                            val updated = runCatching { api.riderSnapshot(auth) }.getOrNull()
                                            if (updated != null) snapshot = updated
                                            if (action == "collect") {
                                                updated?.laundry?.activeJob?.let {
                                                    openLaundryNavigation(context, it)
                                                }
                                            }
                                        }
                                        .onFailure {
                                            actionMessage = it.message ?: "อัปเดตงานฝากซักไม่สำเร็จ"
                                        }
                                    actionBusy = false
                                }
                            }
                        )
                    }
                    current.offeredJob != null -> {
                        OfferCard(
                            job = current.offeredJob,
                            busy = actionBusy,
                            onAccept = {
                                actionBusy = true
                                scope.launch {
                                    runCatching { api.acceptOffer(auth, current.offeredJob) }
                                        .onSuccess {
                                            actionMessage = "รับงานสำเร็จ · กำลังเปิดนำทางไปร้าน"
                                            val updated = runCatching { api.riderSnapshot(auth) }.getOrNull()
                                            if (updated != null) snapshot = updated
                                            updated?.activeJob?.let { accepted ->
                                                openNavigation(context, accepted)
                                            }
                                        }
                                        .onFailure {
                                            actionMessage = it.message ?: "รับงานไม่สำเร็จ"
                                        }
                                    actionBusy = false
                                }
                            },
                            onDecline = {
                                actionBusy = true
                                scope.launch {
                                    runCatching { api.declineOffer(auth, current.offeredJob.id) }
                                        .onSuccess {
                                            actionMessage = "ส่งงานให้ Rider คนถัดไปแล้ว"
                                            snapshot = runCatching { api.riderSnapshot(auth) }.getOrNull()
                                        }
                                        .onFailure {
                                            actionMessage = it.message ?: "ปฏิเสธงานไม่สำเร็จ"
                                        }
                                    actionBusy = false
                                }
                            },
                            onExpired = {
                                actionMessage = "ข้อเสนอนี้หมดเวลา ระบบกำลังส่งงานให้ Rider คนถัดไป"
                                scope.launch { refreshSnapshot() }
                            }
                        )
                    }
                    else -> {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(18.dp)) {
                                Text("พร้อมรับงาน", color = QgRed, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(5.dp))
                                Text("ระบบกำลังหางานและจัดให้คุณอัตโนมัติ", fontWeight = FontWeight.Bold)
                                Text("เมื่อ Server เลือกคุณ งานจะขึ้นเฉพาะเครื่องนี้ 30 วินาที ไม่มีการแย่งงานกับ Rider คนอื่น", color = QgMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                if (
                    current.activeJob == null &&
                    current.laundry.activeJob == null &&
                    current.laundry.pool.isNotEmpty()
                ) {
                    Spacer(Modifier.height(10.dp))
                    RiderLaundryPoolCard(
                        jobs = current.laundry.pool,
                        busy = actionBusy,
                        onClaim = { job ->
                            if (actionBusy) return@RiderLaundryPoolCard
                            actionBusy = true
                            scope.launch {
                                runCatching { api.claimLaundryJob(auth, job.jobId) }
                                    .onSuccess {
                                        actionMessage = "รับงานฝากซักแล้ว · กำลังเปิดนำทาง"
                                        val updated = runCatching { api.riderSnapshot(auth) }.getOrNull()
                                        if (updated != null) snapshot = updated
                                        updated?.laundry?.activeJob?.let {
                                            openLaundryNavigation(context, it)
                                        }
                                    }
                                    .onFailure {
                                        actionMessage = it.message ?: "รับงานฝากซักไม่สำเร็จ"
                                    }
                                actionBusy = false
                            }
                        }
                    )
                }

                Spacer(Modifier.height(10.dp))
                RiderLaundryModeCard(
                    enabled = current.laundry.modeEnabled,
                    invites = current.laundry.invites,
                    busy = actionBusy,
                    onToggle = { enabled ->
                        if (actionBusy) return@RiderLaundryModeCard
                        actionBusy = true
                        scope.launch {
                            runCatching { api.setLaundryMode(auth, enabled) }
                                .onSuccess {
                                    actionMessage = if (enabled) "เปิดรับงานฝากซักแล้ว" else "ปิดรับงานฝากซักแล้ว"
                                    snapshot = runCatching { api.riderSnapshot(auth) }.getOrNull()
                                }
                                .onFailure {
                                    actionMessage = it.message ?: "เปลี่ยนโหมดฝากซักไม่สำเร็จ"
                                }
                            actionBusy = false
                        }
                    },
                    onInvite = { invite, accept ->
                        if (actionBusy) return@RiderLaundryModeCard
                        actionBusy = true
                        scope.launch {
                            runCatching { api.laundryInviteAction(auth, invite.inviteId, accept) }
                                .onSuccess {
                                    actionMessage = if (accept) {
                                        "ยอมรับเป็น Rider รับ-ส่งผ้าของร้านแล้ว"
                                    } else {
                                        "ปฏิเสธคำเชิญแล้ว"
                                    }
                                    snapshot = runCatching { api.riderSnapshot(auth) }.getOrNull()
                                }
                                .onFailure {
                                    actionMessage = it.message ?: "ตอบคำเชิญไม่สำเร็จ"
                                }
                            actionBusy = false
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        RiderRecentHistoryCard(recentHistory)
        Spacer(Modifier.height(18.dp))
        }

        RiderBottomNavigation(
            activeTab = activeTab,
            onSelect = { activeTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun RiderBottomNavigation(
    activeTab: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .height(58.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFAFFFFFF)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE6E8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            listOf(
                Triple("home", "หน้าแรก", "home"),
                Triple("chat", "ข้อความ", "support"),
                Triple("earn", "รายได้", "chart"),
                Triple("profile", "โปรไฟล์", "user")
            ).forEach { (key, label, icon) ->
                val selected = activeTab == key
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) QgRedSoft else Color.Transparent)
                        .clickable { onSelect(key) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    QgIcon(
                        icon,
                        Modifier.size(19.dp),
                        if (selected) QgRed else Color(0xFF8D878B)
                    )
                    Text(
                        label,
                        color = if (selected) QgRed else Color(0xFF8D878B),
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun RiderLaundryActiveCard(
    job: RiderLaundryJob,
    busy: Boolean,
    onNavigate: () -> Unit,
    onAction: (String) -> Unit
) {
    val action = when (job.jobStatus) {
        "assigned" -> "arrive"
        "arrived" -> "collect"
        "collected" -> "deliver"
        else -> null
    }
    val actionLabel = when (action) {
        "arrive" -> "ถึงจุดรับแล้ว"
        "collect" -> "รับผ้าแล้ว"
        "deliver" -> "ส่งถึงปลายทางแล้ว"
        else -> "กำลังอัปเดต"
    }
    val currentAddress = if (job.isCollected) job.toAddress else job.fromAddress

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE5E8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "งานฝากซัก",
                        color = QgRed,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(job.numberLabel, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        job.shopName ?: job.hubName ?: "ร้านซัก",
                        color = QgMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(QgRedSoft)
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Text("฿" + "%.0f".format(job.jobFee), color = QgRed, fontWeight = FontWeight.Black)
                }
            }

            Spacer(Modifier.height(9.dp))
            Text(job.routeLabel, fontWeight = FontWeight.Bold)
            Text(
                currentAddress ?: "พิกัดปลายทางยังไม่พร้อม",
                color = QgMuted,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(9.dp))
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF6F7))
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text("เส้นทางงาน", color = QgMuted, style = MaterialTheme.typography.labelSmall)
                    Text(
                        (job.fromAddress ?: "-") + " → " + (job.toAddress ?: "-"),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    job.serviceName?.let {
                        Spacer(Modifier.height(3.dp))
                        Text(it, color = QgMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(9.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigate,
                    enabled = !busy && job.navigationTarget != null,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    QgIcon("location", Modifier.size(18.dp), QgRed)
                    Spacer(Modifier.width(6.dp))
                    Text("นำทาง", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { action?.let(onAction) },
                    enabled = !busy && action != null,
                    modifier = Modifier.weight(1.25f).height(50.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(actionLabel, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun RiderLaundryPoolCard(
    jobs: List<RiderLaundryJob>,
    busy: Boolean,
    onClaim: (RiderLaundryJob) -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE5E8))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("งานฝากซักที่รับได้", fontWeight = FontWeight.Black)
                    Text(
                        "เฉพาะร้านที่คุณยอมรับเป็น Rider แล้ว",
                        color = QgMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                QgStatusPill(jobs.size.toString() + " งาน", true)
            }

            jobs.take(5).forEachIndexed { index, job ->
                if (index > 0) {
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.HorizontalDivider(color = QgLine)
                    Spacer(Modifier.height(10.dp))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (job.leg == "pickup") "รับผ้าจากลูกค้า" else "ส่งผ้าคืนลูกค้า") +
                                " · " + job.numberLabel,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            (job.shopName ?: job.hubName ?: "ร้านซัก") +
                                " · " + (job.serviceName ?: "บริการฝากซัก"),
                            color = QgMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            (job.fromAddress ?: "-") + " → " + (job.toAddress ?: "-"),
                            color = QgMuted,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("฿" + "%.0f".format(job.jobFee), color = QgRed, fontWeight = FontWeight.Black)
                        Button(
                            onClick = { onClaim(job) },
                            enabled = !busy,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("รับงาน", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RiderLaundryModeCard(
    enabled: Boolean,
    invites: List<RiderLaundryInvite>,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onInvite: (RiderLaundryInvite, Boolean) -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE5E8))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("โหมดงานฝากซัก", fontWeight = FontWeight.Black)
                    Text(
                        if (enabled) "พร้อมรับงานรับ-ส่งผ้าจากร้านที่เชื่อมไว้" else "ปิดรับงานฝากซักอยู่",
                        color = QgMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { onToggle(it) },
                    enabled = !busy
                )
            }

            invites.forEach { invite ->
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.HorizontalDivider(color = QgLine)
                Spacer(Modifier.height(10.dp))
                Text("คำเชิญงานฝากซัก", color = QgRed, fontWeight = FontWeight.Bold)
                Text(invite.shopName.ifBlank { invite.hubName }, fontWeight = FontWeight.Bold)
                Text(invite.hubName, color = QgMuted, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(7.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onInvite(invite, false) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("ปฏิเสธ") }
                    Button(
                        onClick = { onInvite(invite, true) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("ยอมรับ", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun ReturnControlCard(
    overlayAllowed: Boolean,
    onConfigure: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp)) {
            Text("ปุ่ม Q กลับ QueueGo", fontWeight = FontWeight.Bold)
            Text(
                if (overlayAllowed) {
                    "พร้อมแสดงปุ่มลอยขณะเปิด Google Maps"
                } else {
                    "ยังไม่ได้เปิดสิทธิ์ปุ่มลอย · หากไม่เปิดจะใช้การแจ้งเตือนเป็นทางกลับ"
                }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onConfigure, modifier = Modifier.fillMaxWidth()) {
                Text(if (overlayAllowed) "ตรวจการตั้งค่าปุ่มลอย" else "เปิดปุ่มลอย")
            }
        }
    }
}

@Composable
private fun OfferCard(
    job: RiderJob,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onExpired: () -> Unit
) {
    var secondsLeft by remember(job.id, job.offerExpiresAt) { mutableStateOf(30) }

    LaunchedEffect(job.id, job.offerExpiresAt) {
        while (true) {
            val end = runCatching { Instant.parse(job.offerExpiresAt ?: "").toEpochMilli() }.getOrNull()
            secondsLeft = if (end == null) 30 else
                (((end - System.currentTimeMillis()).coerceAtLeast(0L) + 999L) / 1000L).toInt()
            if (secondsLeft <= 0) {
                onExpired()
                break
            }
            delay(250)
        }
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEFDFD)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE5E8))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("งานใหม่", color = QgRed, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelMedium)
                    Text(job.numberLabel, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(QgRedSoft)
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Text(
                        "฿" + "%.0f".format(job.deliveryFee ?: 0.0),
                        color = QgRed,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            RiderJobPlaceRow("store", "รับที่ร้าน", job.pickupAddress ?: "ยังไม่มีที่อยู่ร้าน")
            RiderJobPlaceRow("home", "ส่งลูกค้า", job.deliveryAddress ?: "ยังไม่มีที่อยู่ลูกค้า")

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RiderOfferMetric("ค่ารอบ", "฿" + "%.0f".format(job.deliveryFee ?: 0.0), Modifier.weight(1f))
                RiderOfferMetric("จ่ายร้าน", job.shopCash?.let { "฿" + "%.0f".format(it) } ?: "—", Modifier.weight(1f))
                RiderOfferMetric("เก็บลูกค้า", job.customerCash?.let { "฿" + "%.0f".format(it) } ?: "—", Modifier.weight(1f))
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("ปฏิเสธ", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onAccept,
                    enabled = !busy && secondsLeft > 0,
                    modifier = Modifier.weight(1.25f).height(50.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("รับงาน · " + secondsLeft + " วิ", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun MarketPickupPanel(
    job: RiderJob,
    pickups: List<MarketPickup>,
    busy: Boolean,
    onNavigate: (MarketPickup) -> Unit,
    onVerify: (MarketPickup) -> Unit,
    onStartDelivery: () -> Unit
) {
    val pending = pickups.filter { !it.done }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Text("รับของหลายร้าน", fontWeight = FontWeight.Bold)
            Text(job.numberLabel + " · รับแล้ว " + (pickups.size - pending.size) + "/" + pickups.size + " จุด")
            Spacer(Modifier.height(10.dp))

            pickups.forEachIndexed { index, pickup ->
                Text(
                    "จุดรับ " + (index + 1) + " · " + pickup.shopName,
                    fontWeight = FontWeight.SemiBold
                )
                if (!pickup.shopAddress.isNullOrBlank()) Text(pickup.shopAddress)
                Text("ยอดร้าน ฿" + "%.0f".format(pickup.shopAmount))
                Spacer(Modifier.height(6.dp))
                if (pickup.done) {
                    Text(
                        if (pickup.status.uppercase() == "CANCELLED") "ยกเลิกจุดรับนี้" else "รับสินค้าแล้ว ✓",
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { onNavigate(pickup) },
                            enabled = !busy && pickup.latitude != null && pickup.longitude != null,
                            modifier = Modifier.weight(1f)
                        ) { Text("นำทาง") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onVerify(pickup) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text("ถ่ายรูปและรับ") }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (pickups.isNotEmpty() && pending.isEmpty()) {
                Button(
                    onClick = onStartDelivery,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("รับครบแล้ว · เริ่มจัดส่ง") }
            }
        }
    }
}

@Composable
private fun ActiveJobCard(
    job: RiderJob,
    busy: Boolean,
    onChat: () -> Unit,
    onNavigate: () -> Unit,
    onArriveShop: () -> Unit,
    onPickupVerify: () -> Unit,
    onStartDelivery: () -> Unit,
    onArriveCustomer: () -> Unit,
    onDeliveryVerify: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEFDFD)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE5E8))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("กำลังทำงาน", color = QgRed, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelMedium)
                    Text(job.numberLabel, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(QgRedSoft)
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Text(
                        riderStatusLabel(job.status),
                        color = QgRed,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(Modifier.height(7.dp))
            RiderJobPlaceRow("store", "ร้าน", job.pickupAddress ?: "ยังไม่มีที่อยู่ร้าน")
            RiderJobPlaceRow("home", "ลูกค้า", job.deliveryAddress ?: "ยังไม่มีที่อยู่ลูกค้า")

            job.deliveryFee?.let {
                Spacer(Modifier.height(7.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFAF6F7), RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text("รายได้ค่าส่ง", color = QgMuted, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.weight(1f))
                    Text("฿" + "%.0f".format(it), color = QgRed, fontWeight = FontWeight.Black)
                }
            }

            if (job.marketOrderId == null && job.status in setOf("rider_assigned", "assigned", "preparing", "ready")) {
                job.shopCash?.let {
                    Spacer(Modifier.height(7.dp))
                    RiderCashPanel("เงินสดที่ต้องจ่ายร้าน", it, "แสดงยอดเท่านั้น ไม่มีปุ่มยืนยันเงิน")
                }
            }
            if (job.status == "in_progress") {
                job.customerCash?.let {
                    Spacer(Modifier.height(7.dp))
                    RiderCashPanel("เงินสดที่ต้องเก็บจากลูกค้า", it, "ส่งสินค้าและถ่ายรูปจบงานได้เลย")
                }
            }

            Spacer(Modifier.height(9.dp))
            OutlinedButton(
                onClick = onChat,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("แชทกับลูกค้า", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(7.dp))

            when (job.status) {
                "rider_assigned", "assigned", "preparing", "ready" -> {
                    Button(
                        onClick = onNavigate,
                        enabled = !busy && job.navigationTarget != null,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text("นำทางไปร้าน", fontWeight = FontWeight.Black) }
                    Spacer(Modifier.height(7.dp))
                    if (!job.shopArrived) {
                        OutlinedButton(
                            onClick = onArriveShop,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("ถึงร้านแล้ว", fontWeight = FontWeight.Bold) }
                    } else if (job.status == "ready") {
                        Button(
                            onClick = onPickupVerify,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) { Text("ตรวจสอบรายการ", fontWeight = FontWeight.Black) }
                    } else {
                        Box(
                            Modifier.fillMaxWidth().background(QgRedSoft, RoundedCornerShape(14.dp)).padding(10.dp)
                        ) {
                            Text("ถึงร้านแล้ว · รอร้านเตรียมสินค้า", color = QgRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                "picked_up" -> {
                    Button(
                        onClick = onStartDelivery,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text("เริ่มจัดส่งและนำทาง", fontWeight = FontWeight.Black) }
                }
                "in_progress" -> {
                    Button(
                        onClick = onNavigate,
                        enabled = !busy && job.navigationTarget != null,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text("นำทางไปลูกค้า", fontWeight = FontWeight.Black) }
                    Spacer(Modifier.height(7.dp))
                    if (!job.customerArrived) {
                        OutlinedButton(
                            onClick = onArriveCustomer,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("ถึงลูกค้าแล้ว", fontWeight = FontWeight.Bold) }
                    } else {
                        Button(
                            onClick = onDeliveryVerify,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) { Text("ตรวจสอบและส่งมอบสินค้า", fontWeight = FontWeight.Black) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RiderBlueprintTopBar(name: String, active: Boolean, online: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFAFFFFFF)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE7E9))
        ) {
            Row(Modifier.padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(QgRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Q", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(7.dp))
                Column {
                    QueueGoBrand()
                    Text(name, color = QgMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFAFFFFFF)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE7E9))
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (online && active) Color(0xFF18B968) else Color(0xFFB7B2B4))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (online && active) "ออนไลน์" else "ออฟไลน์",
                    color = Color(0xFF756E72),
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun RiderJobPlaceRow(icon: String, label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .background(QgRedSoft, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            QgIcon(icon, Modifier.size(22.dp), QgRed)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = QgMuted, style = MaterialTheme.typography.labelSmall)
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
    }
}

@Composable
private fun RiderOfferMetric(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .background(Color(0xFFFAF7F8), RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = QgMuted, style = MaterialTheme.typography.labelSmall)
        Text(value, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RiderCashPanel(title: String, amount: Double, note: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(QgRedSoft, RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Text(title, color = QgMuted, style = MaterialTheme.typography.labelSmall)
        Text("฿" + "%.0f".format(amount), color = QgRed, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        Text(note, color = QgMuted, style = MaterialTheme.typography.labelSmall)
    }
}

private fun riderStatusLabel(status: String): String = when (status) {
    "rider_assigned", "assigned" -> "ไปร้าน"
    "preparing" -> "ร้านกำลังเตรียม"
    "ready" -> "พร้อมรับ"
    "picked_up" -> "รับสินค้าแล้ว"
    "in_progress" -> "กำลังจัดส่ง"
    else -> status
}

@Composable
private fun VerificationScreen(
    modifier: Modifier,
    mode: String,
    job: RiderJob,
    marketPickup: MarketPickup?,
    items: List<RiderItem>,
    photoReady: Boolean,
    busy: Boolean,
    message: String?,
    onBack: () -> Unit,
    onCamera: () -> Unit,
    onConfirm: () -> Unit
) {
    val pickup = mode == "pickup"
    val marketPickupMode = mode == "marketPickup"
    Column(
        modifier.fillMaxSize().background(QgRiderBg).verticalScroll(rememberScrollState()).padding(14.dp)
    ) {
        OutlinedButton(onClick = onBack, enabled = !busy) { Text("ย้อนกลับ") }
        Spacer(Modifier.height(14.dp))
        Text(
            when {
                marketPickupMode -> "ตรวจจุดรับและถ่ายรูป"
                pickup -> "ตรวจสอบรายการและถ่ายรูป"
                else -> "ตรวจสอบและส่งมอบสินค้า"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(job.numberLabel)
        Text(
            when {
                marketPickupMode -> "ตรวจร้านและยอดที่ต้องรับสินค้า แล้วถ่ายรูปหลักฐานในหน้าเดียว"
                pickup -> "เช็กชื่อสินค้าและจำนวนให้ครบ แล้วถ่ายรูปหลักฐานในหน้าเดียว"
                else -> "ตรวจรายการและถ่ายรูปตอนส่งสินค้าในหน้าเดียว"
            }
        )
        if (marketPickupMode && marketPickup != null) {
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp)) {
                    Text(marketPickup.shopName, fontWeight = FontWeight.Bold)
                    if (!marketPickup.shopAddress.isNullOrBlank()) Text(marketPickup.shopAddress)
                    Text("ยอดร้าน ฿" + "%.0f".format(marketPickup.shopAmount))
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        if (!marketPickupMode) Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp)) {
                Text("รายการสินค้า", fontWeight = FontWeight.Bold)
                if (items.isEmpty()) {
                    Text("ไม่พบรายการสินค้า")
                } else {
                    items.forEach { item ->
                        Spacer(Modifier.height(8.dp))
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        if (!item.description.isNullOrBlank()) Text(item.description)
                        Text("จำนวน " + item.quantity + " · ฿" + "%.0f".format(item.totalPrice))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    when {
                        marketPickupMode -> "รูปตอนรับสินค้าจุดนี้ · จำเป็น"
                        pickup -> "รูปตอนรับสินค้า · จำเป็น"
                        else -> "รูปตอนส่งสินค้า · จำเป็น"
                    },
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when {
                        marketPickupMode -> "ให้เห็นสินค้าที่รับจากร้านนี้ชัดเจน"
                        pickup -> "ให้เห็นสินค้าที่ตรวจรับจากร้านชัดเจน"
                        else -> "ให้เห็นสินค้าและจุดส่งชัดเจน ไม่ต้องถ่ายหน้าลูกค้า"
                    }
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onCamera,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (photoReady) "ถ่ายใหม่" else "เปิดกล้องถ่ายรูป")
                }
                if (photoReady) {
                    Spacer(Modifier.height(8.dp))
                    Text("ถ่ายรูปหลักฐานแล้ว ✓", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (message != null) {
            Spacer(Modifier.height(10.dp))
            Text(message)
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onConfirm,
            enabled = photoReady && !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) {
                CircularProgressIndicator()
            } else {
                Text(
                    when {
                        marketPickupMode -> "ยืนยันรับสินค้าจุดนี้"
                        pickup -> "ยืนยันรับสินค้า"
                        else -> "ยืนยันส่งสินค้า"
                    }
                )
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun lastKnownLocation(context: Context): Pair<Double, Double>? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return runCatching {
        manager.getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { it.latitude to it.longitude }
    }.getOrNull()
}

private fun createEvidenceUri(context: Context): Uri {
    val dir = File(context.cacheDir, "queuego-evidence").apply { mkdirs() }
    val file = File.createTempFile("qg-proof-", ".jpg", dir)
    return FileProvider.getUriForFile(
        context,
        context.packageName + ".fileprovider",
        file
    )
}

private fun openLaundryNavigation(context: Context, job: RiderLaundryJob) {
    val target = job.navigationTarget ?: return
    RiderReturnService.start(context)
    val navigation = Uri.parse(
        "google.navigation:q=" + target.first + "," + target.second + "&mode=d"
    )
    val mapsIntent = Intent(Intent.ACTION_VIEW, navigation).apply {
        setPackage("com.google.android.apps.maps")
    }
    val opened = runCatching { context.startActivity(mapsIntent) }.isSuccess
    if (!opened) {
        val web = Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination=" +
                target.first + "," + target.second
        )
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, web)) }
    }
}

private fun openNavigation(context: Context, job: RiderJob) {
    val target = job.navigationTarget ?: return
    RiderReturnService.start(context)
    val navigation = Uri.parse(
        "google.navigation:q=" + target.first + "," + target.second + "&mode=d"
    )
    val mapsIntent = Intent(Intent.ACTION_VIEW, navigation).apply {
        setPackage("com.google.android.apps.maps")
    }
    val opened = runCatching { context.startActivity(mapsIntent) }.isSuccess
    if (!opened) {
        val web = Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination=" +
                target.first + "," + target.second
        )
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, web)) }
    }
}
