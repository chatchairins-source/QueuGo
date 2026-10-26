package com.queuego.rider

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import com.queuego.shared.QueueGoTheme
import com.queuego.shared.QueueGoVoiceCallOverlay
import com.queuego.shared.NativeVoiceCallController
import com.queuego.shared.NativeVoiceControllerState
import com.queuego.shared.NativeVoicePhase
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
import com.queuego.ridernavigationmap.NavigationViewportInsets
import com.queuego.ridernavigationmap.NavigationGuidanceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.SideEffect
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

@Composable
fun QueueGoRiderApp(pushTarget: NativePushLaunchTarget? = null) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        RiderFrameJankTracker.start()
        RiderMainThreadWatchdog.start()
        onDispose {
            RiderMainThreadWatchdog.stop()
            RiderFrameJankTracker.stop()
        }
    }
    val api = remember { QueueGoApi() }
    val store = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()

    // Rider session restore touches SharedPreferences + Android Keystore + AES/GCM.
    // Keep all of that off Main so a slow keystore/provider cannot turn cold start
    // into an ANR. The existing silent loading surface prevents a login flash.
    var auth by remember { mutableStateOf<QueueGoAuth?>(null) }
    var restoring by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var restorationError by remember { mutableStateOf(false) }
    var restorationAttempt by remember { mutableStateOf(0) }
    var registrationOpen by remember { mutableStateOf(false) }

    LaunchedEffect(restorationAttempt) {
        restoring = auth == null
        restorationError = false
        val cached = auth ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            store.load()
        }
        if (cached == null) {
            restoring = false
            return@LaunchedEffect
        }

        // Never blank an already-restored UI while the server session check runs.
        if (auth == null) auth = cached
        registrationOpen = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            store.pendingRegistrationUserId() == cached.session.authUserId
        }
        restoring = false

        val validation = runCatching {
            val validated = api.validate(cached)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.save(validated) }
            validated
        }
        validation.onSuccess { validated ->
            auth = validated
        }.onFailure {
            if (it is CancellationException) throw it
            if (shouldClearRiderSession(it)) {
                store.clear()
                auth = null
                error = it.message
            } else if (auth == null) {
                restorationError = true
            }
            // With a cached authenticated view, transient network failure is
            // non-blocking; the periodic validator will retry in the background.
        }
    }

    val appLifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(auth?.session?.sessionId, appLifecycle) {
        if (auth == null) return@LaunchedEffect
        appLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(25_000)
                val current = auth ?: break
                val result = runCatching {
                    val validated = api.validate(current)
                    api.touch(validated.session)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.save(validated) }
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
                if (auth == null) return@repeatOnLifecycle
            }
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
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.save(result) }
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
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.save(signed) }
                                auth = signed
                                if (signed.user.status == "pending") {
                                    val draft = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.loadRegistrationDraft() }
                                    if (draft?.optJSONObject("form")?.optString("phone")?.replace(Regex("[-\\s]"), "") == signed.user.phone &&
                                        (kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            store.pendingRegistrationUserId() == signed.session.authUserId
                                        } || api.registrationProfileMissing(signed))) {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            store.saveRegistrationCheckpoint(signed)
                                        }
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
                    modifier = Modifier.padding(insets),
                    name = auth!!.user.name,
                    status = auth!!.user.status,
                    onLogout = {
                        val current = auth!!
                        RiderReturnService.stop(context)
                        clearRiderOrderNotifications(context)
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
                        clearRiderOrderNotifications(context)
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

internal fun riderPickupCommittedStatus(mutationResult: Any?): String =
    when (mutationResult?.toString()?.trim()?.lowercase()) {
        "in_progress" -> "in_progress"
        "picked_up" -> "picked_up"
        else -> "picked_up"
    }

internal fun riderSnapshotJobForCommit(updated: RiderSnapshot, jobId: String): RiderJob? =
    updated.activeJob?.takeIf { it.id == jobId }
        ?: updated.bundle.activeOrders.firstOrNull { it.job.id == jobId }?.job

internal fun riderPickupReadCanReplace(
    committedStatus: String,
    jobId: String,
    updated: RiderSnapshot
): Boolean {
    val candidate = riderSnapshotJobForCommit(updated, jobId) ?: return false
    val status = candidate.status.lowercase()
    return when (committedStatus.lowercase()) {
        "in_progress" -> status in setOf("in_progress", "completed")
        else -> status in setOf("picked_up", "in_progress", "completed")
    }
}

internal fun riderApplyPickupCommit(
    current: RiderSnapshot,
    pickedJob: RiderJob,
    committedStatus: String
): RiderSnapshot {
    val advancedJob = pickedJob.copy(status = committedStatus.lowercase())
    val bundled = current.bundle.activeOrders.any { it.job.id == pickedJob.id }
    if (!bundled) {
        return current.copy(activeJob = advancedJob, offeredJob = null)
    }

    val bundleAfter = current.bundle.copy(
        activeOrders = current.bundle.activeOrders.map { bundledOrder ->
            if (bundledOrder.job.id == pickedJob.id) {
                bundledOrder.copy(job = advancedJob)
            } else bundledOrder
        }
    )
    return current.copy(
        activeJob = bundleAfter.currentOrder()?.job ?: advancedJob,
        offeredJob = null,
        bundle = bundleAfter
    )
}

private fun riderStageRank(status: String): Int = when (status.lowercase()) {
    "rider_assigned", "assigned" -> 10
    "preparing" -> 20
    "ready" -> 30
    "picked_up" -> 40
    "in_progress" -> 50
    "completed", "cancelled" -> 60
    else -> 0
}

internal fun riderMergeLiveSnapshot(current: RiderSnapshot, incoming: RiderSnapshot): RiderSnapshot {
    val currentJob = current.activeJob
    val incomingJob = incoming.activeJob
    val mergedJob = when {
        currentJob == null || incomingJob == null || currentJob.id != incomingJob.id -> incomingJob
        else -> {
            val currentRank = riderStageRank(currentJob.status)
            val incomingRank = riderStageRank(incomingJob.status)
            val base = if (incomingRank >= currentRank && incomingRank > 0) incomingJob else currentJob
            base.copy(
                arrivedShopAt = base.arrivedShopAt ?: currentJob.arrivedShopAt,
                arrivedCustomerAt = base.arrivedCustomerAt ?: currentJob.arrivedCustomerAt,
                shopCash = base.shopCash ?: currentJob.shopCash,
                customerCash = base.customerCash ?: currentJob.customerCash,
                customerSubtotal = base.customerSubtotal ?: currentJob.customerSubtotal,
                customerDeliveryFee = base.customerDeliveryFee ?: currentJob.customerDeliveryFee,
                customerServiceFee = base.customerServiceFee ?: currentJob.customerServiceFee
            )
        }
    }
    val previousPickups = current.marketPickups.associateBy { it.pickupId }
    val mergedPickups = incoming.marketPickups.map { pickup ->
        previousPickups[pickup.pickupId]?.takeIf { it.done && !pickup.done } ?: pickup
    }
    return incoming.copy(activeJob = mergedJob, marketPickups = mergedPickups)
}

internal fun riderResolveMissingActiveSnapshot(
    current: RiderSnapshot,
    incoming: RiderSnapshot,
    resolvedJob: RiderJob?,
    lookupSucceeded: Boolean
): RiderSnapshot {
    val currentJob = current.activeJob ?: return incoming
    if (incoming.activeJob != null) return incoming
    val status = resolvedJob?.status?.lowercase()
    val activeStatuses = setOf("rider_assigned", "assigned", "preparing", "ready", "picked_up", "in_progress")
    return when {
        !lookupSucceeded -> incoming.copy(
            activeJob = currentJob,
            marketPickups = if (currentJob.marketOrderId != null) current.marketPickups else incoming.marketPickups
        )
        resolvedJob?.id == currentJob.id && status in activeStatuses -> incoming.copy(
            activeJob = resolvedJob,
            marketPickups = if (resolvedJob.marketOrderId != null) current.marketPickups else incoming.marketPickups
        )
        else -> incoming
    }
}

internal fun riderArrivalReadCanReplace(target: String, jobId: String, updated: RiderSnapshot): Boolean {
    val candidate = updated.activeJob ?: return false
    if (candidate.id != jobId) return false
    return when (target) {
        "shop" -> candidate.shopArrived
        "customer" -> candidate.customerArrived
        else -> false
    }
}

internal fun riderVerificationCommitVisible(
    mode: String,
    jobId: String,
    marketPickupId: String?,
    updated: RiderSnapshot
): Boolean = when (mode) {
    "pickup" -> riderSnapshotJobForCommit(updated, jobId)?.let { job ->
        job.status.lowercase() in setOf("picked_up", "in_progress", "completed")
    } == true
    "marketPickup" -> {
        val pickupCommitted = marketPickupId?.let { id ->
            updated.marketPickups.firstOrNull { it.pickupId == id }?.done
        } == true
        val orderAdvanced = updated.activeJob?.let { job ->
            job.id == jobId && job.status.lowercase() in setOf("picked_up", "in_progress", "completed")
        } == true
        pickupCommitted || orderAdvanced
    }
    else -> updated.activeJob?.let { job ->
        job.id != jobId || job.status.lowercase() in setOf("completed", "cancelled")
    } ?: true
}

@Composable
private fun LoadingScreen(modifier: Modifier) {
    // Restoration still blocks interaction, but normal app/session recovery stays visually silent.
    Box(modifier.fillMaxSize())
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
    val eventFeedbackView = LocalView.current
    val networkAvailable by rememberRiderValidatedNetwork(context)
    val scope = rememberCoroutineScope()
    val nativeVoiceAuth = remember(
        auth.user.id,
        auth.session.accessToken,
        auth.session.sessionId,
        auth.session.expiresAtMs
    ) { auth.asNativeVoiceAuth() }
    val voiceController = remember(auth.user.id) { NativeVoiceCallController(context) }
    val voiceState by voiceController.state.collectAsState()
    val voiceAssistantState = remember(auth.user.id) {
        RiderVoiceAssistantSettings.state(auth.user.id)
    }
    val voiceAssistantEnabled by voiceAssistantState.collectAsState()
    LaunchedEffect(auth.user.id) {
        RiderVoiceAssistantSettings.hydrate(context, auth.user.id)
    }
    val voiceAssistantSpeaker = remember(auth.user.id) {
        RiderVoiceAssistantSpeaker(context.applicationContext)
    }
    var lastSpokenOfferKey by rememberSaveable(auth.user.id) { mutableStateOf<String?>(null) }
    var lastSpokenJobStageKey by rememberSaveable(auth.user.id) { mutableStateOf<String?>(null) }
    val navigationFix by RiderNavigationLocationStore.fix.collectAsState()
    val navigationSessionStartedMs by RiderNavigationLocationStore.sessionStartedElapsedRealtimeMs.collectAsState()
    var navigationGuidance by remember(auth.user.id) { mutableStateOf(NavigationGuidanceState()) }
    var navigationClockMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    var dismissedRestSessionStart by remember { mutableStateOf<Long?>(null) }
    var unexpectedStopLastMovementMs by remember(auth.user.id) { mutableStateOf<Long?>(null) }
    var dismissedUnexpectedStopEpisode by remember(auth.user.id) { mutableStateOf<Long?>(null) }
    var showUnexpectedStopSafety by remember(auth.user.id) { mutableStateOf(false) }
    DisposableEffect(voiceController) { onDispose { voiceController.close() } }
    DisposableEffect(voiceAssistantSpeaker) { onDispose { voiceAssistantSpeaker.close() } }
    LaunchedEffect(voiceAssistantEnabled, voiceState.phase) {
        if (!voiceAssistantEnabled || voiceState.phase != NativeVoicePhase.IDLE) {
            voiceAssistantSpeaker.stop()
        }
    }
    val pushStore = remember(context) { SessionStore(context) }
    val acceptanceModeStore = remember(context) { RiderAcceptanceModeStore(context) }
    val stopAfterWorkStore = remember(context) { RiderStopAfterWorkStore(context) }
    val realtime = remember { NativeOrderRealtime() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val snapshotMutex = remember { Mutex() }
    val dispatchPreferenceWriteMutex = remember(auth.user.id) { Mutex() }

    var snapshot by remember(auth.user.id) {
        mutableStateOf<RiderSnapshot?>(null)
    }
    LaunchedEffect(auth.user.id) {
        val cachedSnapshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            pushStore.loadHomeSnapshot(auth.user.id)
        }
        if (snapshot == null && cachedSnapshot != null) snapshot = cachedSnapshot
    }
    var dispatchPreferencesHydrated by remember(auth.user.id) { mutableStateOf(false) }
    var workPreferences by remember(auth.user.id) { mutableStateOf<RiderWorkPreferences?>(null) }
    var workPreferencesBusy by remember(auth.user.id) { mutableStateOf(false) }
    var workPreferencesError by remember(auth.user.id) { mutableStateOf<String?>(null) }
    var autoAcceptEnabled by rememberSaveable(auth.user.id) { mutableStateOf(false) }
    var autoAcceptAttemptKey by rememberSaveable(auth.user.id) {
        mutableStateOf<String?>(null)
    }
    var stopAfterWorkPending by rememberSaveable(auth.user.id) { mutableStateOf(false) }

    LaunchedEffect(auth.user.id, auth.session.accessToken, acceptanceModeStore, stopAfterWorkStore) {
        dispatchPreferencesHydrated = false
        stopAfterWorkPending = try {
            stopAfterWorkStore.pending(auth.user.id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        try {
            val restored = api.workPreferences(auth)
            dispatchPreferenceWriteMutex.withLock {
                acceptanceModeStore.setAutoAcceptEnabled(auth.user.id, restored.autoAccept)
            }
            workPreferences = restored
            autoAcceptEnabled = restored.autoAccept
            workPreferencesError = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            workPreferences = null
            autoAcceptEnabled = false
            workPreferencesError = "ตรวจอินเทอร์เน็ตแล้วลองใหม่"
            try {
                dispatchPreferenceWriteMutex.withLock {
                    acceptanceModeStore.setAutoAcceptEnabled(auth.user.id, false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Fail closed in memory even when the cache cannot be updated.
            }
        } finally {
            dispatchPreferencesHydrated = true
        }
    }

    LaunchedEffect(
        voiceAssistantEnabled,
        voiceState.phase,
        snapshot?.offeredJob?.id,
        snapshot?.offeredJob?.offerExpiresAt
    ) {
        if (!voiceAssistantEnabled || voiceState.phase != NativeVoicePhase.IDLE) return@LaunchedEffect
        val offer = snapshot?.offeredJob ?: return@LaunchedEffect
        if (riderOfferSecondsLeft(offer.offerExpiresAt) <= 0) return@LaunchedEffect
        val key = offer.id + ":" + (offer.offerExpiresAt ?: "")
        if (key != lastSpokenOfferKey) {
            lastSpokenOfferKey = key
            delay(1_300L)
            if (voiceAssistantEnabled && voiceState.phase == NativeVoicePhase.IDLE) {
                voiceAssistantSpeaker.speak(riderVoiceOfferAnnouncement(), "offer:$key")
            }
        }
    }

    LaunchedEffect(
        voiceAssistantEnabled,
        voiceState.phase,
        snapshot?.activeJob?.id,
        snapshot?.activeJob?.status,
        snapshot?.activeJob?.arrivedShopAt,
        snapshot?.activeJob?.arrivedCustomerAt
    ) {
        if (!voiceAssistantEnabled || voiceState.phase != NativeVoicePhase.IDLE) return@LaunchedEffect
        val job = snapshot?.activeJob ?: return@LaunchedEffect
        val key = riderVoiceJobStageKey(job) ?: return@LaunchedEffect
        val message = riderVoiceJobAnnouncement(job) ?: return@LaunchedEffect
        if (key != lastSpokenJobStageKey) {
            lastSpokenJobStageKey = key
            voiceAssistantSpeaker.speak(message, "job:$key")
        }
    }

    var todaySummary by remember { mutableStateOf<RiderPeriodSummary?>(null) }
    var homeMissions by remember(auth.user.id) { mutableStateOf<List<RiderMission>>(emptyList()) }
    var demandZones by remember(auth.user.id) { mutableStateOf<List<RiderDemandZone>>(emptyList()) }
    var destinationPreference by remember(auth.user.id) { mutableStateOf<RiderDestinationPreference?>(null) }
    var destinationLoaded by remember(auth.user.id) { mutableStateOf(false) }
    var showDestination by remember(auth.user.id) { mutableStateOf(false) }
    var availabilityReverification by remember(auth.user.id) { mutableStateOf<RiderReverificationStatus?>(null) }
    var availabilityReverificationLoaded by remember(auth.user.id) { mutableStateOf(false) }
    var availabilityDocuments by remember(auth.user.id) { mutableStateOf<RiderDocumentReadiness?>(null) }
    var availabilityDocumentsLoaded by remember(auth.user.id) { mutableStateOf(false) }
    var availabilityVehicleInspection by remember(auth.user.id) { mutableStateOf<RiderVehicleInspectionStatus?>(null) }
    var availabilityVehicleInspectionLoaded by remember(auth.user.id) { mutableStateOf(false) }
    var availabilitySelectedVehicle by remember(auth.user.id) { mutableStateOf<RiderVehicle?>(null) }
    var availabilityVehiclesLoaded by remember(auth.user.id) { mutableStateOf(false) }
    var recentHistory by remember { mutableStateOf<List<RiderHistoryOrder>>(emptyList()) }
    var lastSummaryAt by remember { mutableStateOf(0L) }
    var lastHistoryAt by remember { mutableStateOf(0L) }
    var lastLocationPushAt by remember { mutableStateOf(0L) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var consecutiveSnapshotFailures by remember(auth.user.id) { mutableIntStateOf(0) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    fun retryWorkPreferences() {
        if (workPreferencesBusy) return
        workPreferencesBusy = true
        scope.launch {
            try {
                val restored = api.workPreferences(auth)
                dispatchPreferenceWriteMutex.withLock {
                    acceptanceModeStore.setAutoAcceptEnabled(auth.user.id, restored.autoAccept)
                }
                workPreferences = restored
                autoAcceptEnabled = restored.autoAccept
                workPreferencesError = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                workPreferences = null
                autoAcceptEnabled = false
                workPreferencesError = "ตรวจอินเทอร์เน็ตแล้วลองใหม่"
            } finally {
                workPreferencesBusy = false
            }
        }
    }

    fun saveWorkPreferences(nextAutoAccept: Boolean, nextBundleEnabled: Boolean, nextServices: Set<String>) {
        if (workPreferencesBusy || workPreferences == null) return
        workPreferencesBusy = true
        scope.launch {
            try {
                val acknowledged = api.saveWorkPreferences(
                    auth = auth,
                    autoAccept = nextAutoAccept,
                    bundleEnabled = nextBundleEnabled,
                    services = nextServices
                )
                dispatchPreferenceWriteMutex.withLock {
                    acceptanceModeStore.setAutoAcceptEnabled(auth.user.id, acknowledged.autoAccept)
                }
                workPreferences = acknowledged
                autoAcceptEnabled = acknowledged.autoAccept
                workPreferencesError = null
                actionMessage = "บันทึกการรับงานแล้ว"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                actionMessage = e.message ?: "บันทึกการรับงานไม่สำเร็จ"
            } finally {
                workPreferencesBusy = false
            }
        }
    }

    suspend fun persistCommittedSnapshot(value: RiderSnapshot) {
        val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { pushStore.saveHomeSnapshot(auth.user.id, value, durable = true) }
                .getOrDefault(false)
        }
        if (!saved) actionMessage = "บันทึกงานในเครื่องไม่สำเร็จ · เชื่อมต่อเพื่อกู้คืนสถานะจากระบบ"
    }

    fun setStopAfterWorkPending(pending: Boolean) {
        if (!dispatchPreferencesHydrated) return
        stopAfterWorkPending = pending
        scope.launch {
            try {
                dispatchPreferenceWriteMutex.withLock {
                    stopAfterWorkStore.setPending(auth.user.id, pending)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                actionMessage = "บันทึกโหมดหยุดรับงานหลังจบงานไม่สำเร็จ"
            }
        }
    }

    fun requireServerNetwork(): Boolean {
        if (riderServerMutationAllowed(networkAvailable)) return true
        actionMessage = RIDER_NETWORK_GUARD_MESSAGE
        return false
    }
    var deliverySuccessJob by remember { mutableStateOf<RiderJob?>(null) }
    var arrivalGuard by remember { mutableStateOf<RiderArrivalGuardState?>(null) }
    LaunchedEffect(actionMessage) {
        if (actionMessage != null) {
            delay(4_000)
            actionMessage = null
        }
    }
    val demandHeatmapEligible =
        snapshot?.online == true &&
            snapshot?.activeJob == null &&
            snapshot?.laundry?.activeJob == null &&
            snapshot?.offeredJob == null

    val homeActionEligible =
        snapshot != null &&
            snapshot?.activeJob == null &&
            snapshot?.laundry?.activeJob == null &&
            snapshot?.offeredJob == null

    LaunchedEffect(auth.user.id, auth.session.accessToken, homeActionEligible, lifecycle) {
        if (!homeActionEligible) {
            homeMissions = emptyList()
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                homeMissions = try {
                    api.missions(auth)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    homeMissions
                }
                delay(300_000L)
            }
        }
    }

    LaunchedEffect(auth.user.id, auth.session.accessToken, demandHeatmapEligible, lifecycle) {
        if (!demandHeatmapEligible) {
            demandZones = emptyList()
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try {
                    demandZones = api.demandHeatmap(auth)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Keep the last aggregate during a transient read failure.
                }
                delay(300_000L)
            }
        }
    }

    LaunchedEffect(
        auth.user.id,
        auth.session.accessToken,
        snapshot?.online,
        lifecycle
    ) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try {
                    availabilityReverification = api.reverificationStatus(auth)
                    availabilityReverificationLoaded = true
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    availabilityReverificationLoaded = false
                }
                try {
                    availabilityDocuments = api.documentReadiness(auth)
                    availabilityDocumentsLoaded = true
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    availabilityDocumentsLoaded = false
                }
                try {
                    availabilityVehicleInspection = api.vehicleInspectionStatus(auth)
                    availabilityVehicleInspectionLoaded = true
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    availabilityVehicleInspectionLoaded = false
                }
                try {
                    availabilitySelectedVehicle = api.vehicles(auth).firstOrNull { it.selected }
                    availabilityVehiclesLoaded = true
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    availabilityVehiclesLoaded = false
                }
                delay(if (snapshot?.online == true) 300_000L else 60_000L)
            }
        }
    }

    LaunchedEffect(auth.user.id, auth.session.accessToken) {
        destinationLoaded = false
        destinationPreference = try {
            api.destinationPreference(auth)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        destinationLoaded = true
    }

    var detailJob by remember { mutableStateOf<RiderJob?>(null) }
    var pendingPushReference by rememberSaveable(auth.user.id) { mutableStateOf<String?>(null) }
    var homeDetails by remember { mutableStateOf<RiderVerificationDetails?>(null) }
    var homeItems by remember { mutableStateOf<List<RiderItem>>(emptyList()) }
    var activeLoadSummary by remember { mutableStateOf<RiderLoadSummary?>(null) }
    LaunchedEffect(snapshot?.activeJob?.id, auth.session.accessToken) {
        homeDetails = null
        homeItems = emptyList()
        activeLoadSummary = null
        snapshot?.activeJob?.takeUnless { it.isPassenger || it.isExpress }?.let { job ->
            coroutineScope {
                val detailsDeferred = async { runCatching { api.verificationDetails(auth, job.id) } }
                val itemsDeferred = async { runCatching { api.orderItems(auth, job.id) } }
                val loadDeferred = async { runCatching { api.orderLoadSummary(auth, job.id) } }
                detailsDeferred.await().onSuccess { homeDetails = it }.onFailure { if (it is CancellationException) throw it }
                itemsDeferred.await().onSuccess { homeItems = it }.onFailure { if (it is CancellationException) throw it }
                loadDeferred.await().onSuccess { activeLoadSummary = it }.onFailure { if (it is CancellationException) throw it }
            }
        }
    }
    var offerShopDisplay by remember { mutableStateOf<RiderShopDisplay?>(null) }
    var offerLoadSummary by remember { mutableStateOf<RiderLoadSummary?>(null) }
    LaunchedEffect(snapshot?.offeredJob?.id, auth.session.accessToken) {
        offerShopDisplay = null
        offerLoadSummary = null
        snapshot?.offeredJob?.takeUnless { it.isPassenger || it.isExpress }?.let { job ->
            try {
                offerShopDisplay = api.offerShopDisplay(auth, job.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The offer remains actionable with its real order address if shop media is unavailable.
            }
            offerLoadSummary = try {
                api.orderLoadSummary(auth, job.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }
    var chatJob by remember { mutableStateOf<RiderJob?>(null) }
    var deliveryIssueJob by remember { mutableStateOf<RiderJob?>(null) }
    var pickupIssueJob by remember { mutableStateOf<RiderJob?>(null) }
    var pickupIssueStopId by remember { mutableStateOf<String?>(null) }
    var pickupChatJob by remember { mutableStateOf<RiderJob?>(null) }
    var verifyMode by remember { mutableStateOf<String?>(null) }
    var verifyJob by remember { mutableStateOf<RiderJob?>(null) }
    var verifyDetails by remember { mutableStateOf<RiderVerificationDetails?>(null) }
    var verifyItems by remember { mutableStateOf<List<RiderItem>>(emptyList()) }
    var verifyMarketPickup by remember { mutableStateOf<MarketPickup?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var returnPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var returnPendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val pendingProofStore = remember(context, auth.user.id) { RiderPendingProofStore(context) }
    val pendingProofStoreMutex = remember(auth.user.id) { Mutex() }
    suspend fun <T> pendingProofIo(block: () -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            pendingProofStoreMutex.withLock { block() }
        }
    var lastOfferAlertKey by remember { mutableStateOf<String?>(null) }
    var handledPushEventId by remember(auth.user.id) { mutableStateOf<Long?>(null) }
    var activeTab by rememberSaveable(auth.user.id) { mutableStateOf("home") }
    var activeTabHydrated by remember(auth.user.id) { mutableStateOf(false) }
    LaunchedEffect(auth.user.id) {
        val savedTab = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            pushStore.loadActiveTab(auth.user.id)
        }
        if (!activeTabHydrated) activeTab = savedTab
        activeTabHydrated = true
    }
    LaunchedEffect(auth.user.id, activeTab, activeTabHydrated) {
        if (!activeTabHydrated) return@LaunchedEffect
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            pushStore.saveActiveTab(auth.user.id, activeTab)
        }
    }
    var pendingVoiceOrderId by remember { mutableStateOf<String?>(null) }
    var pendingVoiceTarget by remember { mutableStateOf("customer") }
    var pendingVoiceAnswer by remember { mutableStateOf(false) }
    val voicePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (pendingVoiceAnswer) {
                voiceController.answerIncoming(nativeVoiceAuth)
            } else {
                pendingVoiceOrderId?.let { orderId ->
                    voiceController.startOutgoing(nativeVoiceAuth, orderId, pendingVoiceTarget)
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
            pendingVoiceTarget = "customer"
            pendingVoiceAnswer = false
            voicePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun startShopVoice(orderId: String) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceController.startOutgoing(nativeVoiceAuth, orderId, "shop")
        } else {
            pendingVoiceOrderId = orderId
            pendingVoiceTarget = "shop"
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

    val badgeSeenStore = remember(context.applicationContext) { RiderMessageSeenStore(context.applicationContext) }
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
                        val seenAt = if (badgeInbox) {
                            badgeSeenStore.markSeen(auth.user.id, now)
                            now
                        } else {
                            badgeSeenStore.seenAt(auth.user.id)
                        }
                        val (count, notice) = badgeTracker.update(active, badgeChat, badgeInbox, rooms,
                            messages, seenAt, now)
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
        if (activeTab == "chat") {
            badgeSeenStore.markSeen(auth.user.id, System.currentTimeMillis())
        }
        if (chatJob != null) unreadMessages = 0
    }

    var recenterSignal by remember { mutableStateOf(0) }
    var overviewSignal by remember { mutableStateOf(0) }
    var compassResetSignal by remember { mutableStateOf(0) }
    var routeOverviewActive by remember { mutableStateOf(false) }
    var pendingMapRecenter by remember { mutableStateOf(false) }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it }
        actionMessage = if (granted && pendingMapRecenter) {
            routeOverviewActive = false
            recenterSignal += 1
            "กำลังหาตำแหน่ง"
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
        scope.launch {
            try {
                if (success) {
                    val promoted = pendingProofIo {
                        pendingProofStore.promoteCapture(auth.user.id, RIDER_PROOF_SLOT_DELIVERY)
                    }
                    photoUri = promoted?.uri?.let(Uri::parse)
                    actionMessage = if (promoted != null) {
                        "ถ่ายรูปหลักฐานแล้ว · ระบบเก็บรูปไว้จนกว่าจะส่งสำเร็จ"
                    } else {
                        "บันทึกรูปหลักฐานไม่สำเร็จ กรุณาถ่ายใหม่"
                    }
                } else {
                    pendingProofIo {
                        pendingProofStore.cancelCapture(auth.user.id, RIDER_PROOF_SLOT_DELIVERY)
                    }
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                photoUri = null
                actionMessage = "บันทึกรูปหลักฐานไม่สำเร็จ กรุณาถ่ายใหม่"
            } finally {
                pendingCameraUri = null
            }
        }
    }

    val returnCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        scope.launch {
            try {
                if (success) {
                    val promoted = pendingProofIo {
                        pendingProofStore.promoteCapture(auth.user.id, RIDER_PROOF_SLOT_RETURN)
                    }
                    returnPhotoUri = promoted?.uri?.let(Uri::parse)
                    actionMessage = if (promoted != null) {
                        "ถ่ายรูปหลักฐานการคืนสินค้าแล้ว · ระบบเก็บรูปไว้จนกว่าจะส่งสำเร็จ"
                    } else {
                        "บันทึกรูปหลักฐานการคืนสินค้าไม่สำเร็จ กรุณาถ่ายใหม่"
                    }
                } else {
                    pendingProofIo {
                        pendingProofStore.cancelCapture(auth.user.id, RIDER_PROOF_SLOT_RETURN)
                    }
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                returnPhotoUri = null
                actionMessage = "บันทึกรูปหลักฐานการคืนสินค้าไม่สำเร็จ กรุณาถ่ายใหม่"
            } finally {
                returnPendingCameraUri = null
            }
        }
    }

    LaunchedEffect(snapshot?.deliveryReturn?.caseId) {
        val returnCase = snapshot?.deliveryReturn
        returnPendingCameraUri = null
        returnPhotoUri = returnCase?.let { current ->
            pendingProofIo {
                pendingProofStore.loadConfirmedMatching(
                    userId = auth.user.id,
                    slot = RIDER_PROOF_SLOT_RETURN,
                    workId = current.caseId,
                    mode = "deliveryReturn",
                    marketPickupId = null
                )
            }?.uri?.let(Uri::parse)
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

    fun startQueueGoNavigation(message: String = "นำทางด้วยแผนที่ QueueGo"): Boolean {
        if (!ensureLocationPermission()) {
            pendingMapRecenter = true
            return false
        }
        routeOverviewActive = false
        recenterSignal += 1
        actionMessage = message
        return true
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
            val opened = runCatching { context.startActivity(intent) }.isSuccess
            actionMessage = if (opened) {
                "เปิดสิทธิ์ 'แสดงทับแอปอื่น' เพื่อใช้ปุ่ม Q ระหว่างนำทาง"
            } else {
                "ไม่สามารถเปิดหน้าตั้งค่าสิทธิ์ปุ่ม Q ได้"
            }
            return
        }
        actionMessage = "ปุ่ม Q กลับ QueueGo พร้อมใช้งานแล้ว"
    }

    fun openCamera() {
        val job = verifyJob ?: run {
            actionMessage = "ไม่พบงานที่กำลังตรวจสอบ"
            return
        }
        val mode = verifyMode ?: run {
            actionMessage = "ไม่พบขั้นตอนการถ่ายรูป"
            return
        }
        scope.launch {
            try {
                val uri = pendingProofIo {
                    createEvidenceUri(context).also { created ->
                        pendingProofStore.reserveCapture(
                            userId = auth.user.id,
                            slot = RIDER_PROOF_SLOT_DELIVERY,
                            workId = job.id,
                            mode = mode,
                            marketPickupId = verifyMarketPickup?.pickupId,
                            uri = created
                        )
                    }
                }
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                try {
                    pendingProofIo {
                        pendingProofStore.cancelCapture(auth.user.id, RIDER_PROOF_SLOT_DELIVERY)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Best-effort cleanup; stale reservation is age-bounded and reconciled later.
                }
                pendingCameraUri = null
                actionMessage = "เปิดกล้องไม่สำเร็จ กรุณาลองอีกครั้ง"
            }
        }
    }

    fun openReturnCamera() {
        val returnCase = snapshot?.deliveryReturn ?: run {
            actionMessage = "ไม่พบงานคืนสินค้าที่กำลังดำเนินการ"
            return
        }
        scope.launch {
            try {
                val uri = pendingProofIo {
                    createEvidenceUri(context).also { created ->
                        pendingProofStore.reserveCapture(
                            userId = auth.user.id,
                            slot = RIDER_PROOF_SLOT_RETURN,
                            workId = returnCase.caseId,
                            mode = "deliveryReturn",
                            marketPickupId = null,
                            uri = created
                        )
                    }
                }
                returnPendingCameraUri = uri
                returnCameraLauncher.launch(uri)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                try {
                    pendingProofIo {
                        pendingProofStore.cancelCapture(auth.user.id, RIDER_PROOF_SLOT_RETURN)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Best-effort cleanup; stale reservation is age-bounded and reconciled later.
                }
                returnPendingCameraUri = null
                actionMessage = "เปิดกล้องคืนสินค้าไม่สำเร็จ กรุณาลองอีกครั้ง"
            }
        }
    }

    fun openVerification(mode: String, job: RiderJob) {
        actionBusy = true
        actionMessage = null
        scope.launch {
            try {
                val effectiveMode = when {
                    job.isExpress && mode == "pickup" -> "expressPickup"
                    job.isExpress && mode == "delivery" -> "expressDelivery"
                    else -> mode
                }
                val loaded = if (job.isExpress) {
                    emptyList<RiderItem>() to null
                } else coroutineScope {
                    val items = async { api.orderItems(auth, job.id) }
                    val details = async { api.verificationDetails(auth, job.id) }
                    items.await() to details.await()
                }
                val recoveredProof = pendingProofIo {
                    pendingProofStore.saveVerification(
                        auth.user.id,
                        RiderVerificationResume(job.id, effectiveMode, null)
                    )
                    pendingProofStore.loadConfirmedMatching(
                        userId = auth.user.id,
                        slot = RIDER_PROOF_SLOT_DELIVERY,
                        workId = job.id,
                        mode = effectiveMode,
                        marketPickupId = null
                    )
                }
                verifyMode = effectiveMode
                verifyJob = job
                verifyItems = loaded.first
                verifyDetails = loaded.second
                photoUri = recoveredProof?.uri?.let(Uri::parse)
                pendingCameraUri = null
                if (recoveredProof != null) {
                    actionMessage = "กู้คืนรูปหลักฐานที่ยังไม่ได้ส่งแล้ว"
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                actionMessage = failure.message ?: "เปิดขั้นตอนหลักฐานไม่สำเร็จ"
            } finally {
                actionBusy = false
            }
        }
    }

    fun openMarketPickupVerification(job: RiderJob, pickup: MarketPickup) {
        scope.launch {
            try {
                val recoveredProof = pendingProofIo {
                    pendingProofStore.saveVerification(
                        auth.user.id,
                        RiderVerificationResume(job.id, "marketPickup", pickup.pickupId)
                    )
                    pendingProofStore.loadConfirmedMatching(
                        userId = auth.user.id,
                        slot = RIDER_PROOF_SLOT_DELIVERY,
                        workId = job.id,
                        mode = "marketPickup",
                        marketPickupId = pickup.pickupId
                    )
                }
                verifyMode = "marketPickup"
                verifyJob = job
                verifyMarketPickup = pickup
                verifyItems = emptyList()
                verifyDetails = null
                photoUri = recoveredProof?.uri?.let(Uri::parse)
                pendingCameraUri = null
                actionMessage = if (recoveredProof != null) {
                    "กู้คืนรูปหลักฐานที่ยังไม่ได้ส่งแล้ว"
                } else {
                    null
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                actionMessage = failure.message ?: "เปิดขั้นตอนตรวจสอบไม่สำเร็จ"
            }
        }
    }

    fun closeVerification() {
        val closingUserId = auth.user.id
        scope.launch {
            try {
                pendingProofIo { pendingProofStore.clearVerification(closingUserId) }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                // Stale workflow state is validated against the authoritative Order on restore.
            }
        }
        verifyMode = null
        verifyJob = null
        verifyItems = emptyList()
        verifyDetails = null
        verifyMarketPickup = null
        photoUri = null
        pendingCameraUri = null
    }

    fun commitExpressAction(job: RiderJob, action: String, successMessage: String) {
        if (actionBusy || !requireServerNetwork()) return
        actionBusy = true
        actionMessage = null
        scope.launch {
            try {
                val status = api.expressAction(auth, job, action)
                val now = java.time.Instant.now().toString()
                val committedJob = job.copy(
                    status = status,
                    arrivedShopAt = if (action == "arrive_pickup") (job.arrivedShopAt ?: now) else job.arrivedShopAt,
                    arrivedCustomerAt = if (action == "arrive_dropoff") (job.arrivedCustomerAt ?: now) else job.arrivedCustomerAt
                )
                snapshot?.takeIf { it.activeJob?.id == job.id }?.copy(activeJob = committedJob)?.let {
                    snapshot = it
                    persistCommittedSnapshot(it)
                }
                actionMessage = successMessage
                runCatching { api.riderSnapshot(auth) }
                    .onFailure { if (it is CancellationException) throw it }
                    .onSuccess { snapshot = it; persistCommittedSnapshot(it) }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                actionMessage = failure.message ?: "อัปเดตงาน Express ไม่สำเร็จ"
            } finally {
                actionBusy = false
            }
        }
    }

    fun commitArrival(job: RiderJob, target: String) {
        if (actionBusy || !requireServerNetwork()) return
        val location = lastKnownLocation(context)
        actionBusy = true
        actionMessage = null
        scope.launch {
            var committed = false
            var updated: RiderSnapshot? = null
            runCatching {
                api.markArrival(
                    auth,
                    job.id,
                    target,
                    location?.first,
                    location?.second
                )
            }.onSuccess {
                committed = true
                actionMessage = if (target == "shop") {
                    "บันทึกถึงร้านแล้ว · แจ้งร้านแล้ว"
                } else {
                    "บันทึกถึงลูกค้าแล้ว · แจ้งลูกค้าแล้ว"
                }
                val committedMarker = "local:" + System.currentTimeMillis()
                val committedJob = if (target == "shop") {
                    job.copy(arrivedShopAt = job.arrivedShopAt ?: committedMarker)
                } else {
                    job.copy(arrivedCustomerAt = job.arrivedCustomerAt ?: committedMarker)
                }
                val committedSnapshot = snapshot?.takeIf { it.activeJob?.id == job.id }?.copy(activeJob = committedJob)
                if (committedSnapshot != null) {
                    snapshot = committedSnapshot
                    persistCommittedSnapshot(committedSnapshot)
                }
                updated = runCatching { api.riderSnapshot(auth) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrNull()
                    ?.takeIf { riderArrivalReadCanReplace(target, job.id, it) }
                    ?.let { server ->
                        committedSnapshot?.let { local -> riderMergeLiveSnapshot(local, server) } ?: server
                    }
                updated?.let {
                    snapshot = it
                    persistCommittedSnapshot(it)
                }
                if (updated == null && committedSnapshot != null) updated = committedSnapshot
            }.onFailure {
                actionMessage = it.message ?: "บันทึกการมาถึงไม่สำเร็จ"
            }
            actionBusy = false
            if (committed) {
                arrivalGuard = null
                if (target == "customer") {
                    openVerification("delivery", updated?.activeJob ?: job)
                }
            }
        }
    }

    fun requestArrival(job: RiderJob, target: String) {
        if (!requireServerNetwork()) return
        if (!ensureLocationPermission()) return
        val location = lastKnownLocation(context)
        val destinationLat = if (target == "shop") job.pickupLat else job.deliveryLat
        val destinationLng = if (target == "shop") job.pickupLng else job.deliveryLng
        val km = if (location != null && destinationLat != null && destinationLng != null) {
            riderDistanceKm(
                location.first,
                location.second,
                destinationLat,
                destinationLng
            )
        } else null
        val meters = km?.let { kotlin.math.round(it * 1000.0).toInt().coerceAtLeast(1) }
        if (km != null && km <= 0.2) {
            commitArrival(job, target)
        } else {
            arrivalGuard = RiderArrivalGuardState(job, target, meters)
        }
    }

    fun playRiderOfferAlert() {
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100) }.getOrNull() ?: return
        scope.launch {
            try {
                if (!runCatching { tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 650) }.getOrDefault(false)) return@launch
                delay(720)
                runCatching { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 450) }
                delay(520)
            } finally {
                runCatching { tone.release() }
            }
        }
    }

    suspend fun refreshSnapshot() = snapshotMutex.withLock {
        runCatching { api.riderSnapshot(auth) }
            .onSuccess {
                val liveSnapshot = if (it.offeredJob != null && riderOfferSecondsLeft(it.offeredJob.offerExpiresAt) <= 0)
                    it.copy(offeredJob = null) else it
                val offerKey = liveSnapshot.offeredJob?.let { job ->
                    job.id + ":" + (job.offerExpiresAt ?: "")
                }
                if (offerKey != null && offerKey != lastOfferAlertKey) {
                    lastOfferAlertKey = offerKey
                    val expiry = riderOfferExpiryMillis(it.offeredJob?.offerExpiresAt)
                    val orderId = it.offeredJob?.id
                    if (expiry != null && orderId != null && expiry > RiderServerClock.nowEpochMillis() &&
                        consumeRiderAlert(context, riderOfferAlertKey(auth.user.id, orderId, expiry))) playRiderOfferAlert()
                    actionMessage = "มีงานใหม่ที่ระบบจัดให้ กรุณาตอบรับภายใน 30 วินาที"
                    // A selected 30-second offer must be visible immediately even if the Rider
                    // was viewing earnings/profile/messages. There is no shared job pool.
                    chatJob = null
                    activeTab = "home"
                }
                val currentSnapshot = snapshot
                val resolvedSnapshot = if (currentSnapshot?.activeJob != null && liveSnapshot.activeJob == null) {
                    val profileId = liveSnapshot.riderProfileId ?: currentSnapshot.riderProfileId
                    val lookup = profileId?.let { id ->
                        runCatching { api.notificationJob(auth, currentSnapshot.activeJob.id, id) }
                            .onFailure { if (it is CancellationException) throw it }
                    }
                    riderResolveMissingActiveSnapshot(
                        currentSnapshot,
                        liveSnapshot,
                        lookup?.getOrNull(),
                        lookup?.isSuccess == true
                    )
                } else liveSnapshot
                val mergedSnapshot = currentSnapshot?.let { current ->
                    riderMergeLiveSnapshot(current, resolvedSnapshot)
                } ?: resolvedSnapshot
                snapshot = mergedSnapshot
                if (mergedSnapshot != currentSnapshot) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { pushStore.saveHomeSnapshot(auth.user.id, mergedSnapshot) }
                    }
                }
                reconcileRiderOfferNotifications(context, auth.user.id, mergedSnapshot.offeredJob?.id)
                consecutiveSnapshotFailures = 0
                loadError = null
                if (mergedSnapshot.activeJob == null) RiderReturnService.stop(context)
                val now = System.currentTimeMillis()
                if (mergedSnapshot.online && now - lastLocationPushAt >= 10_000L) {
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
                // A transient refresh must never cover an already-restored safe snapshot.
                // Keep the cached/live job visible. After two consecutive misses, show a compact
                // reconnecting notice instead of replacing the job UI with an error screen.
                consecutiveSnapshotFailures = (consecutiveSnapshotFailures + 1).coerceAtMost(99)
                if (snapshot == null) loadError = "ยังโหลดสถานะ Rider ไม่สำเร็จ: " +
                    (it.message ?: "ตรวจสอบอินเทอร์เน็ตแล้วลองอีกครั้ง")
            }
    }

    LaunchedEffect(pushTarget?.eventId, auth.user.id, auth.session.accessToken, lifecycle) {
        val target = pushTarget ?: return@LaunchedEffect
        if (handledPushEventId == target.eventId) return@LaunchedEffect
        handledPushEventId = target.eventId
        chatJob = null
        detailJob = null
        closeVerification()
        activeTab = "home"
        val reference = target.referenceId
        if (target.type in setOf("order", "rider_offer") && reference.isNotBlank()) {
            if (runCatching { java.util.UUID.fromString(reference) }.isSuccess) pendingPushReference = reference
            else actionMessage = "หมายเลขออเดอร์ในการแจ้งเตือนไม่ถูกต้อง"
        } else refreshSnapshot()
    }
    LaunchedEffect(pendingPushReference, auth.user.id, auth.session.accessToken, lifecycle) {
        val reference = pendingPushReference ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (pendingPushReference == reference) {
                try {
                    refreshSnapshot()
                    currentCoroutineContext().ensureActive()
                    if (loadError != null) error(loadError!!)
                    val current = snapshot ?: error("ยังโหลดข้อมูลไรเดอร์ไม่สำเร็จ")
                    if (current.activeJob?.matchesPushReference(reference) == true || current.offeredJob?.matchesPushReference(reference) == true) {
                        pendingPushReference = null
                    } else {
                        val profileId = current.riderProfileId ?: error("ยังโหลดโปรไฟล์ไรเดอร์ไม่สำเร็จ")
                        val notifiedJob = api.notificationJob(auth, reference, profileId)
                        currentCoroutineContext().ensureActive()
                        if (pendingPushReference != reference) return@repeatOnLifecycle
                        if (notifiedJob != null) detailJob = notifiedJob
                        else actionMessage = "การแจ้งเตือนนี้หมดอายุ หรือคุณไม่มีสิทธิ์ดูออเดอร์นี้แล้ว"
                        pendingPushReference = null
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    actionMessage = "ยังเปิดออเดอร์จากการแจ้งเตือนไม่ได้ กำลังเชื่อมต่อใหม่"
                }
                if (pendingPushReference == reference) delay(5_000)
            }
        }
    }
    LaunchedEffect(activeTab) {
        if (activeTab != "home") {
            pendingPushReference = null
            detailJob = null
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
    LaunchedEffect(
        auth.session.accessToken,
        lifecycle,
        networkAvailable,
        snapshot?.online,
        snapshot?.activeJob?.id,
        snapshot?.laundry?.activeJob?.jobId,
        snapshot?.offeredJob?.id
    ) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                refreshSnapshot()
                delay(
                    riderSnapshotPollIntervalMs(
                        networkAvailable = networkAvailable,
                        online = snapshot?.online == true,
                        hasActiveWork = snapshot?.activeJob != null || snapshot?.laundry?.activeJob != null,
                        hasOffer = snapshot?.offeredJob != null
                    )
                )
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

    LaunchedEffect(auth.user.id, snapshot?.activeJob?.id, snapshot?.activeJob?.status,
        snapshot?.activeJob?.customerArrived, snapshot?.marketPickups?.map { it.pickupId to it.done }, actionBusy) {
        if (actionBusy) return@LaunchedEffect
        val saved = pendingProofIo {
            pendingProofStore.loadVerification(auth.user.id)
        } ?: return@LaunchedEffect
        val current = snapshot ?: return@LaunchedEffect
        val job = riderSnapshotJobForCommit(current, saved.workId) ?: run {
            if (verifyMode != null) closeVerification()
            return@LaunchedEffect
        }
        val pickup = current.marketPickups.firstOrNull { it.pickupId == saved.pickupId && !it.done }
        if (!riderVerificationResumeAllowed(saved.mode, job.status, job.customerArrived, pickup != null)) {
            pendingProofIo { pendingProofStore.clearVerification(auth.user.id) }
            if (verifyMode != null) closeVerification()
            return@LaunchedEffect
        }
        if (verifyMode != null) {
            verifyJob = job
            return@LaunchedEffect
        }
        // Restore identity and the one saved photo immediately, even while offline.
        verifyMode = saved.mode
        verifyJob = job
        verifyMarketPickup = pickup
        photoUri = pendingProofIo {
            pendingProofStore.loadConfirmedMatching(
                auth.user.id,
                RIDER_PROOF_SLOT_DELIVERY,
                job.id,
                saved.mode,
                saved.pickupId
            )
        }?.uri?.let(Uri::parse)
        if (pickup == null && !job.isExpress) {
            try {
                verifyItems = api.orderItems(auth, job.id)
                verifyDetails = api.verificationDetails(auth, job.id)
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { actionMessage = "กู้คืนขั้นตอนงานแล้ว · รายการจะพร้อมเมื่อเชื่อมต่อได้" }
        } else if (job.isExpress) {
            verifyItems = emptyList()
            verifyDetails = null
        }
    }

    if (chatJob != null || pickupChatJob != null) {
        val currentChat = pickupChatJob ?: chatJob!!
        Box(modifier.fillMaxSize()) {
            RiderChatScreen(
                auth = auth,
                job = currentChat,
                onBack = { chatJob = null; pickupChatJob = null },
                onCallCustomer = {
                    if (pickupChatJob != null) startShopVoice(currentChat.id) else startCustomerVoice(currentChat.id)
                },
                contactLabel = if (pickupChatJob != null) "แชตออเดอร์ · ร้านและลูกค้า" else null,
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
                if (actionBusy) return@VerificationScreen
                val location = lastKnownLocation(context)
                val syncRequest = RiderProofSyncRequest(
                    userId = auth.user.id,
                    workId = job.id,
                    mode = verifyMode ?: return@VerificationScreen,
                    photoUri = uri.toString(),
                    marketPickupId = verifyMarketPickup?.pickupId,
                    marketOrderId = job.marketOrderId,
                    marketPickupAmount = verifyMarketPickup?.shopAmount,
                    latitude = location?.first,
                    longitude = location?.second
                )
                RiderProofSyncScheduler.enqueue(context, syncRequest)
                if (!networkAvailable) {
                    actionMessage = "บันทึกรูปแล้ว · จะซิงก์อัตโนมัติเมื่อมีอินเทอร์เน็ต"
                    return@VerificationScreen
                }
                if (!requireServerNetwork()) return@VerificationScreen
                actionBusy = true
                actionMessage = null
                scope.launch {
                    val result = runCatching {
                        when (verifyMode) {
                            "expressPickup" -> api.expressPickupWithPhoto(
                                context,
                                auth,
                                job,
                                uri,
                                location?.first,
                                location?.second
                            )
                            "expressDelivery" -> api.expressCompleteWithPhoto(
                                context,
                                auth,
                                job,
                                uri,
                                location?.first,
                                location?.second
                            )
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
                    result.onSuccess { mutationResult ->
                        RiderProofSyncScheduler.cancel(context, syncRequest)
                        val completedMode = verifyMode
                        val completedMarketPickupId = verifyMarketPickup?.pickupId

                        if (completedMode == "expressPickup" || completedMode == "expressDelivery") {
                            val before = snapshot
                            val pickupDone = completedMode == "expressPickup"
                            val localAfter = if (pickupDone) {
                                before?.copy(
                                    activeJob = before.activeJob?.takeIf { it.id == job.id }?.copy(status = "picked_up")
                                        ?: job.copy(status = "picked_up"),
                                    offeredJob = null
                                )
                            } else {
                                before?.copy(activeJob = null, offeredJob = null)
                            }
                            actionMessage = if (pickupDone) "รับพัสดุแล้ว · กดเริ่มนำส่ง" else "ส่งพัสดุสำเร็จแล้ว"
                            if (pickupDone) {
                                playRiderEventFeedback(eventFeedbackView, RiderEventFeedbackKind.PICKUP_SUCCESS)
                            } else {
                                RiderReturnService.stop(context)
                            }
                            if (localAfter != null) {
                                snapshot = localAfter
                                persistCommittedSnapshot(localAfter)
                            }
                            scope.launch {
                                try {
                                    pendingProofIo {
                                        pendingProofStore.clearConfirmedMatching(
                                            userId = auth.user.id,
                                            slot = RIDER_PROOF_SLOT_DELIVERY,
                                            workId = job.id,
                                            mode = completedMode,
                                            marketPickupId = null
                                        )
                                    }
                                } catch (failure: CancellationException) {
                                    throw failure
                                } catch (_: Exception) {
                                    // Express server mutation already committed; stale proof is reconciled later.
                                }
                            }
                            closeVerification()
                            runCatching { api.riderSnapshot(auth) }
                                .onFailure { if (it is CancellationException) throw it }
                                .onSuccess { snapshot = it; persistCommittedSnapshot(it) }
                            return@onSuccess
                        }

                        var pickupCommittedStatus = if (completedMode == "pickup") {
                            riderPickupCommittedStatus(mutationResult)
                        } else null

                        // Current Production pickup normally returns in_progress. A legacy/idempotent
                        // picked_up row still needs one deliver transition before QueueGo navigation,
                        // otherwise the Rider would see "นำทางไปลูกค้า" again.
                        if (completedMode == "pickup" && pickupCommittedStatus == "picked_up") {
                            if (runCatching { api.startDelivery(auth, job.copy(status = "picked_up")) }.isSuccess) {
                                pickupCommittedStatus = "in_progress"
                            }
                        }

                        val before = snapshot
                        val bundlePickup = completedMode == "pickup" &&
                            before?.bundle?.activeOrders?.any { it.job.id == job.id } == true
                        actionMessage = when (completedMode) {
                            "pickup" -> if (pickupCommittedStatus == "in_progress")
                                if (bundlePickup) "รับสินค้าแล้ว · กำลังเปิดนำทางจุดถัดไป"
                                else "รับสินค้าแล้ว · กำลังเปิดนำทางไปลูกค้า"
                            else "รับสินค้าแล้ว · กดเริ่มจัดส่งเพื่อไปลูกค้า"
                            "marketPickup" -> "รับสินค้าจุดนี้แล้ว"
                            else -> "จัดส่งสำเร็จ"
                        }

                        if (completedMode == "pickup" || completedMode == "marketPickup") {
                            playRiderEventFeedback(eventFeedbackView, RiderEventFeedbackKind.PICKUP_SUCCESS)
                        }

                        // The successful mutation is authoritative. Promote the local
                        // workflow immediately so a slow/failed snapshot refresh can
                        // never return Rider to the same verification/photo step.
                        val localAfterSuccess = when (completedMode) {
                            "pickup" -> before?.let {
                                riderApplyPickupCommit(
                                    it,
                                    job,
                                    pickupCommittedStatus ?: "picked_up"
                                )
                            }
                            "marketPickup" -> {
                                val pickupId = completedMarketPickupId
                                before?.copy(
                                    marketPickups = before.marketPickups.map { pickup ->
                                        if (pickup.pickupId == pickupId) pickup.copy(status = "PICKED_UP") else pickup
                                    }
                                )
                            }
                            else -> before?.let { previous ->
                                riderAfterDeliveryCommit(previous, job.id)
                            }
                        }
                        if (localAfterSuccess != null) {
                            snapshot = localAfterSuccess
                            persistCommittedSnapshot(localAfterSuccess)
                        }

                        if (completedMode == "delivery") {
                            RiderReturnService.stop(context)
                            deliverySuccessJob = job
                        }
                        scope.launch {
                            try {
                                pendingProofIo {
                                    pendingProofStore.clearConfirmedMatching(
                                        userId = auth.user.id,
                                        slot = RIDER_PROOF_SLOT_DELIVERY,
                                        workId = job.id,
                                        mode = completedMode.orEmpty(),
                                        marketPickupId = completedMarketPickupId
                                    )
                                }
                            } catch (failure: CancellationException) {
                                throw failure
                            } catch (_: Exception) {
                                // Server mutation already committed; stale local proof is reconciled later.
                            }
                        }
                        closeVerification()

                        val updated = runCatching { api.riderSnapshot(auth) }
                            .onFailure { if (it is CancellationException) throw it }
                            .getOrNull()
                        val committedRead = updated?.takeIf { candidate ->
                            val visible = riderVerificationCommitVisible(
                                completedMode.orEmpty(),
                                job.id,
                                completedMarketPickupId,
                                candidate
                            )
                            val monotonicPickup = completedMode != "pickup" ||
                                pickupCommittedStatus == null ||
                                riderPickupReadCanReplace(pickupCommittedStatus, job.id, candidate)
                            visible && monotonicPickup
                        }
                        if (committedRead != null) {
                            snapshot = committedRead
                            persistCommittedSnapshot(committedRead)
                        }

                        if (completedMode == "delivery") {
                            val promoted = committedRead?.activeJob ?: localAfterSuccess?.activeJob
                            if (promoted != null && promoted.id != job.id) {
                                deliverySuccessJob = null
                            }
                        }
                        if (completedMode == "pickup" && pickupCommittedStatus == "in_progress") {
                            actionMessage = "รับสินค้าแล้ว · นำทางต่อด้วยแผนที่ QueueGo"
                        }
                    }.onFailure {
                        playRiderEventFeedback(eventFeedbackView, RiderEventFeedbackKind.ACTION_ERROR)
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
                    onOpenHome = { activeTab = "home" },
                    onNavigationSettings = { configureReturnControl() },
                    workSettings = {
                        RiderWorkPreferencesControl(
                            preferences = workPreferences,
                            busy = workPreferencesBusy || !dispatchPreferencesHydrated,
                            error = workPreferencesError,
                            onRetry = ::retryWorkPreferences,
                            onChange = ::saveWorkPreferences,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    },
                    routeSettings = {
                        RiderSettingDestinationRow(
                            enabled = destinationLoaded && !actionBusy && !workPreferencesBusy
                        ) { showDestination = true }
                    },
                    onLogout = onLogout,
                    modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 748.dp).fillMaxSize()
                )
            }
            if (showDestination) {
                RiderMyDestinationDialog(
                    auth = auth, api = api, current = destinationPreference,
                    currentGps = navigationFix?.point?.let { it.latitude to it.longitude },
                    onSaved = { destinationPreference = it },
                    onClose = { showDestination = false }
                )
            }
            RiderBottomNavigation(
                activeTab = activeTab,
                unreadMessages = unreadMessages.coerceAtMost(99),
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
        if (actionBusy || snapshot?.activeJob != null || snapshot?.laundry?.activeJob != null || snapshot == null) return
        if (enabled) {
            val diagnostics = readRiderDiagnostics(context)
            if (!diagnostics.networkReady) {
                actionMessage = "ต้องเชื่อมต่ออินเทอร์เน็ตก่อนเปิดรับงาน"
                return
            }
            if (!diagnostics.locationServices) {
                actionMessage = "ต้องเปิด GPS / Location ก่อนเปิดรับงาน"
                runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                return
            }
        }
        if (enabled && !riderFirebaseConfigured(context)) {
            actionMessage = "ระบบแจ้งเตือน Android ยังไม่พร้อม จึงยังเปิดรับงานไม่ได้"
            return
        }
        if (enabled && !riderNotificationsReady(context)) {
            if (
                Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                actionMessage = "ต้องอนุญาตการแจ้งเตือนก่อนเปิดรับงาน"
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                actionMessage = "ต้องเปิดการแจ้งเตือนงาน QueueGo ก่อนเปิดรับงาน"
                runCatching { context.startActivity(riderNotificationSettingsIntent(context)) }
            }
            return
        }
        if (enabled && !ensureLocationPermission()) return
        actionBusy = true
        scope.launch {
            if (enabled) {
                val pushBound = runCatching {
                    syncRiderNativePush(context, auth, api, pushStore)
                }.getOrElse {
                    actionMessage = "เชื่อมการแจ้งเตือนงานกับอุปกรณ์นี้ไม่สำเร็จ กรุณาลองอีกครั้ง"
                    false
                }
                if (!pushBound) {
                    if (actionMessage == null) {
                        actionMessage = "ยังเชื่อมการแจ้งเตือนงานกับ session นี้ไม่ได้"
                    }
                    actionBusy = false
                    return@launch
                }
            }
            val loc = lastKnownLocation(context)
            runCatching { api.setOnline(auth, enabled, loc?.first, loc?.second) }
                .onSuccess {
                    if (!enabled) setStopAfterWorkPending(false)
                    playRiderEventFeedback(
                        eventFeedbackView,
                        if (enabled) RiderEventFeedbackKind.GO_ONLINE else RiderEventFeedbackKind.GO_OFFLINE
                    )
                    actionMessage = if (enabled) "เปิดรับงานแล้ว" else "ปิดรับงานแล้ว"
                    runCatching { api.riderSnapshot(auth) }.onSuccess { snapshot = it }
                        .onFailure { actionMessage = it.message ?: "โหลดสถานะรับงานไม่สำเร็จ" }
                }
                .onFailure {
                    playRiderEventFeedback(eventFeedbackView, RiderEventFeedbackKind.ACTION_ERROR)
                    actionMessage = it.message ?: "เปลี่ยนสถานะรับงานไม่สำเร็จ"
                }
            actionBusy = false
        }
    }

    LaunchedEffect(
        dispatchPreferencesHydrated,
        stopAfterWorkPending,
        networkAvailable,
        snapshot?.online,
        snapshot?.activeJob?.id,
        snapshot?.laundry?.activeJob?.jobId,
        snapshot?.deliveryReturn?.caseId,
        snapshot?.offeredJob?.id,
        lifecycle
    ) {
        if (!dispatchPreferencesHydrated) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (stopAfterWorkPending) {
                val current = snapshot
                if (current == null) {
                    delay(1_000L)
                    continue
                }
                if (!current.online) {
                    setStopAfterWorkPending(false)
                    return@repeatOnLifecycle
                }
                if (!riderStopAfterWorkReady(
                        pending = stopAfterWorkPending,
                        networkAvailable = networkAvailable,
                        online = current.online,
                        hasActiveJob = current.activeJob != null,
                        hasLaundryJob = current.laundry.activeJob != null,
                        hasDeliveryReturn = current.deliveryReturn != null,
                        hasOffer = current.offeredJob != null,
                        actionBusy = actionBusy
                    )
                ) {
                    delay(1_000L)
                    continue
                }

                actionBusy = true
                val loc = lastKnownLocation(context)
                val stopped = runCatching {
                    api.setOnline(auth, false, loc?.first, loc?.second)
                }.isSuccess
                if (stopped) {
                    setStopAfterWorkPending(false)
                    playRiderEventFeedback(eventFeedbackView, RiderEventFeedbackKind.GO_OFFLINE)
                    actionMessage = "จบงานแล้ว · ปิดรับงานให้แล้ว"
                    runCatching { api.riderSnapshot(auth) }
                        .onSuccess {
                            snapshot = it
                            persistCommittedSnapshot(it)
                        }
                    actionBusy = false
                    return@repeatOnLifecycle
                }

                actionBusy = false
                actionMessage = "ยังปิดรับงานไม่สำเร็จ · ระบบจะลองอีกครั้งเมื่อเชื่อมต่อพร้อม"
                delay(5_000L)
            }
        }
    }

    fun acceptTargetedOffer(job: RiderJob, automatic: Boolean) {
        if (actionBusy) return
        if (!networkAvailable) {
            if (!automatic) actionMessage = RIDER_NETWORK_GUARD_MESSAGE
            return
        }
        val current = snapshot ?: return
        val liveOffer = current.offeredJob?.takeIf {
            it.id == job.id && it.offerExpiresAt == job.offerExpiresAt
        } ?: return
        if (riderOfferSecondsLeft(liveOffer.offerExpiresAt) <= 0) return

        actionBusy = true
        scope.launch {
            runCatching { api.acceptOffer(auth, liveOffer) }
                .onSuccess {
                    playRiderEventFeedback(eventFeedbackView, RiderEventFeedbackKind.JOB_ACCEPTED)
                    actionMessage = if (automatic) {
                        "รับงานอัตโนมัติแล้ว · นำทางด้วยแผนที่ QueueGo"
                    } else {
                        "รับงานสำเร็จ · นำทางด้วยแผนที่ QueueGo"
                    }

                    val acceptedFallback = liveOffer.copy(
                        status = "rider_assigned",
                        offerExpiresAt = null
                    )
                    val acceptedSnapshot = current.copy(
                        activeJob = acceptedFallback,
                        offeredJob = null,
                        marketPickups = emptyList()
                    )
                    snapshot = acceptedSnapshot
                    persistCommittedSnapshot(acceptedSnapshot)

                    // Accept is the start of work. Enter QueueGo follow/navigation mode
                    // immediately; there is no second navigation action for the rider.
                    routeOverviewActive = false
                    recenterSignal += 1

                    val updated = runCatching { api.riderSnapshot(auth) }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrNull()
                        ?.takeIf { it.activeJob?.id == acceptedFallback.id }
                        ?.let { riderMergeLiveSnapshot(acceptedSnapshot, it) }
                    if (updated != null) {
                        snapshot = updated
                        persistCommittedSnapshot(updated)
                    }
                }
                .onFailure {
                    playRiderEventFeedback(eventFeedbackView, RiderEventFeedbackKind.ACTION_ERROR)
                    actionMessage = if (automatic) {
                        "รับงานอัตโนมัติไม่สำเร็จ · คุณยังกดรับงานได้ตามปกติ"
                    } else {
                        it.message ?: "รับงานไม่สำเร็จ"
                    }
                }
            actionBusy = false
        }
    }

    // Navigation and visible UI renew the same lease with the same on-device preference.
    SideEffect {
        RiderNavigationDispatchReadiness.bind(RiderNavigationDispatchReadiness.Binding(
            auth.user.id, auth.session.sessionId, snapshot?.riderProfileId, snapshot?.activeJob?.id,
            networkAvailable && snapshot?.online == true && !actionBusy &&
                snapshot?.deliveryReturn == null && snapshot?.laundry?.activeJob == null))
    }
    DisposableEffect(auth.user.id, auth.session.sessionId) {
        onDispose { RiderNavigationDispatchReadiness.clear(auth.user.id) }
    }
    LaunchedEffect(autoAcceptEnabled, stopAfterWorkPending, networkAvailable, snapshot?.online,
        snapshot?.deliveryReturn?.caseId, snapshot?.laundry?.activeJob?.jobId,
        actionBusy, lifecycle, auth.session.accessToken) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                runCatching { RiderNavigationDispatchReadiness.syncForeground(context, api, auth) }
                    .onFailure { if (it is CancellationException) throw it }
                delay(20_000L)
            }
        }
    }

    LaunchedEffect(
        dispatchPreferencesHydrated,
        autoAcceptEnabled,
        stopAfterWorkPending,
        networkAvailable,
        snapshot?.online,
        snapshot?.activeJob?.id,
        snapshot?.laundry?.activeJob?.jobId,
        snapshot?.offeredJob?.id,
        snapshot?.offeredJob?.offerExpiresAt,
        actionBusy,
        lifecycle
    ) {
        if (!dispatchPreferencesHydrated) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val current = snapshot ?: return@repeatOnLifecycle
            val offer = current.offeredJob ?: return@repeatOnLifecycle
            val key = offer.id + ":" + (offer.offerExpiresAt ?: "")
            if (autoAcceptAttemptKey == key) return@repeatOnLifecycle
            if (!riderAutoAcceptEligible(
                    enabled = riderAutoAcceptAllowedWhileStopping(autoAcceptEnabled, stopAfterWorkPending),
                    networkAvailable = networkAvailable,
                    online = current.online,
                    hasActiveWork = current.activeJob != null || current.laundry.activeJob != null,
                    actionBusy = actionBusy,
                    secondsLeft = riderOfferSecondsLeft(offer.offerExpiresAt)
                )
            ) return@repeatOnLifecycle

            delay(250L)
            val latest = snapshot?.offeredJob ?: return@repeatOnLifecycle
            val latestKey = latest.id + ":" + (latest.offerExpiresAt ?: "")
            if (latestKey != key) return@repeatOnLifecycle
            if (!riderAutoAcceptEligible(
                    enabled = riderAutoAcceptAllowedWhileStopping(autoAcceptEnabled, stopAfterWorkPending),
                    networkAvailable = networkAvailable,
                    online = snapshot?.online == true,
                    hasActiveWork = snapshot?.activeJob != null || snapshot?.laundry?.activeJob != null,
                    actionBusy = actionBusy,
                    secondsLeft = riderOfferSecondsLeft(latest.offerExpiresAt)
                )
            ) return@repeatOnLifecycle

            autoAcceptAttemptKey = key
            acceptTargetedOffer(latest, automatic = true)
        }
    }

    val shortHome = LocalConfiguration.current.screenHeightDp <= 760
    val returnMapJob = snapshot?.deliveryReturn?.takeIf { it.canReturnToShop }?.let { returnCase ->
        snapshot?.activeJob?.copy(
            status = "ready",
            pickupAddress = returnCase.shopAddress,
            pickupLat = returnCase.shopLatitude,
            pickupLng = returnCase.shopLongitude
        )
    }
    val mapJob = returnMapJob ?: snapshot?.activeJob ?: snapshot?.laundry?.activeJob?.asMapJob() ?: snapshot?.offeredJob
    val activeNavigationWork =
        snapshot?.deliveryReturn?.canReturnToShop == true ||
            snapshot?.activeJob != null ||
            snapshot?.laundry?.activeJob != null
    val connectivityNotice = riderConnectivityNotice(
        consecutiveRefreshFailures = consecutiveSnapshotFailures,
        hasSnapshot = snapshot != null,
        hasActiveWork = activeNavigationWork || snapshot?.offeredJob != null,
        networkAvailable = networkAvailable
    )
    LaunchedEffect(activeNavigationWork, navigationSessionStartedMs) {
        if (!activeNavigationWork || navigationSessionStartedMs == null) return@LaunchedEffect
        while (true) {
            navigationClockMs = SystemClock.elapsedRealtime()
            delay(30_000L)
        }
    }
    val reliableSpeedKph = riderReliableSpeedKph(navigationFix)
    val continuousNavigationMs = if (activeNavigationWork) {
        riderContinuousNavigationMs(navigationSessionStartedMs, navigationClockMs)
    } else {
        0L
    }
    val restReminderDue = activeNavigationWork && riderRestReminderDue(
        navigationSessionStartedMs,
        navigationClockMs,
        dismissedRestSessionStart
    )
    val navigationHud = if (activeNavigationWork) riderNavigationHudInfo(navigationGuidance) else null
    val navigationSpeedLimitKph = if (activeNavigationWork) riderNavigationSpeedLimitKph(navigationGuidance) else null
    val navigationCustomerNote = if (activeNavigationWork) {
        riderNavigationCustomerNote(
            note = mapJob?.note,
            delivering = mapJob?.isDelivering == true,
            guidance = navigationGuidance
        )
    } else {
        null
    }

    val customerEtaJob by rememberUpdatedState(snapshot?.activeJob)
    val customerEtaMapJobId by rememberUpdatedState(mapJob?.id)
    val customerEtaGuidance by rememberUpdatedState(navigationGuidance)
    LaunchedEffect(auth.user.id, auth.session.sessionId) {
        while (true) {
            val job = customerEtaJob
            val guidance = customerEtaGuidance
            val eta = guidance.etaSeconds
            if (
                job != null &&
                job.id == customerEtaMapJobId &&
                job.marketOrderId == null &&
                job.status.lowercase() in setOf("picked_up", "in_progress") &&
                !guidance.weakGps &&
                !guidance.offRoute &&
                eta != null &&
                eta.isFinite() &&
                eta in 0.0..14_400.0
            ) {
                // Best-effort telemetry only. The backend verifies that this QT is
                // the authoritative customer dropoff before accepting the snapshot.
                runCatching {
                    api.publishCustomerEta(auth, job.id, eta.toInt().coerceIn(0, 14_400))
                }
            }
            delay(15_000L)
        }
    }

    LaunchedEffect(
        activeNavigationWork,
        navigationFix,
        navigationGuidance,
        reliableSpeedKph,
        navigationSessionStartedMs
    ) {
        val now = navigationFix
            ?.elapsedRealtimeNanos
            ?.div(1_000_000L)
            ?.takeIf { it > 0L }
            ?: SystemClock.elapsedRealtime()

        if (!activeNavigationWork) {
            unexpectedStopLastMovementMs = null
            dismissedUnexpectedStopEpisode = null
            showUnexpectedStopSafety = false
            return@LaunchedEffect
        }

        if (unexpectedStopLastMovementMs == null) {
            unexpectedStopLastMovementMs = navigationSessionStartedMs ?: now
        }

        if (riderUnexpectedStopResetRequired(navigationGuidance, reliableSpeedKph)) {
            unexpectedStopLastMovementMs = now
            dismissedUnexpectedStopEpisode = null
        }
    }

    val unexpectedStopDue = riderUnexpectedStopDue(
        activeNavigationWork = activeNavigationWork,
        guidance = navigationGuidance,
        reliableSpeedKph = reliableSpeedKph,
        lastMovementElapsedMs = unexpectedStopLastMovementMs,
        nowElapsedMs = navigationClockMs,
        dismissedEpisodeStartMs = dismissedUnexpectedStopEpisode
    )

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(QgRiderBg)
    ) {
        val navigationDensity = LocalDensity.current
        val navigationTopInsetPx = with(navigationDensity) {
            when {
                activeNavigationWork && navigationCustomerNote != null -> 180.dp.roundToPx()
                activeNavigationWork -> 132.dp.roundToPx()
                else -> 72.dp.roundToPx()
            }
        }
        var navigationBottomInsetPx by remember(navigationDensity) {
            mutableIntStateOf(with(navigationDensity) { 68.dp.roundToPx() })
        }

        QueueGoRiderNavigationMap(
            modifier = Modifier.fillMaxSize(),
            stateKey = auth.user.id,
            job = mapJob,
            marketPickups = snapshot?.marketPickups.orEmpty(),
            bundleOrders = snapshot?.bundle?.activeOrders.orEmpty(),
            online = snapshot?.online == true,
            demandZones = if (mapJob == null && snapshot?.online == true) demandZones else emptyList(),
            recenterSignal = recenterSignal,
            overviewSignal = overviewSignal,
            compassResetSignal = compassResetSignal,
            viewportInsets = NavigationViewportInsets(
                topPx = navigationTopInsetPx,
                bottomPx = navigationBottomInsetPx
            ),
            onMapReady = { },
            onGuidance = { navigationGuidance = it }
        )

        if (!activeNavigationWork) {
            RiderMapWordmark(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
            )
        }

        if (activeNavigationWork) {
            navigationHud?.let { hud ->
                RiderManeuverBanner(
                    hud = hud,
                    customerNote = navigationCustomerNote,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }

        if (snapshot?.online == true || activeNavigationWork) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        end = 10.dp,
                        top = when {
                            activeNavigationWork && navigationCustomerNote != null -> 180.dp
                            activeNavigationWork && navigationHud != null -> 132.dp
                            else -> 12.dp
                        }
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    navigationSpeedLimitKph?.let { RiderSpeedLimitSign(it) }
                    RiderBlueprintSpeedPill(reliableSpeedKph)
                }
            }
        }

        if (activeNavigationWork && (restReminderDue || unexpectedStopDue)) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = when {
                            navigationCustomerNote != null -> 180.dp
                            navigationHud != null -> 132.dp
                            else -> 64.dp
                        },
                        end = 10.dp,
                        start = 78.dp
                    ),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (restReminderDue) {
                    Card(
                        modifier = Modifier.widthIn(max = 250.dp),
                        shape = RoundedCornerShape(17.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFAFFF7E8)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD99A))
                    ) {
                        Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                            Text("แนะนำให้พักเมื่อสะดวก", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF6B4A10))
                            Text(
                                "นำทางต่อเนื่อง " + riderDriveDurationLabel(continuousNavigationMs) +
                                    " · หลังจบงานนี้ควรหยุดพักก่อนรับงานถัดไป",
                                fontSize = 8.5.sp,
                                lineHeight = 12.sp,
                                color = Color(0xFF78694E),
                                modifier = Modifier.padding(top = 3.dp)
                            )
                            if (stopAfterWorkPending) {
                                Text(
                                    "ตั้งให้หยุดรับงานอัตโนมัติหลังงานนี้แล้ว",
                                    color = Color(0xFF287A4B),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 5.dp)
                                )
                                TextButton(
                                    onClick = {
                                        setStopAfterWorkPending(false)
                                        actionMessage = "ยกเลิกการหยุดรับงานหลังจบงานแล้ว"
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("ยกเลิก", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = QgRed)
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                        setStopAfterWorkPending(true)
                                        actionMessage = "ตั้งให้หยุดรับงานหลังจบงานนี้แล้ว"
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("จบงานนี้แล้วหยุดรับงาน", fontSize = 9.sp, fontWeight = FontWeight.Black, color = QgRed)
                                }
                                TextButton(
                                    onClick = { dismissedRestSessionStart = navigationSessionStartedMs },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("รับทราบ", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = QgMuted)
                                }
                            }
                        }
                    }
                }
                if (unexpectedStopDue) {
                    Card(
                        modifier = Modifier.widthIn(max = 270.dp),
                        shape = RoundedCornerShape(17.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFAFFF2F3)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFC8CE))
                    ) {
                        Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                            Text(
                                "คุณโอเคไหม?",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = QgRed
                            )
                            Text(
                                "ระบบพบว่าหยุดนิ่งระหว่างนำทางประมาณ " +
                                    (riderUnexpectedStopDurationLabel(
                                        unexpectedStopLastMovementMs,
                                        navigationClockMs
                                    ) ?: "10 นาที") +
                                    " · QueueGo จะไม่โทรหรือส่งตำแหน่งให้อัตโนมัติ",
                                fontSize = 8.5.sp,
                                lineHeight = 12.sp,
                                color = Color(0xFF6F5B60),
                                modifier = Modifier.padding(top = 3.dp)
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 7.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        dismissedUnexpectedStopEpisode = unexpectedStopLastMovementMs
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("ฉันโอเค", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        dismissedUnexpectedStopEpisode = unexpectedStopLastMovementMs
                                        showUnexpectedStopSafety = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = QgRed)
                                ) {
                                    Text("ศูนย์ความปลอดภัย", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
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
                        !hasLocationPermission(context) -> {
                            pendingMapRecenter = true
                            ensureLocationPermission()
                        }
                        else -> {
                            routeOverviewActive = false
                            recenterSignal += 1
                            actionMessage = "กำลังหาตำแหน่ง"
                        }
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

        Card(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 116.dp, end = 10.dp)
                .size(44.dp)
                .clickable {
                    if (activeNavigationWork) {
                        routeOverviewActive = true
                        overviewSignal += 1
                    } else {
                        actionMessage = "ยังไม่มีเส้นทางให้ดูทั้งหมด"
                    }
                },
            shape = androidx.compose.foundation.shape.CircleShape,
            colors = CardDefaults.cardColors(containerColor = Color(0xF8FFFFFF)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE5E8)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                QgIcon("route", Modifier.size(23.dp), QgRed)
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 168.dp, end = 10.dp)
                .size(44.dp)
                .clickable { compassResetSignal += 1 },
            shape = androidx.compose.foundation.shape.CircleShape,
            colors = CardDefaults.cardColors(containerColor = Color(0xF8FFFFFF)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE5E8)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(23.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val north = Path().apply {
                        moveTo(cx, size.height * 0.08f)
                        lineTo(size.width * 0.68f, cy)
                        lineTo(cx, size.height * 0.43f)
                        lineTo(size.width * 0.32f, cy)
                        close()
                    }
                    val south = Path().apply {
                        moveTo(cx, size.height * 0.92f)
                        lineTo(size.width * 0.68f, cy)
                        lineTo(cx, size.height * 0.57f)
                        lineTo(size.width * 0.32f, cy)
                        close()
                    }
                    drawPath(north, color = QgRed)
                    drawPath(south, color = Color(0xFFFFA3B4))
                }
            }
        }

        val activeSheetJob = mapJob.takeIf { activeNavigationWork }
        LaunchedEffect(activeSheetJob?.id) {
            routeOverviewActive = false
        }
        val expandedSheetHeight = (maxHeight - 132.dp).coerceAtLeast(0.dp)
        val dockMaximumHeight = if (activeSheetJob != null) {
            // Expanded details leave the top map controls reachable; collapsed height
            // is owned by RiderJobDock and defaults to a small summary above navigation.
            (maxHeight * 0.62f).coerceAtMost((maxHeight - 200.dp).coerceAtLeast(92.dp))
        } else expandedSheetHeight
        val dockModifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 68.dp)
            .widthIn(max = 676.dp)
            .fillMaxWidth()
        val dockKey = when {
            activeSheetJob != null -> activeSheetJob.id
            snapshot?.offeredJob != null -> null
            else -> null
        }
        val dockTitle = activeSheetJob?.let { "งาน " + it.numberLabel }
            ?: if (snapshot?.online == true) "พร้อมรับงานแล้ว" else "พร้อมรับงานเมื่อคุณพร้อม"
        val dockDestination = activeSheetJob?.let {
            (if (it.isDelivering) it.deliveryAddress else it.pickupAddress)
                ?.takeIf { address -> address.isNotBlank() }
        } ?: "กดเพื่อย่อหรือเปิดการ์ด"

        RiderJobDock(
            jobKey = dockKey,
            title = dockTitle,
            destination = dockDestination,
            maximumHeight = dockMaximumHeight,
            initiallyExpanded = activeSheetJob == null && dockKey != null,
            mapOverviewActive = activeSheetJob != null && routeOverviewActive,
            onMapModeToggle = if (activeSheetJob != null) {
                {
                    if (routeOverviewActive) {
                        routeOverviewActive = false
                        if (ensureLocationPermission()) {
                            recenterSignal += 1
                        } else {
                            pendingMapRecenter = true
                        }
                    } else {
                        routeOverviewActive = true
                        overviewSignal += 1
                    }
                }
            } else null,
            modifier = dockModifier.onGloballyPositioned { coordinates ->
                navigationBottomInsetPx = coordinates.size.height + with(navigationDensity) { 68.dp.roundToPx() }
            }
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
            if (current == null) {
                if (loadError != null) {
                    Text(loadError!!, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { scope.launch { refreshSnapshot() } }, enabled = !actionBusy) {
                        Text("ลองเชื่อมต่ออีกครั้ง")
                    }
                }
            } else {
                if (current.activeJob == null && current.offeredJob == null && current.laundry.activeJob == null) {
                    RiderReadyCard(
                        online = current.online,
                        enabled = !actionBusy,
                        onCheckedChange = { changeOnline(it) }
                    )
                }
                Spacer(Modifier.height(12.dp))
                fun claimLaundry(laundryJob: RiderLaundryJob) {
                    if (actionBusy) return
                    actionBusy = true
                    scope.launch {
                        runCatching { api.claimLaundryJob(auth, laundryJob.jobId) }
                            .onSuccess {
                                actionMessage = "รับงานฝากซักแล้ว"
                                runCatching { api.riderSnapshot(auth) }
                                    .onFailure { if (it is CancellationException) throw it }
                                    .onSuccess { updated ->
                                        snapshot = updated
                                    }
                            }
                            .onFailure { actionMessage = it.message ?: "รับงานฝากซักไม่สำเร็จ" }
                        actionBusy = false
                    }
                }
                when {
                    current.deliveryReturn != null -> {
                        val returnCase = current.deliveryReturn
                        RiderDeliveryReturnCard(
                            state = returnCase,
                            photoUri = returnPhotoUri,
                            busy = actionBusy,
                            networkAvailable = networkAvailable,
                            onStartReturn = {
                                if (!requireServerNetwork()) {
                                    Unit
                                } else {
                                    actionBusy = true
                                    scope.launch {
                                        runCatching { api.startDeliveryReturn(auth, returnCase.caseId) }
                                            .onSuccess {
                                                val updated = runCatching { api.riderSnapshot(auth) }
                                                    .onFailure { if (it is CancellationException) throw it }
                                                    .getOrNull()
                                                if (updated != null) {
                                                    snapshot = updated
                                                    persistCommittedSnapshot(updated)
                                                }
                                                startQueueGoNavigation(
                                                    "เริ่มคืนสินค้า · นำทางกลับร้านด้วยแผนที่ QueueGo"
                                                )
                                            }
                                            .onFailure {
                                                playRiderEventFeedback(eventFeedbackView, RiderEventFeedbackKind.ACTION_ERROR)
                                                actionMessage = it.message ?: "เริ่มคืนสินค้าไม่สำเร็จ"
                                            }
                                        actionBusy = false
                                    }
                                }
                            },
                            onNavigate = {
                                startQueueGoNavigation("นำทางกลับร้านด้วยแผนที่ QueueGo")
                            },
                            onCaptureProof = { openReturnCamera() },
                            onCompleteReturn = {
                                val proof = returnPhotoUri
                                when {
                                    proof == null -> actionMessage = "กรุณาถ่ายรูปหลักฐานการคืนสินค้าก่อน"
                                    !requireServerNetwork() -> Unit
                                    else -> {
                                        actionBusy = true
                                        scope.launch {
                                            runCatching {
                                                api.completeDeliveryReturn(context, auth, returnCase.caseId, proof)
                                            }.onSuccess { result ->
                                                scope.launch {
                                                    try {
                                                        pendingProofIo {
                                                            pendingProofStore.clearConfirmedMatching(
                                                                userId = auth.user.id,
                                                                slot = RIDER_PROOF_SLOT_RETURN,
                                                                workId = returnCase.caseId,
                                                                mode = "deliveryReturn",
                                                                marketPickupId = null
                                                            )
                                                        }
                                                    } catch (failure: CancellationException) {
                                                        throw failure
                                                    } catch (_: Exception) {
                                                        // Server return already committed; local proof cleanup is recoverable.
                                                    }
                                                }
                                                returnPhotoUri = null
                                                returnPendingCameraUri = null
                                                val updated = runCatching { api.riderSnapshot(auth) }
                                                    .onFailure { if (it is CancellationException) throw it }
                                                    .getOrNull()
                                                if (updated != null) {
                                                    snapshot = updated
                                                    persistCommittedSnapshot(updated)
                                                }
                                                if (result == "returned") {
                                                    playRiderEventFeedback(eventFeedbackView, RiderEventFeedbackKind.RETURN_COMPLETE)
                                                    actionMessage = "คืนสินค้าครบทุกจุด · เงินคืน Rider บันทึกแล้ว"
                                                } else {
                                                    playRiderEventFeedback(eventFeedbackView, RiderEventFeedbackKind.RETURN_STOP_SUCCESS)
                                                    actionMessage = "คืนจุดนี้เรียบร้อย · ไปจุดคืนถัดไป"
                                                    updated?.deliveryReturn?.takeIf { it.canReturnToShop }?.let {
                                                        startQueueGoNavigation(
                                                            "คืนจุดนี้เรียบร้อย · นำทางไปจุดคืนถัดไปด้วยแผนที่ QueueGo"
                                                        )
                                                    }
                                                }
                                            }.onFailure {
                                                playRiderEventFeedback(eventFeedbackView, RiderEventFeedbackKind.ACTION_ERROR)
                                                actionMessage = it.message ?: "บันทึกการคืนสินค้าไม่สำเร็จ"
                                            }
                                            actionBusy = false
                                        }
                                    }
                                }
                            }
                        )
                    }
                    current.activeJob == null && current.laundry.activeJob != null -> {
                        val laundryJob = current.laundry.activeJob
                        RiderLaundryActiveCard(
                            job = laundryJob,
                            busy = actionBusy,
                            onNavigate = {
                                startQueueGoNavigation("นำทางงานฝากซักด้วยแผนที่ QueueGo")
                            },
                            onAction = { action ->
                                actionBusy = true
                                scope.launch {
                                    runCatching { api.laundryJobAction(auth, laundryJob.jobId, action) }
                                        .onSuccess {
                                            actionMessage = when (action) {
                                                "arrive" -> "บันทึกถึงจุดรับแล้ว"
                                                "collect" -> "รับผ้าแล้ว · กำลังนำทางต่อ"
                                                "deliver" -> "ส่งงานฝากซักถึงปลายทางแล้ว"
                                                else -> "อัปเดตงานฝากซักแล้ว"
                                            }
                                            runCatching { api.riderSnapshot(auth) }
                                                .onFailure { if (it is CancellationException) throw it }
                                                .onSuccess { snapshot = it }
                                        }
                                        .onFailure { actionMessage = it.message ?: "อัปเดตงานฝากซักไม่สำเร็จ" }
                                    actionBusy = false
                                }
                            }
                        )
                    }
                    current.activeJob != null -> {
                        val activeJob = current.activeJob
                        if (activeJob.marketOrderId != null && activeJob.status != "in_progress") {
                            MarketPickupPanel(
                                job = activeJob,
                                pickups = current.marketPickups,
                                busy = actionBusy,
                                onNavigate = { _ ->
                                    startQueueGoNavigation("นำทางไปจุดรับถัดไปด้วยแผนที่ QueueGo")
                                },
                                onVerify = { pickup ->
                                    openMarketPickupVerification(activeJob, pickup)
                                },
                                onStartDelivery = {
                                    if (actionBusy || !requireServerNetwork()) {
                                        Unit
                                    } else {
                                    actionBusy = true
                                    scope.launch {
                                        runCatching { api.startDelivery(auth, activeJob) }
                                            .onSuccess {
                                                actionMessage = "รับของครบแล้ว · เริ่มจัดส่ง"
                                                val committedJob = activeJob.copy(status = "in_progress")
                                                val committedSnapshot = current.copy(
                                                    activeJob = committedJob,
                                                    offeredJob = null
                                                )
                                                snapshot = committedSnapshot
                                                persistCommittedSnapshot(committedSnapshot)
                                                val updated = runCatching { api.riderSnapshot(auth) }
                                                    .onFailure { if (it is CancellationException) throw it }
                                                    .getOrNull()
                                                val committedRead = updated?.takeIf {
                                                    riderPickupReadCanReplace("in_progress", activeJob.id, it)
                                                }
                                                if (committedRead != null) {
                                                    snapshot = committedRead
                                                    persistCommittedSnapshot(committedRead)
                                                }
                                            }
                                            .onFailure {
                                                actionMessage = it.message ?: "เริ่มจัดส่งไม่สำเร็จ"
                                            }
                                        actionBusy = false
                                    }
                                    }
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        if (activeJob.isPassenger) {
                            RiderPassengerCard(
                                job = activeJob, busy = actionBusy, networkAvailable = networkAvailable,
                                onNavigate = {
                                    startQueueGoNavigation("นำทางเที่ยวผู้โดยสารด้วยแผนที่ QueueGo")
                                },
                                onChat = { chatJob = activeJob },
                                onAction = { action ->
                                    if (!actionBusy && requireServerNetwork()) {
                                        actionBusy = true
                                        scope.launch {
                                            try {
                                                val status = api.passengerAction(auth, activeJob, action)
                                                if (status == "searching_rider") {
                                                    val released = current.copy(activeJob = null, offeredJob = null)
                                                    snapshot = released
                                                    persistCommittedSnapshot(released)
                                                    actionMessage = "สิทธิ์หรือเครดิตรับเที่ยวไม่พร้อม · ระบบส่งเที่ยวกลับไปหาคนขับใหม่แล้ว"
                                                    runCatching { api.riderSnapshot(auth) }
                                                        .onFailure { if (it is CancellationException) throw it }
                                                        .onSuccess { snapshot = it; persistCommittedSnapshot(it) }
                                                } else {
                                                    val now = java.time.Instant.now().toString()
                                                    val committedJob = activeJob.copy(status = status,
                                                        arrivedShopAt = if (action == "arrive_pickup") now else activeJob.arrivedShopAt,
                                                        arrivedCustomerAt = if (action == "arrive_dropoff") now else activeJob.arrivedCustomerAt)
                                                    val committed = current.copy(activeJob = committedJob.takeUnless { status == "completed" }, offeredJob = null)
                                                    snapshot = committed
                                                    persistCommittedSnapshot(committed)
                                                    actionMessage = if (status == "completed") "จบเที่ยวแล้ว" else "อัปเดตเที่ยวแล้ว"
                                                    if (action == "start" && status == "in_progress") {
                                                        actionMessage = "เริ่มเที่ยวแล้ว · นำทางด้วยแผนที่ QueueGo"
                                                    }
                                                    runCatching { api.riderSnapshot(auth) }
                                                        .onFailure { if (it is CancellationException) throw it }
                                                        .onSuccess { snapshot = it; persistCommittedSnapshot(it) }
                                                }
                                            } catch (e: CancellationException) { throw e }
                                            catch (e: Exception) { actionMessage = e.message ?: "ยังยืนยันผลไม่ได้ กรุณาลองอีกครั้ง" }
                                            finally { actionBusy = false }
                                        }
                                    }
                                }
                            )
                        } else if (activeJob.isExpress) {
                            RiderExpressCard(
                                job = activeJob,
                                busy = actionBusy,
                                networkAvailable = networkAvailable,
                                onNavigate = { startQueueGoNavigation("นำทางงาน Express ด้วยแผนที่ QueueGo") },
                                onChat = { chatJob = activeJob },
                                onArrivePickup = { commitExpressAction(activeJob, "arrive_pickup", "ถึงจุดรับพัสดุแล้ว") },
                                onPickupProof = { openVerification("pickup", activeJob) },
                                onStartTransit = { commitExpressAction(activeJob, "start_transit", "เริ่มนำส่งพัสดุแล้ว") },
                                onArriveDropoff = { commitExpressAction(activeJob, "arrive_dropoff", "ถึงจุดส่งพัสดุแล้ว") },
                                onDeliveryProof = { openVerification("delivery", activeJob) }
                            )
                        } else ActiveJobCard(
                            job = if (
                                activeJob.marketOrderId != null &&
                                activeJob.status != "in_progress" &&
                                current.marketPickups.isNotEmpty() &&
                                current.marketPickups.all { it.done }
                            ) activeJob.copy(status = "picked_up") else activeJob,
                            details = homeDetails,
                            items = homeItems,
                            etaSeconds = riderActiveJobEtaSeconds(navigationGuidance),
                            loadSummary = activeLoadSummary,
                            busy = actionBusy,
                            networkAvailable = networkAvailable,
                            onDetails = { detailJob = current.activeJob },
                            onCall = {
                                if (current.activeJob.status in setOf("rider_assigned", "assigned", "preparing", "ready")) {
                                    val pickupId = current.marketPickups.firstOrNull { !it.done }?.pickupId
                                    if (current.activeJob.marketOrderId != null && pickupId != null) {
                                        scope.launch {
                                            runCatching { api.pickupIssueOrderId(auth, pickupId) }
                                                .onSuccess(::startShopVoice)
                                                .onFailure {
                                                    if (it is CancellationException) throw it
                                                    actionMessage = it.message ?: "ยังระบุร้านของจุดรับนี้ไม่ได้"
                                                }
                                        }
                                    } else {
                                        startShopVoice(current.activeJob.id)
                                    }
                                } else {
                                    startCustomerVoice(current.activeJob.id)
                                }
                            },
                            onChat = { chatJob = current.activeJob },
                            onQueueGoNavigate = {
                                startQueueGoNavigation()
                            },
                            onArriveShop = {
                                requestArrival(current.activeJob, "shop")
                            },
                            onPickupVerify = {
                                if (current.activeJob.marketOrderId != null) {
                                    current.marketPickups.firstOrNull { !it.done }?.let { pickup ->
                                        openMarketPickupVerification(current.activeJob, pickup)
                                    }
                                } else {
                                    openVerification("pickup", current.activeJob)
                                }
                            },
                            onStartDelivery = {
                                if (actionBusy || !requireServerNetwork()) {
                                    Unit
                                } else {
                                actionBusy = true
                                scope.launch {
                                    runCatching { api.startDelivery(auth, current.activeJob) }
                                        .onSuccess {
                                            actionMessage = "เริ่มจัดส่งแล้ว"
                                            val committedJob = current.activeJob.copy(status = "in_progress")
                                            val committedSnapshot = current.copy(
                                                activeJob = committedJob,
                                                offeredJob = null
                                            )
                                            snapshot = committedSnapshot
                                            persistCommittedSnapshot(committedSnapshot)
                                            val updated = runCatching { api.riderSnapshot(auth) }
                                                .onFailure { if (it is CancellationException) throw it }
                                                .getOrNull()
                                            val committedRead = updated?.takeIf {
                                                riderPickupReadCanReplace("in_progress", current.activeJob.id, it)
                                            }
                                            if (committedRead != null) {
                                                snapshot = committedRead
                                                persistCommittedSnapshot(committedRead)
                                            }
                                        }
                                        .onFailure {
                                            actionMessage = it.message ?: "เริ่มจัดส่งไม่สำเร็จ"
                                        }
                                    actionBusy = false
                                }
                                }
                            },
                            onArriveCustomer = {
                                requestArrival(current.activeJob, "customer")
                            },
                            onDeliveryVerify = {
                                openVerification("delivery", current.activeJob)
                            },
                            onDeliveryIssue = {
                                deliveryIssueJob = current.activeJob
                            },
                            onPickupIssue = {
                                pickupIssueJob = current.activeJob
                                pickupIssueStopId = current.marketPickups.firstOrNull { !it.done }?.pickupId
                            }
                        )
                        snapshot?.nextJob?.let { queued ->
                            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("งานถัดไป · " + queued.numberLabel, fontWeight = FontWeight.Bold)
                                    Text("ส่งงานปัจจุบันเสร็จแล้ว ไปรับที่ " + (queued.pickupAddress ?: "ร้านค้า"))
                                    Text("รายรับค่าส่ง " + riderMoneyBaht(queued.deliveryFee ?: 0.0))
                                }
                            }
                        }
                        RiderRouteBundlePanel(
                            state = current.bundle,
                            busy = actionBusy,
                            onClaim = { candidate ->
                                actionBusy = true
                                scope.launch {
                                    runCatching { api.claimRouteBundle(auth, candidate.orderId) }
                                        .onSuccess {
                                            actionMessage = "รับงานพ่วงแล้ว"
                                            runCatching { api.riderSnapshot(auth) }
                                                .onFailure { if (it is CancellationException) throw it }
                                                .onSuccess { snapshot = it }
                                        }
                                        .onFailure {
                                            actionMessage = it.message ?: "รับงานพ่วงไม่สำเร็จ"
                                        }
                                    actionBusy = false
                                }
                            }
                        )
                    }
                    current.offeredJob != null -> {
                        OfferCard(
                            job = current.offeredJob,
                            shop = offerShopDisplay,
                            loadSummary = offerLoadSummary,
                            riderLocation = lastKnownLocation(context),
                            busy = actionBusy,
                            onAccept = {
                                acceptTargetedOffer(current.offeredJob, automatic = false)
                            },
                            onDecline = {
                                actionBusy = true
                                scope.launch {
                                    runCatching { api.declineOffer(auth, current.offeredJob.id) }
                                        .onSuccess {
                                            actionMessage = "ส่งงานให้ Rider คนถัดไปแล้ว"
                                            runCatching { api.riderSnapshot(auth) }
                                            .onFailure { if (it is CancellationException) throw it }
                                            .onSuccess { snapshot = it }
                                        }
                                        .onFailure {
                                            actionMessage = it.message ?: "ปฏิเสธงานไม่สำเร็จ"
                                        }
                                    actionBusy = false
                                }
                            },
                            onExpired = {
                                // Clear only the expiring version: a newer offer may already have arrived.
                                if (snapshot?.offeredJob?.id == current.offeredJob.id &&
                                    snapshot?.offeredJob?.offerExpiresAt == current.offeredJob.offerExpiresAt) {
                                    snapshot = snapshot?.copy(offeredJob = null)
                                }
                                actionMessage = "ข้อเสนอนี้หมดเวลา ระบบกำลังส่งงานให้ Rider คนถัดไป"
                                scope.launch { refreshSnapshot() }
                            }
                        )
                        if (current.laundry.pool.isNotEmpty()) {
                            RiderLaundryPoolCard(
                                jobs = current.laundry.pool,
                                busy = actionBusy,
                                onClaim = ::claimLaundry
                            )
                        }
                    }
                    current.laundry.pool.isNotEmpty() -> {
                        RiderLaundryPoolCard(
                            jobs = current.laundry.pool,
                            busy = actionBusy,
                            onClaim = ::claimLaundry
                        )
                    }
                    else -> Unit
                }


            }
        }
        }

        RiderBottomNavigation(
            activeTab = activeTab,
            unreadMessages = unreadMessages.coerceAtMost(99),
            onSelect = { activeTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        if (showDestination) {
            RiderMyDestinationDialog(
                auth = auth,
                api = api,
                current = destinationPreference,
                currentGps = navigationFix?.point?.let { it.latitude to it.longitude },
                onSaved = { destinationPreference = it },
                onClose = { showDestination = false }
            )
        }
        if (showUnexpectedStopSafety) {
            Dialog(onDismissRequest = { showUnexpectedStopSafety = false }) {
                Surface(
                    modifier = Modifier.widthIn(max = 620.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White
                ) {
                    RiderSafetyScreen(
                        auth = auth,
                        api = api,
                        activeJob = mapJob,
                        onSupport = {
                            showUnexpectedStopSafety = false
                            activeTab = "chat"
                        },
                        onClose = { showUnexpectedStopSafety = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        detailJob?.let { job ->
            RiderOrderDetailsSheet(auth, job, api, onDismiss = { detailJob = null })
        }
        actionMessage?.let { RiderFeedbackToast(it, Modifier.align(Alignment.BottomCenter)) }
        if (connectivityNotice != null || messageNotice != null) {
            Column(Modifier.align(Alignment.TopCenter)) {
                connectivityNotice?.let { RiderConnectivityNotice(it) }
                messageNotice?.let { RiderMessageNotice(it, Modifier) }
            }
        }
        deliverySuccessJob?.let { completed ->
            val afterWorkRecommendation = riderAfterWorkRecommendation(
                online = snapshot?.online == true,
                stopAfterWorkPending = stopAfterWorkPending,
                missions = homeMissions,
                demandZones = demandZones,
                originLatitude = completed.deliveryLat,
                originLongitude = completed.deliveryLng
            )
            RiderDeliverySuccess(
                auth = auth,
                api = api,
                job = completed,
                recommendation = afterWorkRecommendation,
                onRecommendation = { item ->
                    when (item.target) {
                        RiderAfterWorkRecommendationTarget.HOME -> {
                            deliverySuccessJob = null
                            activeTab = "home"
                        }
                        RiderAfterWorkRecommendationTarget.EARNINGS -> {
                            deliverySuccessJob = null
                            activeTab = "earn"
                        }
                        RiderAfterWorkRecommendationTarget.DEMAND_NAVIGATION -> {
                            deliverySuccessJob = null
                            activeTab = "home"
                            actionMessage = "เปิดแผนที่ QueueGo เพื่อดูพื้นที่งานใกล้คุณ"
                        }
                    }
                },
                onDismiss = { deliverySuccessJob = null }
            )
        }
        pickupIssueJob?.let { issueJob ->
            RiderPickupIssueDialog(
                auth = auth, job = issueJob, pickupId = pickupIssueStopId,
                onDismiss = { pickupIssueJob = null },
                onCallShop = { startShopVoice(it.id) },
                onChat = { pickupIssueJob = null; pickupChatJob = it },
                onReported = { label ->
                    pickupIssueJob = null
                    actionMessage = "ส่งปัญหาจุดรับให้ฝ่ายดูแลแล้ว · $label"
                }
            )
        }
        deliveryIssueJob?.let { issueJob ->
            RiderDeliveryIssueDialog(
                auth = auth,
                job = issueJob,
                onDismiss = { deliveryIssueJob = null },
                onCallCustomer = { startCustomerVoice(issueJob.id) },
                onOpenChat = {
                    deliveryIssueJob = null
                    chatJob = issueJob
                },
                onReported = { label ->
                    deliveryIssueJob = null
                    actionMessage = "ส่งปัญหาให้ฝ่ายดูแลแล้ว · $label"
                }
            )
        }
        arrivalGuard?.let { guard ->
            RiderArrivalGuard(
                state = guard,
                busy = actionBusy,
                onProceed = { commitArrival(guard.job, guard.target) },
                onDismiss = { if (!actionBusy) arrivalGuard = null }
            )
        }
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
    shop: RiderShopDisplay?,
    loadSummary: RiderLoadSummary?,
    riderLocation: Pair<Double, Double>?,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onExpired: () -> Unit
) {
    val pickupKm = riderDistanceKm(riderLocation?.first, riderLocation?.second, job.pickupLat, job.pickupLng)
    val deliveryKm = riderDistanceKm(job.pickupLat, job.pickupLng, job.deliveryLat, job.deliveryLng)
    var secondsLeft by remember(job.id, job.offerExpiresAt) { mutableStateOf(riderOfferSecondsLeft(job.offerExpiresAt)) }

    LaunchedEffect(job.id, job.offerExpiresAt) {
        while (true) {
            secondsLeft = riderOfferSecondsLeft(job.offerExpiresAt)
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
                    Text(when { job.isPassenger -> "เที่ยวโดยสาร"; job.isExpress -> "Express ส่งด่วน"; else -> "งานใหม่ใกล้คุณ" }, color = QgRed, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                    Text(job.numberLabel, fontWeight = FontWeight.Black, fontSize = 24.sp)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(QgRedSoft)
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Text(
                        riderMoneyBaht(job.deliveryFee ?: 0.0),
                        color = QgRed,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            RiderOfferPlaceRow(
                logoUrl = if (job.isExpress) null else shop?.shopLogo,
                fallback = (if (job.isExpress) job.pickupAddress else shop?.shopName ?: job.pickupAddress ?: "Q")?.take(1),
                icon = if (job.isExpress) "express" else null,
                label = when { job.isPassenger -> "รับผู้โดยสาร"; job.isExpress -> "รับพัสดุ"; else -> "รับที่ร้าน" },
                value = if (job.isExpress) (job.pickupAddress ?: "จุดรับพัสดุ") else (shop?.shopName ?: job.pickupAddress ?: "ร้านค้า"),
                trailing = when { job.isPassenger -> "จุดรับ"; job.isExpress -> "จุดรับ"; else -> "รับสินค้า" }
            )
            RiderOfferPlaceRow(
                logoUrl = null,
                fallback = null,
                icon = "location",
                label = when { job.isPassenger -> "ไปยังจุดหมาย"; job.isExpress -> "ส่งพัสดุ"; else -> "ส่งให้ลูกค้า" },
                value = job.deliveryAddress ?: if (job.isExpress) "จุดส่งพัสดุ" else "จุดส่งสินค้า",
                trailing = if (job.isPassenger || job.isExpress) "จุดส่ง" else "ส่งสินค้า"
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RiderOfferMetric("ระยะทาง", (pickupKm?.let { "%.1f".format(it) } ?: "—") + " กม.", Modifier.weight(1f))
                RiderOfferMetric("เวลาประมาณ", ((when { job.isPassenger -> job.rideDurationMinutes; job.isExpress -> job.expressDurationMinutes; else -> deliveryKm?.let { kotlin.math.ceil(it / 25.0 * 60.0).coerceAtLeast(1.0) } }?.let { kotlin.math.ceil(it).toInt().toString() }) ?: "—") + " นาที", Modifier.weight(1f))
                RiderOfferMetric("รายได้", riderMoneyBaht(job.deliveryFee), Modifier.weight(1f), highlight = true)
            }

            if (!job.isExpress) RiderLoadSummaryBlock(loadSummary, compact = true)

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
                RiderPickupGuideBlock(
                    pickupInstructions = pickup.pickupInstructions,
                    parkingInstructions = pickup.parkingInstructions,
                    marketZone = pickup.marketZone,
                    marketStallNo = pickup.marketStallNo,
                    compact = true
                )
                Text("ยอดร้าน " + riderMoneyBaht(pickup.shopAmount))
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

internal fun riderActiveJobEtaSeconds(guidance: NavigationGuidanceState): Double? =
    if (guidance.weakGps || guidance.offRoute) null else guidance.etaSeconds

internal fun riderActiveJobEtaLabel(etaSeconds: Double?): String? {
    val seconds = etaSeconds?.takeIf { it.isFinite() && it >= 0.0 && it <= 14_400.0 } ?: return null
    val minutes = kotlin.math.ceil(seconds / 60.0).toInt().coerceAtLeast(1)
    return "ประมาณ " + minutes + " นาที"
}

internal fun riderActiveJobContactLabel(pickup: Boolean): String =
    if (pickup) "โทรร้าน" else "โทรลูกค้า"

@Composable
private fun ActiveJobCard(
    job: RiderJob,
    details: RiderVerificationDetails?,
    items: List<RiderItem>,
    etaSeconds: Double?,
    loadSummary: RiderLoadSummary?,
    busy: Boolean,
    networkAvailable: Boolean,
    onDetails: () -> Unit,
    onCall: () -> Unit,
    onChat: () -> Unit,
    onQueueGoNavigate: () -> Unit,
    onArriveShop: () -> Unit,
    onPickupVerify: () -> Unit,
    onStartDelivery: () -> Unit,
    onArriveCustomer: () -> Unit,
    onDeliveryVerify: () -> Unit,
    onDeliveryIssue: () -> Unit,
    onPickupIssue: () -> Unit
) {
    val pickup = job.status in setOf("rider_assigned", "assigned", "preparing", "ready")
    val step = if (pickup) 1 else 2
    val stepTitle = if (pickup) "ไปรับสินค้าที่ร้าน" else "ส่งสินค้าให้ลูกค้า"
    val title = when {
        job.status == "ready" -> "ร้านเตรียมสินค้าแล้ว"
        job.status == "preparing" -> "ร้านกำลังเตรียมสินค้า"
        pickup -> "กำลังไปรับสินค้า"
        job.status == "picked_up" -> "รับสินค้าแล้ว"
        job.customerArrived -> "ถึงลูกค้าแล้ว"
        else -> "กำลังไปส่ง"
    }
    val processLabel = when {
        job.marketOrderId != null && pickup -> "รับของตามจุด"
        job.status == "ready" -> "รายการสินค้า"
        job.status == "in_progress" -> "รายละเอียด"
        job.status == "picked_up" -> "เริ่มจัดส่ง"
        pickup -> "รายละเอียด"
        else -> "รายละเอียด"
    }
    val cashAmount = if (pickup) job.shopCash else job.customerCash
    val cashLabel = if (pickup) "ยอดที่ต้องชำระร้าน" else "ยอดที่ต้องเก็บลูกค้า"
    val primaryRequiresNetwork = when {
        pickup && !job.shopArrived -> true
        job.status == "picked_up" -> true
        job.status == "in_progress" && !job.customerArrived -> true
        else -> false
    }
    val primaryLabel = when {
        pickup && job.shopArrived -> if (job.status == "ready") "ตรวจสอบรายการ" else "ถึงร้านแล้ว ✓"
        pickup -> "ถึงร้านแล้ว"
        job.status == "picked_up" -> "เริ่มจัดส่ง"
        job.status == "in_progress" && job.customerArrived -> "ส่งมอบสินค้า"
        job.status == "in_progress" -> "ถึงลูกค้าแล้ว"
        else -> "ทำรายการต่อ"
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEFDFD)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE5E8))
    ) {
        Column(Modifier.padding(if (LocalConfiguration.current.screenHeightDp <= 760) 10.dp else 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.size(38.dp).background(QgRedSoft, androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(step.toString(), color = QgRed, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f)) {
                    Text("$step · $stepTitle", color = QgRed, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    Text(
                        listOfNotNull(riderActiveJobEtaLabel(etaSeconds), title).joinToString(" · "),
                        color = QgMuted,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(job.numberLabel, color = QgMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text(riderMoneyBaht(job.deliveryFee), color = QgRed, fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                if (pickup) {
                    QgRemoteImage(details?.shopLogo, Modifier.size(44.dp), fallback = details?.shopName?.take(1) ?: "Q", cornerRadius = 14.dp)
                } else {
                    Box(Modifier.size(44.dp).background(QgRedSoft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        QgIcon("user", Modifier.size(22.dp), QgRed)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(if (pickup) "ร้านค้า" else "ลูกค้า", color = QgMuted, fontSize = 8.sp)
                    Text(if (pickup) details?.shopName ?: "ร้านค้า" else details?.customerName ?: "ลูกค้า", fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Text(
                        (if (pickup) job.pickupAddress else job.deliveryAddress) ?: "พิกัดยังไม่พร้อม",
                        color = QgMuted, fontSize = 9.sp, maxLines = 2, modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            if (items.isNotEmpty()) {
                val itemCount = items.sumOf { it.quantity.coerceAtLeast(1) }
                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp)
                        .background(Color(0xFFF8F7F8), RoundedCornerShape(14.dp))
                        .padding(horizontal = 11.dp, vertical = 9.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("รายการสินค้า", color = QgMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text(itemCount.toString() + " รายการ", color = QgRed, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                    items.take(2).forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("×" + item.quantity, color = QgMuted, fontSize = 9.sp, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (items.size > 2) Text("+ อีก " + (items.size - 2) + " รายการ", color = QgMuted, fontSize = 8.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }

            cashAmount?.let {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).background(Color(0xFFFFF6F8), RoundedCornerShape(14.dp))
                        .padding(horizontal = 11.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(cashLabel, color = QgMuted, fontSize = 8.sp)
                        Text("เงินสด", color = Color(0xFF4C4548), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(riderMoneyBaht(it), color = QgRed, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
            }

            if (pickup && details != null) {
                RiderPickupGuideBlock(
                    pickupInstructions = details.pickupInstructions,
                    parkingInstructions = details.parkingInstructions,
                    marketZone = details.marketZone,
                    marketStallNo = details.marketStallNo
                )
            }
            if (pickup) RiderLoadSummaryBlock(loadSummary)

            details?.note?.let { note ->
                Column(Modifier.fillMaxWidth().padding(top = 6.dp).background(Color(0xFFFFF9EA), RoundedCornerShape(14.dp)).padding(10.dp)) {
                    Text("หมายเหตุจากลูกค้า", color = QgMuted, fontSize = 8.sp)
                    Text(note, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RiderJobTool("jobs", processLabel, !busy, Modifier.weight(1f), onDetails)
                RiderJobTool("phone", riderActiveJobContactLabel(pickup), !busy && networkAvailable, Modifier.weight(1f), onCall)
                RiderJobTool("support", "แชต", !busy && networkAvailable, Modifier.weight(1f), onChat)
            }

            if (pickup) {
                OutlinedButton(
                    onClick = onPickupIssue,
                    enabled = !busy && networkAvailable,
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp).heightIn(min = RIDER_SECONDARY_ACTION_MIN_HEIGHT_DP.dp)
                ) { Text("ปัญหาที่จุดรับ", fontSize = RIDER_SECONDARY_ACTION_TEXT_SP.sp) }
            }
            if (!pickup && job.status == "in_progress" && job.customerArrived) {
                OutlinedButton(
                    onClick = onDeliveryIssue,
                    enabled = !busy && networkAvailable,
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp).heightIn(min = RIDER_SECONDARY_ACTION_MIN_HEIGHT_DP.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("ลูกค้าติดต่อไม่ได้ / ส่งไม่สำเร็จ", fontSize = RIDER_SECONDARY_ACTION_TEXT_SP.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = {
                    when {
                        pickup && job.shopArrived && job.status == "ready" -> onPickupVerify()
                        pickup && !job.shopArrived -> onArriveShop()
                        job.status == "picked_up" -> onStartDelivery()
                        job.status == "in_progress" && job.customerArrived -> onDeliveryVerify()
                        job.status == "in_progress" -> onArriveCustomer()
                    }
                },
                enabled = !busy &&
                    !(pickup && job.shopArrived && job.status != "ready" && job.marketOrderId == null) &&
                    (!primaryRequiresNetwork || networkAvailable),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = RIDER_PRIMARY_ACTION_MIN_HEIGHT_DP.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF04455))
            ) {
                QgIcon("location", Modifier.size(22.dp), Color.White)
                Spacer(Modifier.width(9.dp))
                Text(primaryLabel, fontSize = RIDER_PRIMARY_ACTION_TEXT_SP.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

internal fun riderLoadSummaryLabel(summary: RiderLoadSummary): String? {
    val weight = summary.weightKg ?: return null
    val weightText = if (weight < 10.0) "%.1f".format(weight) else "%.0f".format(weight)
    val capacity = summary.vehicleCapacityKg
    val capacityText = capacity?.let { if (it < 10.0) "%.1f".format(it) else "%.0f".format(it) }
    return buildString {
        append("น้ำหนัก ").append(weightText).append(" กก.")
        if (capacityText != null) append(" · รถรับได้ ").append(capacityText).append(" กก.")
        summary.utilizationRatio?.takeIf { it.isFinite() && it >= 0.0 }?.let {
            append(" · ").append((it * 100.0).toInt().coerceAtLeast(0)).append("%")
        }
    }
}

internal fun riderLoadSummaryStateLabel(state: String): String? = when (state) {
    "near_capacity" -> "งานใหญ่ · ใกล้เต็มความจุรถ"
    "over_capacity" -> "เกินความจุรถที่เลือก"
    "capacity_unknown" -> "ยังไม่ทราบความจุรถ"
    else -> null
}

@Composable
private fun RiderLoadSummaryBlock(summary: RiderLoadSummary?, compact: Boolean = false) {
    val current = summary ?: return
    val label = riderLoadSummaryLabel(current) ?: return
    val warning = riderLoadSummaryStateLabel(current.state)
    val warningState = current.state in setOf("near_capacity", "over_capacity")
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = if (compact) 6.dp else 7.dp)
            .background(
                if (warningState) Color(0xFFFFF3E8) else Color(0xFFF4F8FF),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 9.dp, vertical = if (compact) 6.dp else 8.dp)
    ) {
        Text("ขนาดงาน", color = QgMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 3.dp))
        warning?.let {
            Text(
                it,
                color = if (current.state == "over_capacity") QgRed else Color(0xFF9A6200),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

internal fun riderPickupAreaLabel(marketZone: String?, marketStallNo: String?): String? =
    listOfNotNull(
        marketZone?.trim()?.takeIf { it.isNotEmpty() }?.let { "โซน $it" },
        marketStallNo?.trim()?.takeIf { it.isNotEmpty() }?.let { "แผง $it" }
    ).joinToString(" · ").takeIf { it.isNotEmpty() }

@Composable
private fun RiderPickupGuideBlock(
    pickupInstructions: String?,
    parkingInstructions: String?,
    marketZone: String?,
    marketStallNo: String?,
    compact: Boolean = false
) {
    val pickup = pickupInstructions?.trim()?.takeIf { it.isNotEmpty() }
    val parking = parkingInstructions?.trim()?.takeIf { it.isNotEmpty() }
    val area = riderPickupAreaLabel(marketZone, marketStallNo)
    if (pickup == null && parking == null && area == null) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = if (compact) 5.dp else 7.dp)
            .background(Color(0xFFFFF9EA), RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp, vertical = if (compact) 6.dp else 8.dp)
    ) {
        Text("คำแนะนำรับสินค้า", color = QgMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        area?.let { Text(it, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 3.dp)) }
        pickup?.let { Text("รับของ · $it", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp)) }
        parking?.let { Text("จอด / ทางเข้า · $it", color = Color(0xFF65584B), fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp)) }
    }
}

@Composable
private fun RiderJobTool(icon: String, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.heightIn(min = RIDER_SECONDARY_ACTION_MIN_HEIGHT_DP.dp).clip(RoundedCornerShape(15.dp))
        .background(Color.White).border(1.dp, Color(0xFFEDE5E8), RoundedCornerShape(15.dp))
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(
                when (icon) {
                    "support" -> R.drawable.qg_rider_nav_chat
                    "phone" -> R.drawable.qg_rider_flow_phone
                    else -> R.drawable.qg_rider_flow_jobs
                }
            ),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (enabled) Color(0xFF282427) else QgMuted
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (enabled) Color(0xFF282427) else QgMuted, fontSize = RIDER_TOOL_TEXT_SP.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun RiderManeuverBanner(
    hud: RiderNavigationHudInfo,
    customerNote: String? = null,
    modifier: Modifier = Modifier
) {
    Crossfade(
        targetState = hud,
        animationSpec = tween(durationMillis = 150),
        modifier = modifier
    ) { current ->
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFAFFFFFF)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8E2E4)),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(QgRed),
                        contentAlignment = Alignment.Center
                    ) {
                        RiderTurnManeuverIcon(
                            turnCode = current.maneuverTurnCode,
                            modifier = Modifier.size(36.dp),
                            color = Color.White
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                current.maneuverDistanceText ?: current.distanceText,
                                fontSize = 20.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF171416)
                            )
                            current.etaText?.let { eta ->
                                Text(
                                    eta,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = QgMuted
                                )
                            }
                        }
                        Text(
                            current.maneuverRoadName ?: current.maneuverText ?: "ไปตามเส้นทาง",
                            modifier = Modifier.padding(top = 2.dp),
                            fontSize = 14.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF272225),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        current.maneuverText?.let { maneuver ->
                            if (current.maneuverRoadName != null) {
                                Text(
                                    maneuver,
                                    modifier = Modifier.padding(top = 1.dp),
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = QgMuted
                                )
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            current.distanceText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF4E464A)
                        )
                        Text(
                            "เหลือถึงจุดหมาย",
                            fontSize = 7.sp,
                            color = QgMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                current.followingInstructionText?.let { following ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 7.dp)
                            .background(Color(0xFFFFF1F4), RoundedCornerShape(12.dp))
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        RiderTurnManeuverIcon(
                            turnCode = current.followingTurnCode,
                            modifier = Modifier.size(19.dp),
                            color = QgRed
                        )
                        Text(
                            "ถัดไป",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = QgRed
                        )
                        Text(
                            following,
                            modifier = Modifier.weight(1f),
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF51494D),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                customerNote?.let { note ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 7.dp)
                            .background(Color(0xFFFFF7E2), RoundedCornerShape(12.dp))
                            .padding(horizontal = 9.dp, vertical = 7.dp)
                    ) {
                        Text(
                            "หมายเหตุจากลูกค้า",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF8A5A00)
                        )
                        Text(
                            note,
                            modifier = Modifier.padding(top = 2.dp),
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4D4030),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RiderTurnManeuverIcon(
    turnCode: Int?,
    modifier: Modifier = Modifier,
    color: Color
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (size.minDimension * 0.12f).coerceAtLeast(3f)
        val cx = w * 0.5f
        val bottom = h * 0.86f
        val top = h * 0.18f
        val left = w * 0.18f
        val right = w * 0.82f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(color = color, start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = stroke)
        }
        fun arrowUp(x: Float, y: Float) {
            line(x, y, x - w * 0.16f, y + h * 0.18f)
            line(x, y, x + w * 0.16f, y + h * 0.18f)
        }
        when (turnCode) {
            0 -> {
                line(cx, bottom, cx, h * 0.46f); line(cx, h * 0.46f, left, h * 0.46f)
                line(left, h * 0.46f, left + w * 0.17f, h * 0.29f)
                line(left, h * 0.46f, left + w * 0.17f, h * 0.63f)
            }
            1 -> {
                line(cx, bottom, cx, h * 0.46f); line(cx, h * 0.46f, right, h * 0.46f)
                line(right, h * 0.46f, right - w * 0.17f, h * 0.29f)
                line(right, h * 0.46f, right - w * 0.17f, h * 0.63f)
            }
            2 -> {
                line(cx, bottom, cx, h * 0.55f); line(cx, h * 0.55f, left, top)
                line(left, top, left + w * 0.05f, top + h * 0.23f)
                line(left, top, left + w * 0.22f, top + h * 0.05f)
            }
            3 -> {
                line(cx, bottom, cx, h * 0.55f); line(cx, h * 0.55f, right, top)
                line(right, top, right - w * 0.05f, top + h * 0.23f)
                line(right, top, right - w * 0.22f, top + h * 0.05f)
            }
            4 -> {
                line(cx, bottom, cx, h * 0.42f)
                line(cx, h * 0.42f, left + w * 0.16f, h * 0.42f)
                line(left + w * 0.16f, h * 0.42f, left + w * 0.16f, h * 0.68f)
                line(left + w * 0.16f, h * 0.68f, left, h * 0.56f)
                line(left + w * 0.16f, h * 0.68f, left + w * 0.32f, h * 0.56f)
            }
            9 -> {
                drawCircle(color = color, radius = size.minDimension * 0.16f, center = Offset(cx, h * 0.42f))
                line(cx, h * 0.58f, cx, bottom)
            }
            else -> {
                line(cx, bottom, cx, top)
                arrowUp(cx, top)
            }
        }
    }
}

@Composable
private fun RiderSpeedLimitSign(speedLimitKph: Int) {
    val shape = androidx.compose.foundation.shape.CircleShape
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(Color.White, shape)
            .border(5.dp, QgRed, shape)
            .semantics {
                contentDescription = "จำกัดความเร็ว $speedLimitKph กิโลเมตรต่อชั่วโมง"
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            speedLimitKph.coerceIn(1, 160).toString(),
            color = Color(0xFF161214),
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun RiderMapWordmark(modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Queue",
            color = Color(0xFF17171B).copy(alpha = 0.46f),
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp
        )
        Text(
            "Go",
            color = QgRed.copy(alpha = 0.46f),
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp
        )
    }
}

@Composable
private fun RiderBlueprintSpeedPill(speedKph: Int?) {
    Card(
        modifier = Modifier.size(52.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFAFFFFFF)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE7E9))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 3.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                speedKph?.coerceIn(0, 199)?.toString() ?: "—",
                color = Color(0xFF211E20),
                fontSize = 20.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "กม./ชม.",
                color = Color(0xFF8D8589),
                fontSize = 7.sp,
                lineHeight = 7.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun RiderReadyCard(online: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFAFFFFFF)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE5E8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        if (online) Color(0xFF12A651) else Color(0xFFA2A2A7),
                        androidx.compose.foundation.shape.CircleShape
                    )
            )
            Column(Modifier.weight(1f)) {
                Text(
                    if (online) "พร้อมรับงานแล้ว" else "ยังไม่พร้อมรับงาน",
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (online) "คุณจะได้รับงานในพื้นที่ใกล้เคียง" else "เปิดเพื่อเริ่มรับงาน",
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                    color = QgMuted,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            androidx.compose.material3.Switch(
                checked = online,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "พร้อมรับงาน" },
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedTrackColor = QgRed,
                    checkedThumbColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun RiderOfferPlaceRow(
    logoUrl: String?,
    fallback: String?,
    icon: String?,
    label: String,
    value: String,
    trailing: String
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon == null) {
            QgRemoteImage(
                logoUrl,
                Modifier.size(42.dp),
                fallback = fallback ?: "Q",
                cornerRadius = 14.dp
            )
        } else {
            Box(
                Modifier.size(42.dp).background(QgRedSoft, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                QgIcon(icon, Modifier.size(22.dp), QgRed)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = QgMuted, fontSize = 8.sp)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2)
        }
        Spacer(Modifier.width(8.dp))
        Text(trailing, color = QgMuted, fontSize = 9.sp, maxLines = 1)
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
        Text(title, color = Color(0xFF3C383A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(riderMoneyBaht(amount), color = QgRed, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Text(note, color = QgMuted, fontSize = 11.sp)
    }
}

@Composable
private fun RiderCustomerCashPanel(job: RiderJob) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(QgRedSoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Text("เก็บจากลูกค้า", color = Color(0xFF3C383A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        job.customerSubtotal?.let { RiderCashBreakdownRow("ยอดสินค้า", it) }
        job.customerDeliveryFee?.let { RiderCashBreakdownRow("ค่าส่ง", it) }
        job.customerServiceFee?.takeIf { it > 0.0 }?.let {
            RiderCashBreakdownRow("ค่าบริการหลายร้าน", it)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp)
                .height(1.dp)
                .background(Color(0x22E6002D))
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("ยอดที่ต้องเก็บทั้งหมด", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(riderMoneyBaht(job.customerCash), color = QgRed, fontSize = 34.sp, fontWeight = FontWeight.Black)
        }
        Text("เก็บเงินตามยอดนี้จากลูกค้า", color = QgMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RiderCashBreakdownRow(label: String, amount: Double) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = QgMuted, fontSize = 10.sp)
        Text(riderMoneyBaht(amount), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    val expressMode = mode == "expressPickup" || mode == "expressDelivery"
    val pickup = mode == "pickup" || mode == "expressPickup"
    val marketPickupMode = mode == "marketPickup"
    var previewQuality by remember(photoUri) { mutableStateOf<RiderEvidenceQuality?>(null) }
    var showPickupConfirmation by remember(mode, job.id, photoUri) { mutableStateOf(false) }
    val previewReady = previewQuality?.acceptable == true
    val photoReady = photoUri != null
    val deliveryReady = if (expressMode) {
        photoReady && previewReady
    } else if (!pickup && !marketPickupMode) {
        riderCollectionReady(job.customerCash, photoReady, previewReady)
    } else {
        photoReady && previewReady
    }
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
                    Text(when { expressMode && pickup -> "รับพัสดุที่จุดรับ"; expressMode -> "ส่งพัสดุที่จุดส่ง"; pickup || marketPickupMode -> "รับสินค้าที่ร้าน"; else -> "ส่งสินค้าให้ลูกค้า" }, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(job.numberLabel, fontSize = 8.sp, color = Color(0xFF8A8387))
                }
                Spacer(Modifier.size(44.dp))
            }
            Column(Modifier.weight(1f).widthIn(max = 680.dp).fillMaxWidth().align(Alignment.CenterHorizontally).verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp).padding(top = if (shortScreen) 7.dp else 9.dp, bottom = 92.dp)) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(horizontal = 11.dp, vertical = if (shortScreen) 9.dp else 12.dp)) {
                        Text(when { expressMode && pickup -> "รับพัสดุที่จุดรับ"; expressMode -> "ส่งพัสดุที่จุดส่ง"; pickup || marketPickupMode -> "รับสินค้าที่ร้าน"; else -> "ส่งสินค้าให้ลูกค้า" }, fontSize = 9.sp, color = QgMuted)
                        Text(when { expressMode && pickup -> "ตรวจพัสดุและถ่ายรูป"; expressMode -> "ถ่ายรูปก่อนส่งมอบ"; marketPickupMode -> "ตรวจจุดรับและถ่ายรูป"; pickup -> "ตรวจรายการและถ่ายใบเสร็จ"; else -> "ตรวจสอบและส่งมอบสินค้า" },
                            fontSize = if (shortScreen) 18.sp else 20.sp, fontWeight = FontWeight.Bold)
                        Text(if (expressMode) "ใช้รูปหลักฐานหนึ่งรูปต่อจุด ห้ามข้ามขั้นตอน" else if (pickup) "เช็กรายการและยอดรวมให้ตรง แล้วถ่ายใบเสร็จให้ชัดก่อนรับอาหาร" else if (marketPickupMode) "เช็กชื่อสินค้าและจำนวนให้ครบ แล้วถ่ายรูปหลักฐานในหน้าเดียว" else "ตรวจรายการและถ่ายรูปหลักฐานในหน้าเดียว", fontSize = 9.sp, color = QgMuted)
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
                        RiderProofMetaRow(R.drawable.qg_rider_flow_loc, "ที่อยู่ร้าน", job.pickupAddress ?: "จุดรับสินค้า")
                        if (!details.pickupInstructions.isNullOrBlank()) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0EAEC)))
                            RiderProofMetaRow(R.drawable.qg_rider_flow_jobs, "จุดรับสินค้า", details.pickupInstructions)
                        }
                        if (!details.parkingInstructions.isNullOrBlank()) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0EAEC)))
                            RiderProofMetaRow(R.drawable.qg_rider_flow_loc, "จุดจอด / ทางเข้า", details.parkingInstructions)
                        }
                        riderPickupAreaLabel(details.marketZone, details.marketStallNo)?.let { area ->
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0EAEC)))
                            RiderProofMetaRow(R.drawable.qg_rider_flow_loc, "โซน / แผง", area)
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0EAEC)))
                        RiderProofMetaRow(R.drawable.qg_rider_nav_user, "ลูกค้า", details.customerName ?: "ลูกค้า")
                    }
                }
                if (expressMode) {
                    RiderProofCard(shortScreen) {
                        RiderProofMetaRow(R.drawable.qg_rider_flow_jobs, "พัสดุ", (job.expressItemType ?: "พัสดุ") + " · " + riderMoneyNumber(job.expressWeightKg) + " กก. · " + (job.expressSize ?: "ไม่ระบุขนาด"))
                        RiderProofMetaRow(R.drawable.qg_rider_nav_user, "ผู้ส่ง", job.expressSenderName ?: "ผู้ส่ง")
                        RiderProofMetaRow(R.drawable.qg_rider_flow_loc, if (pickup) "จุดรับ" else "จุดส่ง", if (pickup) (job.pickupAddress ?: "จุดรับพัสดุ") else (job.deliveryAddress ?: "จุดส่งพัสดุ"))
                        if (!pickup) RiderProofMetaRow(R.drawable.qg_rider_nav_user, "ผู้รับ", job.expressReceiverName ?: "ผู้รับ")
                        if (!job.expressInstructions.isNullOrBlank()) RiderProofMetaRow(R.drawable.qg_rider_flow_jobs, "หมายเหตุ", job.expressInstructions)
                    }
                }
                if (!pickup && !marketPickupMode && !expressMode) {
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
                            RiderPickupGuideBlock(
                                pickupInstructions = marketPickup.pickupInstructions,
                                parkingInstructions = marketPickup.parkingInstructions,
                                marketZone = marketPickup.marketZone,
                                marketStallNo = marketPickup.marketStallNo
                            )
                            Text("ยอดร้าน " + riderMoneyBaht(marketPickup.shopAmount))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                if (!marketPickupMode && !expressMode) Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
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
                                    Text(riderMoneyBaht(item.unitPrice) + " × ${item.quantity}", fontSize = 8.sp, color = QgMuted)
                                }
                                Text(riderMoneyBaht(item.totalPrice), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            if (index < items.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0EAEC)))
                        }
                        if (pickup && !expressMode && job.shopCash != null) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEE6E8)))
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("รวมค่าสินค้า", fontSize = 10.sp)
                                Text(riderMoneyBaht(job.shopCash), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = QgRed)
                            }
                        }
                    }
                }

                if (pickup && !expressMode) details?.note?.let { RiderProofNote(it, shortScreen) }
                if (pickup && job.shopCash != null) {
                    Spacer(Modifier.height(6.dp))
                    RiderCashPanel(
                        title = "จ่ายร้าน",
                        amount = job.shopCash,
                        note = "จ่ายร้านตามยอดนี้ก่อนรับสินค้า"
                    )
                }
                if (!pickup && !marketPickupMode && !expressMode && (job.customerCash == null || !job.customerCash.isFinite() || job.customerCash < 0.0)) {
                    Text("ยังตรวจสอบยอดเงินจากระบบไม่ได้ กรุณากลับและเปิดรายการใหม่", color = QgRed,
                        modifier = Modifier.padding(vertical = 8.dp), fontSize = 12.sp)
                }
                if (!pickup && !marketPickupMode && !expressMode && job.customerCash != null) {
                    Spacer(Modifier.height(6.dp))
                    RiderCustomerCashPanel(job)
                }
                if (expressMode && !pickup) {
                    Spacer(Modifier.height(6.dp))
                    RiderCashPanel(
                        title = "ค่าส่งเงินสด",
                        amount = job.customerCash ?: job.deliveryFee ?: 0.0,
                        note = "เก็บค่าส่งตามยอดก่อนกดจบงาน"
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (photoUri == null) {
                    RiderProofCard(shortScreen) {
                        Text("แนวทางรูปหลักฐาน", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(
                            if (expressMode && pickup) {
                                "ให้เห็นพัสดุที่รับและจุดรับในเฟรมเดียว ภาพต้องชัด"
                            } else if (expressMode) {
                                "ให้เห็นพัสดุและจุดส่งในเฟรมเดียว ภาพต้องชัด"
                            } else if (pickup) {
                                "ถ่ายใบเสร็จให้เห็นรายการสินค้า จำนวน และยอดรวมครบในเฟรมเดียว ภาพต้องชัด อ่านยอดได้"
                            } else if (marketPickupMode) {
                                "ให้เห็นสินค้าทั้งหมดในเฟรมเดียว ภาพต้องชัด และอย่าบังสินค้า"
                            } else {
                                "ให้เห็นสินค้าและจุดส่งในเฟรมเดียว ภาพต้องชัด และไม่จำเป็นต้องถ่ายหน้าลูกค้า"
                            },
                            color = QgMuted,
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
                if (photoUri != null) {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(when { expressMode && pickup -> "รูปตอนรับพัสดุ"; expressMode -> "รูปตอนส่งพัสดุ"; pickup -> "รูปใบเสร็จ"; marketPickupMode -> "รูปตอนรับสินค้า"; else -> "รูปตอนส่งสินค้า" }, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("จำเป็น", fontSize = 8.sp, color = QgMuted)
                            }
                            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(if (shortScreen) 108.dp else (screenHeight * .31f).coerceIn(160f, 215f).dp)
                                .clip(RoundedCornerShape(15.dp))) {
                                RiderEvidencePreview(photoUri, Modifier.fillMaxSize()) { previewQuality = it }
                                Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)
                                    .heightIn(min = 48.dp).clip(RoundedCornerShape(99.dp)).background(Color(0xF5FFFFFF))
                                    .clickable(enabled = !busy, onClick = onCamera).padding(horizontal = 13.dp), contentAlignment = Alignment.Center) {
                                    Text("ถ่ายใหม่", fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            previewQuality?.let { quality ->
                                Text(
                                    quality.message ?: "รูปพร้อมใช้เป็นหลักฐาน",
                                    color = if (quality.acceptable) Color(0xFF287A4B) else QgRed,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
                if (message != null) Text(message, color = QgRed, modifier = Modifier.padding(top = 8.dp))
                Text(
                    if (expressMode && pickup) "ถ่ายรูปพัสดุก่อนยืนยันรับ"
                    else if (expressMode) "ถ่ายรูปที่จุดส่งและเก็บค่าส่งก่อนจบงาน"
                    else if (pickup) "ต้องมีรูปใบเสร็จที่บันทึกแล้วก่อนยืนยันรับอาหาร" else if (marketPickupMode) "ตรวจรายการให้ครบและถ่ายรูปก่อนรับสินค้า"
                    else "ถ่ายรูปหลักฐาน ตรวจยอดเงิน แล้วกดจบงานครั้งเดียว",
                    fontSize = 8.sp,
                    color = QgMuted,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 12.dp).widthIn(max = 660.dp).fillMaxWidth()
            .background(Color(0xFAFFFFFF), RoundedCornerShape(18.dp))) {
            if (photoReady && !pickup && !marketPickupMode) {
                RiderSwipeCompleteAction(
                    enabled = !busy && deliveryReady,
                    busy = busy,
                    label = if (expressMode) "สไลด์เพื่อยืนยันรับเงินและจบงาน" else "สไลด์เพื่อยืนยันรับเงินและจบงาน",
                    onComplete = onConfirm
                )
            } else {
                Button(
                    onClick = when {
                        !photoReady -> onCamera
                        pickup && !expressMode && !marketPickupMode -> ({ showPickupConfirmation = true })
                        else -> onConfirm
                    },
                    enabled = !busy && if (photoReady) deliveryReady else true,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (pickup && !expressMode && !marketPickupMode && photoReady) Color(0xFF159947) else Color(0xFFF04455),
                        disabledContainerColor = Color(0xFFDAD4D6)
                    )
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                    else Text(
                        when {
                            !photoReady && expressMode && pickup -> "ถ่ายรูปพัสดุที่รับ"
                            !photoReady && expressMode -> "ถ่ายรูปพัสดุที่ส่ง"
                            !photoReady && marketPickupMode -> "ถ่ายรูปสินค้าจุดนี้"
                            !photoReady && pickup -> "ถ่ายรูปใบเสร็จ"
                            !photoReady -> "ถ่ายรูปตอนส่ง"
                            expressMode && pickup -> "ยืนยันรับพัสดุ"
                            marketPickupMode -> "ยืนยันรับสินค้าจุดนี้"
                            pickup -> "ร้านเตรียมเสร็จแล้ว"
                            else -> "ยืนยัน"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (showPickupConfirmation) {
            Dialog(
                onDismissRequest = { if (!busy) showPickupConfirmation = false },
                properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 430.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "ได้รับอาหารแล้วใช่ไหม?",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF211E20)
                        )
                        Text(
                            "ตรวจว่ารายการครบและเก็บรูปใบเสร็จไว้แล้ว ก่อนเริ่มนำส่งให้ลูกค้า",
                            modifier = Modifier.padding(top = 6.dp),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = QgMuted
                        )
                        Button(
                            onClick = {
                                showPickupConfirmation = false
                                onConfirm()
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 14.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF159947))
                        ) {
                            Text("ใช่ ได้รับแล้ว", fontWeight = FontWeight.Black)
                        }
                        OutlinedButton(
                            onClick = { showPickupConfirmation = false },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 8.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("ไม่ กำลังรออยู่", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RiderSwipeCompleteAction(
    enabled: Boolean,
    busy: Boolean,
    label: String,
    onComplete: () -> Unit
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(58.dp)
            .background(if (enabled) Color(0xFFFFEEF1) else Color(0xFFF3EFF0), RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFFF2CDD5), RoundedCornerShape(20.dp))
    ) {
        val density = LocalDensity.current.density
        val maxTravelDp = (maxWidth - 54.dp).coerceAtLeast(1.dp)
        var dragDp by remember(enabled) { mutableStateOf(0f) }
        val scope = rememberCoroutineScope()
        val hapticView = LocalView.current
        val completeThreshold = (maxTravelDp.value - 0.5f).coerceAtLeast(0f)
        fun springBack() {
            val start = dragDp
            scope.launch {
                animate(
                    initialValue = start,
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 200)
                ) { value, _ -> dragDp = value }
            }
        }
        Text(
            if (busy) "กำลังยืนยัน..." else label,
            modifier = Modifier.align(Alignment.Center),
            color = if (enabled) Color(0xFF7C4B55) else QgMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black
        )
        Box(
            Modifier.offset(x = dragDp.dp).size(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (enabled) Color(0xFFF04455) else Color(0xFFD6CDD0))
                .pointerInput(enabled, busy, maxTravelDp) {
                    if (!enabled || busy) return@pointerInput
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            dragDp = (dragDp + amount / density).coerceIn(0f, maxTravelDp.value)
                        },
                        onDragEnd = {
                            if (dragDp >= completeThreshold) {
                                dragDp = maxTravelDp.value
                                hapticView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                onComplete()
                            } else {
                                springBack()
                            }
                        },
                        onDragCancel = { springBack() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text("››", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
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
    val dir = File(context.filesDir, "queuego-evidence").apply { mkdirs() }
    val file = File.createTempFile("qg-proof-", ".jpg", dir)
    return FileProvider.getUriForFile(
        context,
        context.packageName + ".fileprovider",
        file
    )
}


