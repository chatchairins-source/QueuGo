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
import com.queuego.shared.QueueGoVoiceCallOverlay
import com.queuego.shared.NativeVoiceCallController
import com.queuego.shared.NativeVoiceControllerState
import com.queuego.shared.NativeUser
import com.queuego.shared.NativeSession
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgAccountDeletionSection
import com.queuego.shared.QueueGoBrand
import com.queuego.shared.QgStatusPill
import com.queuego.shared.QgRed
import com.queuego.shared.QgMuted
import com.queuego.shared.QgBg
import com.queuego.shared.QgIcon
import com.queuego.shared.QgRemoteImage
import com.queuego.shared.QgLine
import com.queuego.shared.QgRedSoft
import com.queuego.shared.QgRiderBg
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.queuego.shared.NativeOrderRealtime
import com.queuego.shared.riderRealtimeSubscriptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.widthIn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.time.Instant

@Composable
fun QueueGoRiderApp(pushTarget: NativePushLaunchTarget? = null) {
    val context = LocalContext.current
    val api = remember { QueueGoApi() }
    val store = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()

    var auth by remember { mutableStateOf<QueueGoAuth?>(null) }
    var restoring by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var restorationError by remember { mutableStateOf(false) }
    var restorationAttempt by remember { mutableStateOf(0) }
    var registrationOpen by remember { mutableStateOf(false) }

    LaunchedEffect(restorationAttempt) {
        restoring = true
        restorationError = false
        val cached = store.load()
        if (cached != null) {
            runCatching { api.validate(cached).also { store.save(it) } }
                .onSuccess {
                    auth = it
                    registrationOpen = store.pendingRegistrationUserId() == it.session.authUserId
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    if (shouldClearRiderSession(it)) {
                        store.clear()
                        error = it.message
                    } else {
                        restorationError = true
                    }
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
                store.save(validated)
                validated
            }
            result.onSuccess { validated ->
                auth = validated
            }.onFailure {
                if (it is CancellationException) throw it
                if (shouldClearRiderSession(it)) {
                    RiderReturnService.stop(context)
                    store.clear()
                    auth = null
                    error = "Session นี้ถูกยกเลิก หมดอายุ หรือเปิดจากอุปกรณ์อื่น"
                }
            }
            if (auth == null) break
        }
    }

    QueueGoTheme {
        Scaffold(containerColor = QgBg) { insets ->
            when {
                restoring -> LoadingScreen(Modifier.padding(insets))
                restorationError -> RiderConnectionRecoveryScreen(Modifier.padding(insets)) {
                    restorationAttempt += 1
                }
                registrationOpen -> RiderRegistrationScreen(
                    modifier = Modifier.padding(insets), store = store, busy = busy, error = error,
                    resuming = auth != null,
                    onBack = { if (!busy) { registrationOpen = false; error = null } },
                    onSubmit = { form, password, documents ->
                        if (!busy) {
                            busy = true
                            error = null
                            scope.launch {
                                try {
                                    val result = api.registerRider(form, password, documents, store.deviceId(), auth) { checkpoint ->
                                        store.saveRegistrationCheckpoint(checkpoint)
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { auth = checkpoint }
                                    }
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.finishRegistration() }
                                    store.save(result)
                                    auth = result
                                    registrationOpen = false
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    error = e.message ?: "สมัครสมาชิกไม่สำเร็จ"
                                } finally { busy = false }
                            }
                        }
                    }
                )
                auth == null -> LoginScreen(
                    modifier = Modifier.padding(insets),
                    busy = busy,
                    error = error,
                    onRegister = { registrationOpen = true; error = null },
                    onLogin = { id, password ->
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                val signed = api.signIn(id, password, store.deviceId())
                                store.save(signed)
                                auth = signed
                                if (signed.user.status == "pending") {
                                    val draft = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.loadRegistrationDraft() }
                                    if (draft?.optJSONObject("form")?.optString("phone")?.replace(Regex("[-\\s]"), "") == signed.user.phone &&
                                        (store.pendingRegistrationUserId() == signed.session.authUserId || api.registrationProfileMissing(signed))) {
                                        store.saveRegistrationCheckpoint(signed)
                                        registrationOpen = true
                                    }
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                error = e.message ?: "เข้าสู่ระบบไม่สำเร็จ"
                            } finally { busy = false }
                        }
                    }
                )
                auth?.user?.status != "active" -> RiderPendingAccountScreen(
                    modifier = Modifier.padding(insets), name = auth!!.user.name,
                    onLogout = {
                        val current = auth!!
                        RiderReturnService.stop(context)
                        store.clear()
                        auth = null
                        scope.launch {
                            runCatching { disableRiderNativePush(current, api, store) }
                            runCatching { api.revoke(current.session) }
                        }
                    }
                )
                else -> RiderHome(
                    modifier = Modifier.padding(insets),
                    auth = auth!!,
                    api = api,
                    pushTarget = pushTarget,
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
    onLogin: (String, String) -> Unit,
    onRegister: () -> Unit
) {
    RiderLoginScreen(modifier, busy, error, onLogin, onRegister)
}


private fun QueueGoAuth.asNativeVoiceAuth(): NativeAuth = NativeAuth(
    session = NativeSession(
        authUserId = session.authUserId,
        accessToken = session.accessToken,
        refreshToken = session.refreshToken,
        expiresAtMs = session.expiresAtMs,
        sessionId = session.sessionId
    ),
    user = NativeUser(
        id = user.id,
        authUserId = session.authUserId,
        name = user.name,
        role = user.role,
        status = user.status
    )
)

@Composable
private fun RiderVoiceOverlay(
    state: NativeVoiceControllerState,
    auth: NativeAuth,
    controller: NativeVoiceCallController,
    onAnswer: () -> Unit
) {
    QueueGoVoiceCallOverlay(
        state = state,
        currentUserId = auth.user.id,
        onAnswer = onAnswer,
        onDecline = { controller.declineIncoming(auth) },
        onHangUp = { controller.hangUp() },
        onDismissEnded = { controller.dismissTerminal() },
        onToggleMute = { controller.setMuted(it) },
        onToggleSpeaker = { controller.setSpeakerEnabled(it) }
    )
}

@Composable
private fun RiderHome(
    modifier: Modifier,
    auth: QueueGoAuth,
    api: QueueGoApi,
    pushTarget: NativePushLaunchTarget? = null,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nativeVoiceAuth = remember(
        auth.user.id,
        auth.session.accessToken,
        auth.session.sessionId,
        auth.session.expiresAtMs
    ) { auth.asNativeVoiceAuth() }
    val voiceController = remember(auth.user.id) { NativeVoiceCallController(context) }
    val voiceState by voiceController.state.collectAsState()
    DisposableEffect(voiceController) { onDispose { voiceController.close() } }
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
    LaunchedEffect(actionMessage) {
        if (actionMessage != null) {
            delay(4_000)
            actionMessage = null
        }
    }
    var detailJob by remember { mutableStateOf<RiderJob?>(null) }
    var homeDetails by remember { mutableStateOf<RiderVerificationDetails?>(null) }
    LaunchedEffect(snapshot?.activeJob?.id, auth.session.accessToken) {
        homeDetails = null
        snapshot?.activeJob?.let { job ->
            try {
                homeDetails = api.verificationDetails(auth, job.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Work controls remain available if metadata fails.
            }
        }
    }
    var chatJob by remember { mutableStateOf<RiderJob?>(null) }
    var verifyMode by remember { mutableStateOf<String?>(null) }
    var verifyJob by remember { mutableStateOf<RiderJob?>(null) }
    var verifyDetails by remember { mutableStateOf<RiderVerificationDetails?>(null) }
    var verifyItems by remember { mutableStateOf<List<RiderItem>>(emptyList()) }
    var verifyMarketPickup by remember { mutableStateOf<MarketPickup?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var lastOfferAlertKey by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableStateOf("home") }
    var pendingVoiceOrderId by remember { mutableStateOf<String?>(null) }
    var pendingVoiceAnswer by remember { mutableStateOf(false) }
    val voicePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (pendingVoiceAnswer) {
                voiceController.answerIncoming(nativeVoiceAuth)
            } else {
                pendingVoiceOrderId?.let { orderId ->
                    voiceController.startOutgoing(nativeVoiceAuth, orderId, "customer")
                }
            }
        } else {
            actionMessage = "กรุณาอนุญาตไมโครโฟนเพื่อโทรผ่าน QueueGo"
        }
        pendingVoiceOrderId = null
        pendingVoiceAnswer = false
    }

    fun startCustomerVoice(orderId: String) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceController.startOutgoing(nativeVoiceAuth, orderId, "customer")
        } else {
            pendingVoiceOrderId = orderId
            pendingVoiceAnswer = false
            voicePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun answerVoiceCall() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceController.answerIncoming(nativeVoiceAuth)
        } else {
            pendingVoiceOrderId = null
            pendingVoiceAnswer = true
            voicePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val badgePreferences = remember(context) { context.getSharedPreferences("rider_chat_seen", Context.MODE_PRIVATE) }
    val badgeTracker = remember(auth.user.id, auth.session.sessionId) { RiderMessageBadgeTracker(auth.user.id) }
    val badgeApi = remember { RiderMessageBadgeApi() }
    var unreadMessages by remember(auth.user.id, auth.session.sessionId) { mutableStateOf(0) }
    var messageNotice by remember(auth.user.id, auth.session.sessionId) { mutableStateOf<String?>(null) }
    var messageNoticeVersion by remember(auth.user.id, auth.session.sessionId) { mutableStateOf(0) }
    val badgeActive by rememberUpdatedState(snapshot?.activeJob?.id)
    val badgeHistory by rememberUpdatedState(recentHistory)
    val badgeChat by rememberUpdatedState(chatJob?.id)
    val badgeInbox by rememberUpdatedState(activeTab == "chat")
    LaunchedEffect(auth.user.id, auth.session.sessionId, auth.session.accessToken, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val active = badgeActive
                val ids = (listOfNotNull(active) + badgeHistory.map { it.id }).distinct().take(100)
                try {
                    val (rooms, messages) = badgeApi.read(auth, ids)
                    currentCoroutineContext().ensureActive()
                    if (active == badgeActive) {
                        val now = System.currentTimeMillis()
                        if (badgeInbox) badgePreferences.edit().putLong(auth.user.id, now).apply()
                        val (count, notice) = badgeTracker.update(active, badgeChat, badgeInbox, rooms,
                            messages, badgePreferences.getLong(auth.user.id, 0L), now)
                        unreadMessages = count
                        if (notice != null) {
                            messageNotice = notice
                            messageNoticeVersion++
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Keep the last authoritative count during a failed read; retry on the next tick.
                }
                delay(if (badgeActive == null) 15_000 else 5_000)
            }
        }
    }
    LaunchedEffect(messageNoticeVersion) {
        if (messageNotice != null) {
            delay(3_800)
            messageNotice = null
        }
    }
    LaunchedEffect(chatJob?.id, activeTab, auth.user.id) {
        if (activeTab == "chat") badgePreferences.edit().putLong(auth.user.id, System.currentTimeMillis()).apply()
        if (chatJob != null) unreadMessages = 0
    }

    var recenterSignal by remember { mutableStateOf(0) }
    var mapReady by remember { mutableStateOf(false) }
    var pendingMapRecenter by remember { mutableStateOf(false) }
    val navigationController = remember(auth.user.id) { RiderNavigationController() }
    val navigationState by navigationController.state.collectAsState()
    val navigationSpeaker = remember(auth.user.id) { RiderNavigationSpeaker(context) }
    DisposableEffect(navigationController, navigationSpeaker) {
        onDispose {
            navigationSpeaker.close()
            navigationController.close()
        }
    }
    LaunchedEffect(navigationState) {
        navigationSpeaker.speakIfNeeded(navigationState)
    }
    LaunchedEffect(snapshot?.activeJob, snapshot?.marketPickups) {
        navigationController.setTarget(
            snapshot?.activeJob?.let { riderNavigationTarget(it, snapshot?.marketPickups.orEmpty()) }
        )
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it }
        actionMessage = if (granted && pendingMapRecenter) {
            recenterSignal += 1
            if (lastKnownLocation(context) == null) "ยังไม่ได้ตำแหน่ง GPS" else "อนุญาตตำแหน่งแล้ว"
        } else if (granted) {
            "อนุญาตตำแหน่งแล้ว กดปุ่มเดิมอีกครั้ง"
        } else {
            "ยังไม่ได้รับสิทธิ์ตำแหน่ง"
        }
        pendingMapRecenter = false
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
            runCatching {
                coroutineScope {
                    val items = async { api.orderItems(auth, job.id) }
                    val details = async { api.verificationDetails(auth, job.id) }
                    items.await() to details.await()
                }
            }
                .onSuccess {
                    verifyMode = mode
                    verifyJob = job
                    verifyItems = it.first
                    verifyDetails = it.second
                    photoUri = null
                    pendingCameraUri = null
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    actionMessage = it.message ?: "โหลดรายการไม่สำเร็จ"
                }
            actionBusy = false
        }
    }

    fun openMarketPickupVerification(job: RiderJob, pickup: MarketPickup) {
        verifyMode = "marketPickup"
        verifyJob = job
        verifyMarketPickup = pickup
        verifyItems = emptyList()
        verifyDetails = null
        photoUri = null
        pendingCameraUri = null
        actionMessage = null
    }

    fun closeVerification() {
        verifyMode = null
        verifyJob = null
        verifyItems = emptyList()
        verifyDetails = null
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

    LaunchedEffect(pushTarget?.eventId) {
        if (pushTarget == null) return@LaunchedEffect
        chatJob = null
        closeVerification()
        activeTab = "home"
        refreshSnapshot()
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
    LaunchedEffect(
        nativeVoiceAuth.session.accessToken,
        nativeVoiceAuth.session.sessionId,
        lifecycle
    ) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try {
                    voiceController.refreshIncoming(nativeVoiceAuth)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Native push plus foreground polling provide recovery without blocking Rider work.
                }
                delay(3_000)
            }
        }
    }

    if (chatJob != null) {
        Box(modifier.fillMaxSize()) {
            RiderChatScreen(
                auth = auth,
                job = chatJob!!,
                onBack = { chatJob = null },
                modifier = Modifier.fillMaxSize()
            )
            RiderVoiceOverlay(
                state = voiceState,
                auth = nativeVoiceAuth,
                controller = voiceController,
                onAnswer = ::answerVoiceCall
            )
        }
        return
    }

    if (verifyMode != null && verifyJob != null) {
        Box(modifier.fillMaxSize()) {
        VerificationScreen(
            modifier = Modifier.fillMaxSize(),
            mode = verifyMode!!,
            job = verifyJob!!,
            marketPickup = verifyMarketPickup,
            items = verifyItems,
            details = verifyDetails,
            photoUri = photoUri,
            busy = actionBusy,
            message = actionMessage,
            onBack = { closeVerification() },
            onCamera = { openCamera() },
            onChat = { val job = verifyJob; closeVerification(); chatJob = job },
            onCallCustomer = {
                verifyJob?.id?.let(::startCustomerVoice)
            },
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
        RiderVoiceOverlay(
            state = voiceState,
            auth = nativeVoiceAuth,
            controller = voiceController,
            onAnswer = ::answerVoiceCall
        )
        }
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
                    onBack = { activeTab = "home" },
                    modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 748.dp).fillMaxSize()
                )
                "profile" -> RiderProfileScreen(
                    auth = auth,
                    snapshot = snapshot,
                    history = recentHistory,
                    api = api,
                    onSnapshot = { snapshot = it },
                    onNavigationSettings = { configureReturnControl() },
                    onLogout = onLogout,
                    modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 748.dp).fillMaxSize()
                )
            }
            RiderBottomNavigation(
                activeTab = activeTab,
                unreadMessages = unreadMessages,
                onSelect = { activeTab = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            actionMessage?.let { RiderFeedbackToast(it, Modifier.align(Alignment.BottomCenter)) }
            messageNotice?.let { RiderMessageNotice(it, Modifier.align(Alignment.TopCenter)) }
            RiderVoiceOverlay(
                state = voiceState,
                auth = nativeVoiceAuth,
                controller = voiceController,
                onAnswer = ::answerVoiceCall
            )
        }
        return
    }

    fun changeOnline(enabled: Boolean) {
        if (actionBusy || snapshot?.activeJob != null || snapshot == null) return
        if (enabled && !ensureLocationPermission()) return
        actionBusy = true
        scope.launch {
            val loc = lastKnownLocation(context)
            runCatching { api.setOnline(auth, enabled, loc?.first, loc?.second) }
                .onSuccess {
                    actionMessage = if (enabled) "เปิดรับงานแล้ว" else "ปิดรับงานแล้ว"
                    runCatching { api.riderSnapshot(auth) }.onSuccess { snapshot = it }
                        .onFailure { actionMessage = it.message ?: "โหลดสถานะรับงานไม่สำเร็จ" }
                }
                .onFailure { actionMessage = it.message ?: "เปลี่ยนสถานะรับงานไม่สำเร็จ" }
            actionBusy = false
        }
    }

    val shortHome = LocalConfiguration.current.screenHeightDp <= 760
    val mapJob = snapshot?.activeJob ?: snapshot?.offeredJob

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(QgRiderBg)
    ) {
        RiderLongdoMap(
            modifier = Modifier.fillMaxSize(),
            job = mapJob,
            marketPickups = snapshot?.marketPickups.orEmpty(),
            navigationState = navigationState,
            recenterSignal = recenterSignal,
            onLocationFix = navigationController::onLocationFix,
            onMapReady = { mapReady = it }
        )

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 740.dp)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            RiderBlueprintTopBar(
                active = auth.user.status == "active",
                online = snapshot?.online == true,
                enabled = !actionBusy && snapshot != null && snapshot?.activeJob == null,
                onToggle = { changeOnline(snapshot?.online != true) }
            )
            if (navigationState.active) {
                Spacer(Modifier.height(7.dp))
                Card(
                    shape = RoundedCornerShape(15.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xF5FFFFFF)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE5E8))
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp)) {
                        val nextText = navigationState.nextInstruction?.turnCode?.let(::riderTurnPhrase)
                        Text(
                            when {
                                navigationState.loading && !navigationState.routeReady -> "กำลังคำนวณเส้นทาง…"
                                navigationState.weakGps -> "สัญญาณ GPS อ่อน · กำลังรักษาเส้นทางเดิม"
                                navigationState.offRoute -> "ออกนอกเส้นทาง · กำลังคำนวณใหม่"
                                nextText != null && navigationState.nextInstruction?.turnCode != 9 -> nextText
                                else -> "นำทางไป" + navigationState.targetLabel
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                navigationState.distanceRemainingMeters?.let {
                                    if (it >= 1000.0) "%.1f กม.".format(it / 1000.0) else "%.0f ม.".format(it)
                                } ?: "—",
                                color = QgMuted,
                                fontSize = 10.sp
                            )
                            Text(
                                navigationState.etaSeconds?.let {
                                    "ประมาณ " + kotlin.math.ceil(it / 60.0).toInt().coerceAtLeast(1) + " นาที"
                                } ?: "ETA —",
                                color = QgMuted,
                                fontSize = 10.sp
                            )
                            navigationState.headingDegrees?.let {
                                Text("ทิศ %.0f°".format(it), color = QgMuted, fontSize = 10.sp)
                            }
                        }
                        if (navigationState.routeError != null) {
                            Text(navigationState.routeError!!, color = QgRed, fontSize = 9.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                OutlinedButton(
                                    onClick = { navigationController.retry() },
                                    modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                                ) { Text("ลองเส้นทางอีกครั้ง", fontSize = 9.sp) }
                                OutlinedButton(
                                    onClick = { snapshot?.activeJob?.let { openExternalNavigation(context, it) } },
                                    modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                                ) { Text("เปิดแผนที่ภายนอก", fontSize = 9.sp) }
                            }
                        }
                    }
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
                .padding(top = 64.dp, end = 10.dp)
                .size(44.dp)
                .clickable {
                    when {
                        !mapReady -> actionMessage = "แผนที่ยังไม่พร้อม เปิดนำทางภายนอกจากงานได้"
                        !hasLocationPermission(context) -> {
                            pendingMapRecenter = true
                            ensureLocationPermission()
                        }
                        lastKnownLocation(context) == null -> actionMessage = "ยังไม่ได้ตำแหน่ง GPS"
                        else -> recenterSignal += 1
                    }
                },
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
                .padding(bottom = 68.dp)
                .widthIn(max = 676.dp)
                .fillMaxWidth()
                .heightIn(max = (maxHeight - 132.dp).coerceAtLeast(0.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
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
                if (current.activeJob == null && current.offeredJob == null) {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFCFFFFFF)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE5E8))) {
                    Column(Modifier.padding(if (shortHome) 10.dp else 12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Box(Modifier.size(44.dp).background(QgRedSoft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                                QgIcon(if (current.online) "home" else "location", Modifier.size(24.dp), QgRed)
                            }
                            Column {
                                Text(if (current.online) "พร้อมรับงานแล้ว" else "พร้อมรับงานเมื่อคุณพร้อม", fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black)
                                Text(if (current.online) {
                                    if (lastKnownLocation(context) != null) "กำลังค้นหางานใกล้คุณ" else "กำลังรอตำแหน่ง GPS"
                                } else "เปิดรับงานแล้วระบบจะค้นหางานใกล้ตำแหน่งปัจจุบัน", color = QgMuted, fontSize = 10.sp)
                            }
                        }
                        if (current.online) {
                        Spacer(Modifier.height(if (shortHome) 7.dp else 9.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("วันนี้" to (todaySummary?.jobs?.let { "$it งาน" } ?: "— งาน"),
                                "รายได้" to (todaySummary?.income?.let { "฿%.0f".format(it) } ?: "฿—"),
                                "ออนไลน์" to (todaySummary?.onlineHours?.let { "%.1f ชม.".format(it) } ?: "— ชม.")
                            ).forEach { (label, value) ->
                                Column(Modifier.weight(1f).background(Color(0xFFFAF6F7), RoundedCornerShape(15.dp)).padding(horizontal = 6.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(label, fontSize = 8.sp, color = QgMuted)
                                    Text(value, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        }
                    if (current.online) {
                        OutlinedButton(onClick = { changeOnline(false) }, enabled = !actionBusy,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 46.dp),
                            shape = RoundedCornerShape(15.dp)) { Text("ปิดรับงาน", fontSize = 15.sp, fontWeight = FontWeight.Black) }
                    } else {
                        Button(onClick = { changeOnline(true) }, enabled = !actionBusy,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 46.dp),
                            shape = RoundedCornerShape(15.dp)) { Text("เปิดรับงาน", fontSize = 15.sp, fontWeight = FontWeight.Black) }
                    }
                    if (current.online) Text("อยู่ใกล้ย่านร้านอาหาร ตลาด และร้านค้าที่เข้าร่วม จะช่วยให้ได้รับงานเร็วขึ้น",
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp).background(Color(0xFFFFF4F6), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                        color = Color(0xFF7B6870), fontSize = 9.sp, lineHeight = 13.sp)
                    }
                    }
                }
                Spacer(Modifier.height(12.dp))
                when {
                    current.activeJob != null -> {
                        val activeJob = current.activeJob
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
                            details = homeDetails,
                            busy = actionBusy,
                            onDetails = { detailJob = current.activeJob },
                            onChat = { chatJob = current.activeJob },
                            onNavigate = {
                                val next = current.marketPickups.firstOrNull { !it.done }
                                val target = if (activeJob.marketOrderId != null && !activeJob.isDelivering && next != null)
                                    activeJob.copy(pickupAddress = next.shopAddress, pickupLat = next.latitude, pickupLng = next.longitude)
                                else current.activeJob
                                openNavigation(context, target)
                            },
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
                    current.offeredJob != null -> {
                        OfferCard(
                            job = current.offeredJob,
                            riderLocation = lastKnownLocation(context),
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
                    else -> Unit
                }


            }
        }

        }

        RiderBottomNavigation(
            activeTab = activeTab,
            unreadMessages = unreadMessages,
            onSelect = { activeTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        detailJob?.let { job ->
            RiderOrderDetailsSheet(auth, job, api, onDismiss = { detailJob = null })
        }
        actionMessage?.let { RiderFeedbackToast(it, Modifier.align(Alignment.BottomCenter)) }
        messageNotice?.let { RiderMessageNotice(it, Modifier.align(Alignment.TopCenter)) }
        RiderVoiceOverlay(
            state = voiceState,
            auth = nativeVoiceAuth,
            controller = voiceController,
            onAnswer = ::answerVoiceCall
        )
    }
}

@Composable
private fun RiderFeedbackToast(message: String, modifier: Modifier) {
    Box(modifier.padding(horizontal = 16.dp, vertical = 82.dp).widthIn(max = 430.dp)
        .clip(RoundedCornerShape(14.dp)).background(Color(0xFF19171A)).padding(horizontal = 15.dp, vertical = 11.dp)) {
        Text(message, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun RiderMessageNotice(message: String, modifier: Modifier) {
    Row(
        modifier.padding(horizontal = 14.dp, vertical = 12.dp).widthIn(max = 520.dp).fillMaxWidth()
            .background(Color(0xFF171519), RoundedCornerShape(16.dp)).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Canvas(Modifier.size(6.dp)) { drawCircle(Color(0xFFFF4766)) }
        Column {
            Text("ข้อความใหม่", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(message, color = Color(0xFFD7D1D4), fontSize = 8.sp, maxLines = 2)
        }
    }
}

@Composable
private fun RiderBottomNavigation(
    activeTab: String,
    unreadMessages: Int,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier
            .widthIn(max = 736.dp)
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .height(if (LocalConfiguration.current.screenHeightDp <= 760) 54.dp else 58.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFAFFFFFF)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE6E8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(4.dp),
            horizontalArrangement = Arrangement.Start
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
                    Box {
                    Icon(
                        painter = painterResource(when (icon) {
                            "home" -> R.drawable.qg_rider_nav_home
                            "support" -> R.drawable.qg_rider_nav_chat
                            "chart" -> R.drawable.qg_rider_nav_earn
                            else -> R.drawable.qg_rider_nav_user
                        }),
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = if (selected) QgRed else Color(0xFF8D878B)
                    )
                    if (key == "chat" && unreadMessages > 0) {
                        Text(
                            if (unreadMessages > 9) "9+" else unreadMessages.toString(),
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-3).dp)
                                .background(Color(0xFFFF3B30), RoundedCornerShape(99.dp))
                                .padding(horizontal = 4.dp),
                            color = Color.White,
                            fontSize = 8.sp,
                            lineHeight = 15.sp
                        )
                    }
                    }
                    Text(
                        label,
                        modifier = Modifier.padding(top = 2.dp),
                        color = if (selected) QgRed else Color(0xFF8D878B),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 8.sp,
                        lineHeight = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun OfferCard(
    job: RiderJob,
    riderLocation: Pair<Double, Double>?,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onExpired: () -> Unit
) {
    val pickupKm = riderDistanceKm(riderLocation?.first, riderLocation?.second, job.pickupLat, job.pickupLng)
    val deliveryKm = riderDistanceKm(job.pickupLat, job.pickupLng, job.deliveryLat, job.deliveryLng)
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
        Column(Modifier.padding(if (LocalConfiguration.current.screenHeightDp <= 760) 10.dp else 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("งานใหม่ใกล้คุณ", color = QgRed, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                    Text(job.numberLabel, fontWeight = FontWeight.Black, fontSize = 24.sp)
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
                RiderOfferMetric("ระยะทาง", (pickupKm?.let { "%.1f".format(it) } ?: "—") + " กม.", Modifier.weight(1f))
                RiderOfferMetric("เวลาประมาณ", (deliveryKm?.let { kotlin.math.ceil(it / 25.0 * 60.0).toInt().coerceAtLeast(1).toString() } ?: "—") + " นาที", Modifier.weight(1f))
                RiderOfferMetric("รายได้", job.deliveryFee?.let { "฿" + "%.0f".format(it) } ?: "฿—", Modifier.weight(1f), highlight = true)
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
                    Text("ปฏิเสธ", fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
                Button(
                    onClick = onAccept,
                    enabled = !busy && secondsLeft > 0,
                    modifier = Modifier.weight(1.25f).height(50.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("รับงาน · " + secondsLeft + " วิ", fontSize = 15.sp, fontWeight = FontWeight.Black)
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
    details: RiderVerificationDetails?,
    busy: Boolean,
    onDetails: () -> Unit,
    onChat: () -> Unit,
    onNavigate: () -> Unit,
    onArriveShop: () -> Unit,
    onPickupVerify: () -> Unit,
    onStartDelivery: () -> Unit,
    onArriveCustomer: () -> Unit,
    onDeliveryVerify: () -> Unit
) {
    val context = LocalContext.current
    val pickup = job.status in setOf("rider_assigned", "assigned", "preparing", "ready")
    var navigationStarted by remember(job.id, pickup) {
        mutableStateOf(context.getSharedPreferences("rider_navigation", Context.MODE_PRIVATE)
            .getBoolean(job.id + if (pickup) ":shop" else ":customer", false))
    }
    val title = when {
        job.status == "ready" -> "ร้านเตรียมสินค้าแล้ว"
        job.status == "preparing" -> "ร้านกำลังเตรียมสินค้า"
        pickup -> "กำลังไปรับสินค้า"
        job.status == "picked_up" -> "รับสินค้าแล้ว"
        job.customerArrived -> "Rider ถึงลูกค้าแล้ว"
        else -> "กำลังไปส่ง"
    }
    val processLabel = when {
        job.marketOrderId != null && pickup -> "รับของตามจุด"
        job.status == "ready" -> "ตรวจสอบรายการ"
        job.status == "in_progress" -> "กำลังนำส่ง"
        job.status == "picked_up" -> "เริ่มจัดส่ง"
        pickup -> "รอร้าน"
        else -> "รออัปเดต"
    }
    val primaryLabel = when {
        job.marketOrderId != null && pickup -> "นำทางไปร้านถัดไป"
        pickup && job.shopArrived -> if (job.status == "ready") "ตรวจสอบรายการ" else "ถึงร้านแล้ว ✓"
        pickup && navigationStarted -> "ถึงร้านแล้ว"
        pickup -> "นำทางไปร้าน"
        job.status == "in_progress" && job.customerArrived -> "ส่งมอบสินค้า"
        job.status == "in_progress" && navigationStarted -> "ถึงลูกค้าแล้ว"
        else -> "นำทางไปลูกค้า"
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEFDFD)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE5E8))) {
        Column(Modifier.padding(if (LocalConfiguration.current.screenHeightDp <= 760) 10.dp else 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = QgRed, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    Text(job.numberLabel, fontWeight = FontWeight.Black, fontSize = 24.sp)
                }
                Box(Modifier.clip(RoundedCornerShape(13.dp)).background(QgRedSoft).padding(horizontal = 9.dp, vertical = 6.dp)) {
                    Text(job.deliveryFee?.let { "฿%.0f".format(it) } ?: "฿—", color = QgRed, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QgRemoteImage(details?.shopLogo, Modifier.size(42.dp), fallback = details?.shopName?.take(1) ?: "Q", cornerRadius = 14.dp)
                Column { Text("ร้านค้า", color = QgMuted, fontSize = 8.sp); Text(details?.shopName ?: "ร้านค้า", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
            RiderJobPlaceRow("user", "ลูกค้า", details?.customerName ?: "ลูกค้า")
            Column(Modifier.fillMaxWidth().padding(top = 7.dp).background(Color(0xFFFFF6F8), RoundedCornerShape(14.dp)).padding(horizontal = 9.dp, vertical = 8.dp)) {
                Text(if (pickup) "จุดรับสินค้า" else "ที่อยู่จัดส่ง", fontSize = 8.sp, color = QgMuted)
                Text((if (pickup) job.pickupAddress else job.deliveryAddress) ?: "พิกัดยังไม่พร้อม", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            details?.note?.let { note ->
                Column(Modifier.fillMaxWidth().padding(top = 6.dp).background(Color(0xFFFFF9EA), RoundedCornerShape(14.dp)).padding(10.dp)) {
                    Text("หมายเหตุจากลูกค้า", color = QgMuted, fontSize = 8.sp)
                    Text(note, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RiderJobTool("jobs", processLabel, !busy && job.marketOrderId == null && job.status == "ready", Modifier.weight(1f), onPickupVerify)
                RiderJobTool("jobs", "รายการ", !busy, Modifier.weight(1f), onDetails)
                RiderJobTool("support", "แชต", !busy, Modifier.weight(1f), onChat)
            }
            Button(
                onClick = {
                    when {
                        job.marketOrderId != null && pickup -> { onNavigate(); navigationStarted = context.getSharedPreferences("rider_navigation", Context.MODE_PRIVATE).getBoolean(job.id + if (pickup) ":shop" else ":customer", false) }
                        pickup && job.shopArrived && job.status == "ready" -> onPickupVerify()
                        pickup && navigationStarted -> onArriveShop()
                        job.status == "picked_up" -> onStartDelivery()
                        job.status == "in_progress" && job.customerArrived -> onDeliveryVerify()
                        job.status == "in_progress" && navigationStarted -> onArriveCustomer()
                        else -> { onNavigate(); navigationStarted = context.getSharedPreferences("rider_navigation", Context.MODE_PRIVATE).getBoolean(job.id + if (pickup) ":shop" else ":customer", false) }
                    }
                },
                enabled = !busy && job.navigationTarget != null && !(pickup && job.shopArrived && job.status != "ready" && job.marketOrderId == null),
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp).heightIn(min = 46.dp), shape = RoundedCornerShape(18.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFF04455))
            ) {
                QgIcon("location", Modifier.size(22.dp), Color.White)
                Spacer(Modifier.width(9.dp))
                Text(primaryLabel, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun RiderJobTool(icon: String, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.heightIn(min = 46.dp).clip(RoundedCornerShape(15.dp))
        .background(Color.White).border(1.dp, Color(0xFFEDE5E8), RoundedCornerShape(15.dp))
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(if (icon == "support") R.drawable.qg_rider_nav_chat else R.drawable.qg_rider_flow_jobs),
            contentDescription = null, modifier = Modifier.size(18.dp), tint = if (enabled) Color(0xFF282427) else QgMuted)
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (enabled) Color(0xFF282427) else QgMuted, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun RiderBlueprintTopBar(active: Boolean, online: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFAFFFFFF)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE7E9))
        ) {
            Row(
                Modifier.heightIn(min = 44.dp).padding(start = 10.dp, end = 5.dp),
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
                    fontSize = 10.sp
                )
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(width = 43.dp, height = 29.dp).clip(RoundedCornerShape(99.dp))
                    .background(if (online) Color(0xFF1FBE70) else Color(0xFFD9D5D7))
                    .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Switch, onClick = onToggle),
                    contentAlignment = if (online) Alignment.CenterEnd else Alignment.CenterStart) {
                    Box(Modifier.padding(horizontal = 3.dp).size(23.dp).background(Color.White, androidx.compose.foundation.shape.CircleShape))
                }
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
            Text(label, color = QgMuted, fontSize = 8.sp)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2)
        }
    }
}

@Composable
private fun RiderOfferMetric(label: String, value: String, modifier: Modifier, highlight: Boolean = false) {
    Column(
        modifier
            .background(Color(0xFFFAF7F8), RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = QgMuted, fontSize = 8.sp)
        Text(value, color = if (highlight) QgRed else Color(0xFF17171B), fontWeight = FontWeight.Black, fontSize = 12.sp)
    }
}

// Same straight-line distance and 25 km/h estimate as Web offer metrics.
private fun riderDistanceKm(fromLat: Double?, fromLng: Double?, toLat: Double?, toLng: Double?): Double? {
    if (fromLat == null || fromLng == null || toLat == null || toLng == null) return null
    if (!fromLat.isFinite() || !fromLng.isFinite() || !toLat.isFinite() || !toLng.isFinite() ||
        fromLat !in -90.0..90.0 || toLat !in -90.0..90.0 || fromLng !in -180.0..180.0 || toLng !in -180.0..180.0) return null
    val dLat = Math.toRadians(toLat - fromLat)
    val dLng = Math.toRadians(toLng - fromLng)
    val a = kotlin.math.sin(dLat / 2).let { it * it } + kotlin.math.cos(Math.toRadians(fromLat)) *
        kotlin.math.cos(Math.toRadians(toLat)) * kotlin.math.sin(dLng / 2).let { it * it }
    return 6371.0 * 2.0 * kotlin.math.atan2(kotlin.math.sqrt(a.coerceIn(0.0, 1.0)), kotlin.math.sqrt((1.0 - a).coerceIn(0.0, 1.0)))
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
    details: RiderVerificationDetails?,
    photoUri: Uri?,
    busy: Boolean,
    message: String?,
    onBack: () -> Unit,
    onCamera: () -> Unit,
    onChat: () -> Unit,
    onCallCustomer: () -> Unit,
    onConfirm: () -> Unit
) {
    val pickup = mode == "pickup"
    val marketPickupMode = mode == "marketPickup"
    var previewReady by remember(photoUri) { mutableStateOf(false) }
    val photoReady = photoUri != null
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val shortScreen = screenHeight <= 760
    BackHandler { if (!busy) onBack() }
    Box(modifier.fillMaxSize().background(Color(0xFFF8F7F8))) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(QgRedSoft)
                    .clickable(enabled = !busy, onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.qg_rider_flow_back), "ย้อนกลับ", Modifier.size(22.dp), tint = Color(0xFFE23A4C))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (pickup || marketPickupMode) "รับสินค้าที่ร้าน" else "ส่งสินค้าให้ลูกค้า", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(job.numberLabel, fontSize = 8.sp, color = Color(0xFF8A8387))
                }
                Spacer(Modifier.size(44.dp))
            }
            Column(Modifier.weight(1f).widthIn(max = 680.dp).fillMaxWidth().align(Alignment.CenterHorizontally).verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp).padding(top = if (shortScreen) 7.dp else 9.dp, bottom = 92.dp)) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(horizontal = 11.dp, vertical = if (shortScreen) 9.dp else 12.dp)) {
                        Text(if (pickup || marketPickupMode) "รับสินค้าที่ร้าน" else "ส่งสินค้าให้ลูกค้า", fontSize = 9.sp, color = QgMuted)
                        Text(when { marketPickupMode -> "ตรวจจุดรับและถ่ายรูป"; pickup -> "ตรวจสอบรายการและถ่ายรูป"; else -> "ตรวจสอบและส่งมอบสินค้า" },
                            fontSize = if (shortScreen) 18.sp else 20.sp, fontWeight = FontWeight.Bold)
                        Text(if (pickup || marketPickupMode) "เช็กชื่อสินค้าและจำนวนให้ครบ แล้วถ่ายรูปหลักฐานในหน้าเดียว" else "ตรวจรายการและถ่ายรูปหลักฐานในหน้าเดียว", fontSize = 9.sp, color = QgMuted)
                    }
                }
                if (pickup && details != null) {
                    RiderProofCard(shortScreen) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QgRemoteImage(details.shopLogo, Modifier.size(40.dp), fallback = details.shopName?.take(1) ?: "Q", cornerRadius = 12.dp)
                            Column {
                                Text("ร้านค้า", fontSize = 8.sp, color = QgMuted)
                                Text(details.shopName ?: "ร้านค้า", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0EAEC)))
                        RiderProofMetaRow(R.drawable.qg_rider_flow_jobs, "รหัสออเดอร์", job.numberLabel)
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0EAEC)))
                        RiderProofMetaRow(R.drawable.qg_rider_nav_user, "ลูกค้า", details.customerName ?: "ลูกค้า")
                    }
                }
                if (!pickup && !marketPickupMode) {
                    RiderProofCard(shortScreen) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("ลูกค้า", fontSize = 8.sp, color = QgMuted)
                                Text(details?.customerName ?: "ลูกค้า", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            RiderProofContact(
                                R.drawable.qg_rider_flow_phone,
                                "โทรหาลูกค้า",
                                !busy,
                                onCallCustomer
                            )
                            RiderProofContact(R.drawable.qg_rider_nav_chat, "แชต", !busy, onChat)
                        }
                    }
                    RiderProofCard(shortScreen) {
                        RiderProofMetaRow(R.drawable.qg_rider_flow_loc, "ที่อยู่จัดส่ง", job.deliveryAddress ?: "จุดส่งสินค้า")
                    }
                    details?.note?.let { RiderProofNote(it, shortScreen) }
                }
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

                if (!marketPickupMode) Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE6E8)),
                    colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = if (shortScreen) 8.dp else 9.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (pickup) "รายการสินค้า" else "สินค้า", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("${items.sumOf { it.quantity }} รายการ", fontSize = 8.sp, color = QgMuted)
                        }
                        if (items.isEmpty()) Text("ไม่พบรายการสินค้า", fontSize = 10.sp, color = QgMuted)
                        items.forEachIndexed { index, item ->
                            Row(Modifier.fillMaxWidth().padding(vertical = if (shortScreen) 6.dp else 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!item.imageUrl.isNullOrBlank()) QgRemoteImage(item.imageUrl,
                                    Modifier.size(if (shortScreen) 42.dp else 46.dp), cornerRadius = 11.dp, showFallback = false)
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    if (pickup && !item.description.isNullOrBlank()) Text(item.description, fontSize = 9.sp, color = QgMuted)
                                    Text("× ${item.quantity}", fontSize = 8.sp, color = QgMuted)
                                }
                                Text("฿" + "%.0f".format(item.totalPrice), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            if (index < items.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0EAEC)))
                        }
                        if (pickup && job.shopCash != null) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEE6E8)))
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("รวมค่าสินค้า", fontSize = 10.sp)
                                Text("฿" + "%.0f".format(job.shopCash), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = QgRed)
                            }
                        }
                    }
                }

                if (pickup) details?.note?.let { RiderProofNote(it, shortScreen) }
                Spacer(Modifier.height(6.dp))
                if (photoUri != null) {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (pickup || marketPickupMode) "รูปตอนรับสินค้า" else "รูปตอนส่งสินค้า", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("จำเป็น", fontSize = 8.sp, color = QgMuted)
                            }
                            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(if (shortScreen) 108.dp else (screenHeight * .31f).coerceIn(160f, 215f).dp)
                                .clip(RoundedCornerShape(15.dp))) {
                                RiderEvidencePreview(photoUri, Modifier.fillMaxSize()) { previewReady = it }
                                Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)
                                    .height(38.dp).clip(RoundedCornerShape(99.dp)).background(Color(0xF5FFFFFF))
                                    .clickable(enabled = !busy, onClick = onCamera).padding(horizontal = 13.dp), contentAlignment = Alignment.Center) {
                                    Text("ถ่ายใหม่", fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
                if (message != null) Text(message, color = QgRed, modifier = Modifier.padding(top = 8.dp))
                Text(if (pickup || marketPickupMode) "ตรวจรายการให้ครบและถ่ายรูปก่อนรับสินค้า" else "ตรวจรายการให้ครบและถ่ายรูปหลักฐานก่อนยืนยันส่ง", fontSize = 8.sp, color = QgMuted, modifier = Modifier.padding(top = 5.dp))
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 12.dp).widthIn(max = 660.dp).fillMaxWidth()
            .background(Color(0xFAFFFFFF), RoundedCornerShape(18.dp))) {
            Button(
                onClick = if (photoReady) onConfirm else onCamera,
                enabled = !busy && (!photoReady || previewReady),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFF04455))
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                else Text(when { !photoReady && marketPickupMode -> "ถ่ายรูปสินค้าจุดนี้"; !photoReady && pickup -> "ถ่ายรูปสินค้าที่รับ"; !photoReady -> "ถ่ายรูปตอนส่ง"; marketPickupMode -> "ยืนยันรับสินค้าจุดนี้"; pickup -> "ยืนยันรับสินค้า"; else -> "ยืนยันส่งสินค้า" }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RiderProofCard(shortScreen: Boolean, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top = 6.dp), shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE6E8)),
        colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(horizontal = if (shortScreen) 9.dp else 10.dp, vertical = if (shortScreen) 8.dp else 9.dp), content = content)
    }
}

@Composable
private fun RiderProofMetaRow(icon: Int, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(icon), null, Modifier.size(22.dp), tint = QgRed)
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 8.sp, color = QgMuted)
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RiderProofContact(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(QgRedSoft)
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), label, Modifier.size(22.dp), tint = QgRed)
    }
}

@Composable
private fun RiderProofNote(note: String, shortScreen: Boolean) {
    RiderProofCard(shortScreen) {
        Text("หมายเหตุจากลูกค้า", fontSize = 8.sp, color = QgMuted)
        Text(note, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
            ?.takeIf { it.latitude.isFinite() && it.longitude.isFinite() &&
                it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
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

private fun openNavigation(context: Context, job: RiderJob) {
    if (job.navigationTarget == null) return
    RiderReturnService.stop(context)
    context.getSharedPreferences("rider_navigation", Context.MODE_PRIVATE).edit()
        .putBoolean(job.id + if (job.isDelivering) ":customer" else ":shop", true).apply()
}

private fun openExternalNavigation(context: Context, job: RiderJob) {
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
