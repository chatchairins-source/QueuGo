package com.queuego.shared

import android.content.Context
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

enum class NativeVoicePhase {
    IDLE, RINGING_OUT, INCOMING, CONNECTING, CONNECTED, ENDED, ERROR
}

data class NativeVoiceControllerState(
    val phase: NativeVoicePhase = NativeVoicePhase.IDLE,
    val call: NativeVoiceCall? = null,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val error: String? = null
)

/**
 * Coordinates authenticated call RPCs, private Realtime signaling and audio-only WebRTC.
 * UI owns RECORD_AUDIO permission; this controller never asks for permissions itself.
 */
class NativeVoiceCallController(
    context: Context,
    private val api: NativeVoiceCallApi = NativeVoiceCallApi(),
    private val realtime: NativeVoiceRealtime = NativeVoiceRealtime()
) : Closeable {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(NativeVoiceControllerState())
    private var auth: NativeAuth? = null
    private var realtimeConnection: NativeVoiceRealtimeConnection? = null
    private var peer: NativeVoicePeer? = null
    private var operationJob: Job? = null
    private var signalJob: Job? = null
    private var authorizationJob: Job? = null
    private val offerSent = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    val state: StateFlow<NativeVoiceControllerState> = _state

    fun updateAuth(next: NativeAuth) {
        val previous = auth
        auth = next
        if (previous != null && previous.session.sessionId != next.session.sessionId) {
            finishLocal(NativeVoicePhase.ENDED, null)
            return
        }
        if (previous?.session?.accessToken != next.session.accessToken) {
            realtimeConnection?.updateAccessToken(next.session.accessToken)
        }
    }

    fun startOutgoing(nextAuth: NativeAuth, orderId: String, target: String) {
        checkOpen()
        require(target in setOf("shop", "rider", "customer"))
        cancelOperationOnly()
        updateAuth(nextAuth)
        operationJob = scope.launch {
            try {
                val call = api.start(nextAuth, orderId, target)
                check(call.callerUserId == nextAuth.user.id) { "สิทธิ์ผู้โทรไม่ตรงกับ Session" }
                setState(NativeVoiceControllerState(
                    phase = NativeVoicePhase.RINGING_OUT,
                    call = call
                ))
                val accepted: NativeVoiceCall? = withTimeoutOrNull<NativeVoiceCall?>(50_000L) {
                    while (true) {
                        delay(900L)
                        val currentAuth = auth ?: throw IllegalStateException("Session สิ้นสุดแล้ว")
                        val active = api.active(currentAuth, orderId)
                        if (active == null) return@withTimeoutOrNull null
                        if (active.id != call.id) throw IllegalStateException("พบสายอื่นในออเดอร์เดียวกัน")
                        if (active.status == "accepted") return@withTimeoutOrNull active
                    }
                }
                if (accepted == null) {
                    runCatching { auth?.let { api.end(it, call.id) } }
                    finishLocal(NativeVoicePhase.ENDED, "ไม่มีผู้รับสาย")
                    return@launch
                }
                connect(accepted, caller = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                fail(failure)
            }
        }
    }

    suspend fun refreshIncoming(nextAuth: NativeAuth): NativeVoiceCall? {
        checkOpen()
        updateAuth(nextAuth)
        if (_state.value.phase in setOf(
                NativeVoicePhase.RINGING_OUT,
                NativeVoicePhase.CONNECTING,
                NativeVoicePhase.CONNECTED
            )
        ) return _state.value.call
        val incoming = api.incoming(nextAuth)
        if (incoming == null) {
            if (_state.value.phase == NativeVoicePhase.INCOMING) {
                finishLocal(NativeVoicePhase.IDLE, null)
            }
            return null
        }
        check(incoming.calleeUserId == nextAuth.user.id) {
            "สิทธิ์ผู้รับสายไม่ตรงกับ Session"
        }
        setState(
            NativeVoiceControllerState(
                phase = NativeVoicePhase.INCOMING,
                call = incoming
            )
        )
        return incoming
    }

    suspend fun refreshActive(nextAuth: NativeAuth, orderId: String): NativeVoiceCall? {
        checkOpen()
        updateAuth(nextAuth)
        val active = api.active(nextAuth, orderId) ?: return null
        if (active.calleeUserId == nextAuth.user.id && active.status == "ringing") {
            setState(NativeVoiceControllerState(
                phase = NativeVoicePhase.INCOMING,
                call = active
            ))
        } else if (active.status == "accepted" && peer == null) {
            connect(active, caller = active.callerUserId == nextAuth.user.id)
        }
        return active
    }

    fun answerIncoming(nextAuth: NativeAuth) {
        checkOpen()
        val current = _state.value.call ?: return
        if (_state.value.phase != NativeVoicePhase.INCOMING) return
        cancelOperationOnly()
        updateAuth(nextAuth)
        operationJob = scope.launch {
            try {
                check(current.calleeUserId == nextAuth.user.id) { "สิทธิ์ผู้รับสายไม่ตรงกับ Session" }
                val accepted = api.answer(nextAuth, current.id)
                connect(accepted, caller = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                fail(failure)
            }
        }
    }

    fun declineIncoming(nextAuth: NativeAuth) {
        val current = _state.value.call ?: return
        updateAuth(nextAuth)
        scope.launch {
            runCatching { api.decline(nextAuth, current.id) }
            finishLocal(NativeVoicePhase.ENDED, null)
        }
    }

    fun hangUp() {
        val current = _state.value.call
        val currentAuth = auth
        realtimeConnection?.send(
            "hangup",
            JSONObject().apply { current?.let { put("call_id", it.id) } }
        )
        if (current != null && currentAuth != null) {
            scope.launch { runCatching { api.end(currentAuth, current.id) } }
        }
        finishLocal(NativeVoicePhase.ENDED, null)
    }

    fun setMuted(muted: Boolean) {
        peer?.setMuted(muted)
        _state.value = _state.value.copy(muted = muted)
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        peer?.setSpeakerEnabled(enabled)
        _state.value = _state.value.copy(speaker = enabled)
    }

    private suspend fun connect(call: NativeVoiceCall, caller: Boolean) {
        val currentAuth = auth ?: throw IllegalStateException("Session สิ้นสุดแล้ว")
        check(
            (caller && call.callerUserId == currentAuth.user.id) ||
                (!caller && call.calleeUserId == currentAuth.user.id)
        ) { "สิทธิ์สายโทรไม่ตรงกับ Session" }

        closeTransport()
        offerSent.set(false)
        setState(NativeVoiceControllerState(
            phase = NativeVoicePhase.CONNECTING,
            call = call,
            muted = false,
            speaker = false
        ))

        val ice = api.iceConfig(currentAuth, call.id)
        val connection = realtime.connect(currentAuth.session.accessToken, call.topic)
        realtimeConnection = connection
        withTimeout(12_000L) {
            connection.state.first {
                it == NativeVoiceRealtimeState.CONNECTED ||
                    it == NativeVoiceRealtimeState.FAILED ||
                    it == NativeVoiceRealtimeState.CLOSED
            }.also {
                check(it == NativeVoiceRealtimeState.CONNECTED) {
                    "เชื่อมต่อช่องสัญญาณเสียงไม่สำเร็จ"
                }
            }
        }

        val voicePeer = NativeVoicePeer(
            appContext,
            ice,
            onLocalIce = { candidate ->
                connection.send(
                    "ice",
                    JSONObject(candidate.toString()).put("call_id", call.id)
                )
            },
            onStateChanged = { peerState ->
                when (peerState) {
                    NativeVoicePeerState.CONNECTED ->
                        _state.value = _state.value.copy(
                            phase = NativeVoicePhase.CONNECTED,
                            error = null
                        )
                    NativeVoicePeerState.FAILED ->
                        scope.launch { fail(IllegalStateException("การเชื่อมต่อเสียงล้มเหลว")) }
                    NativeVoicePeerState.CLOSED -> Unit
                    else -> Unit
                }
            }
        )
        peer = voicePeer

        signalJob?.cancel()
        signalJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                connection.signals.collect { signal ->
                    if (signal.payload.optString("call_id") != call.id) return@collect
                    when (signal.event) {
                        "ready" -> {
                            if (caller) {
                                if (offerSent.compareAndSet(false, true)) {
                                    val offer = voicePeer.createOffer().put("call_id", call.id)
                                    check(connection.send("offer", offer)) {
                                        "ส่ง Voice offer ไม่สำเร็จ"
                                    }
                                }
                            } else {
                                // If our first ready was sent before the caller joined, answer
                                // the caller's ready so the caller can safely create an offer.
                                connection.send("ready", JSONObject().put("call_id", call.id))
                            }
                        }
                        "offer" -> if (!caller) {
                            val answer = voicePeer.acceptOfferAndCreateAnswer(signal.payload)
                                .put("call_id", call.id)
                            check(connection.send("answer", answer)) {
                                "ส่ง Voice answer ไม่สำเร็จ"
                            }
                        }
                        "answer" -> if (caller) {
                            voicePeer.acceptAnswer(signal.payload)
                        }
                        "ice" -> {
                            check(voicePeer.addRemoteIce(signal.payload)) {
                                "เพิ่ม ICE candidate ไม่สำเร็จ"
                            }
                        }
                        "hangup" -> finishLocal(NativeVoicePhase.ENDED, null)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                fail(failure)
            }
        }

        check(connection.send("ready", JSONObject().put("call_id", call.id))) {
            "แจ้งความพร้อมสายโทรไม่สำเร็จ"
        }

        authorizationJob?.cancel()
        authorizationJob = scope.launch {
            var transientFailures = 0
            while (true) {
                delay(10_000L)
                val latestAuth = auth ?: run {
                    finishLocal(NativeVoicePhase.ENDED, "Session สิ้นสุดแล้ว")
                    return@launch
                }
                try {
                    val active = api.active(latestAuth, call.orderId)
                    transientFailures = 0
                    if (active?.id != call.id || active.status != "accepted") {
                        connection.send("hangup", JSONObject().put("call_id", call.id))
                        finishLocal(NativeVoicePhase.ENDED, null)
                        return@launch
                    }
                    connection.updateAccessToken(latestAuth.session.accessToken)
                } catch (failure: QueueGoHttpException) {
                    if (failure.statusCode in 400..499) {
                        finishLocal(
                            NativeVoicePhase.ENDED,
                            failure.message ?: "สิทธิ์สายโทรสิ้นสุดแล้ว"
                        )
                        return@launch
                    }
                    transientFailures += 1
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    transientFailures += 1
                }
                if (transientFailures >= 3) {
                    fail(IllegalStateException("ตรวจสอบสิทธิ์สายโทรไม่สำเร็จ"))
                    return@launch
                }
            }
        }
    }

    private fun cancelOperationOnly() {
        operationJob?.cancel()
        operationJob = null
    }

    private fun closeTransport() {
        authorizationJob?.cancel()
        authorizationJob = null
        signalJob?.cancel()
        signalJob = null
        runCatching { realtimeConnection?.close() }
        realtimeConnection = null
        runCatching { peer?.close() }
        peer = null
    }

    private fun finishLocal(phase: NativeVoicePhase, message: String?) {
        cancelOperationOnly()
        closeTransport()
        val current = _state.value
        _state.value = current.copy(phase = phase, error = message)
    }

    private fun fail(failure: Throwable) {
        if (closed.get()) return
        finishLocal(
            NativeVoicePhase.ERROR,
            failure.message?.takeIf { it.isNotBlank() } ?: "โทรผ่าน QueueGo ไม่สำเร็จ"
        )
    }

    private fun setState(next: NativeVoiceControllerState) {
        if (!closed.get()) _state.value = next
    }

    private fun checkOpen() {
        check(!closed.get()) { "Voice controller ปิดแล้ว" }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        cancelOperationOnly()
        closeTransport()
        scope.cancel()
        _state.value = _state.value.copy(phase = NativeVoicePhase.ENDED)
    }
}
