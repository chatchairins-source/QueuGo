package com.queuego.shared

import android.content.Context
import android.media.AudioManager
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule

enum class NativeVoicePeerState {
    NEW, CONNECTING, CONNECTED, DISCONNECTED, FAILED, CLOSED
}

/**
 * Audio-only WebRTC peer. The caller must verify RECORD_AUDIO before construction.
 * Signaling is intentionally external so authorization stays in NativeVoiceCallApi/Realtime.
 */
class NativeVoicePeer(
    context: Context,
    iceServers: List<NativeVoiceIceServer>,
    private val onLocalIce: (JSONObject) -> Unit,
    private val onStateChanged: (NativeVoicePeerState) -> Unit = {}
) : Closeable {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val originalAudioMode = audioManager.mode
    @Suppress("DEPRECATION")
    private val originalSpeaker = audioManager.isSpeakerphoneOn
    private val closed = AtomicBoolean(false)
    private val remoteDescriptionReady = AtomicBoolean(false)
    private val remoteIceLock = Any()
    private val pendingRemoteIce = mutableListOf<IceCandidate>()

    private val factory: PeerConnectionFactory
    private val audioSource: AudioSource
    private val audioTrack: AudioTrack
    private val peer: PeerConnection

    @Volatile
    var state: NativeVoicePeerState = NativeVoicePeerState.NEW
        private set

    init {
        ensureWebRtcInitialized(appContext)
        val audioModule = JavaAudioDeviceModule.builder(appContext).createAudioDeviceModule()
        try {
            factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioModule)
                .createPeerConnectionFactory()
        } finally {
            audioModule.release()
        }

        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("queuego-audio", audioSource).apply {
            setEnabled(true)
        }

        val rtcConfig = PeerConnection.RTCConfiguration(
            iceServers.map { server ->
                PeerConnection.IceServer.builder(server.urls)
                    .apply {
                        server.username?.let(::setUsername)
                        server.credential?.let(::setPassword)
                    }
                    .createIceServer()
            }
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peer = requireNotNull(factory.createPeerConnection(rtcConfig, observer())) {
            "สร้างการเชื่อมต่อเสียงไม่สำเร็จ"
        }
        peer.addTrack(audioTrack, listOf("queuego-audio-stream"))
        peer.setAudioPlayout(true)
        peer.setAudioRecording(true)
        setState(NativeVoicePeerState.CONNECTING)
        configureAudioRoute(speaker = false)
    }

    suspend fun createOffer(): JSONObject {
        checkOpen()
        val description = createDescription { observer ->
            peer.createOffer(observer, audioOfferConstraints())
        }
        setLocalDescription(description)
        return JSONObject().put("type", "offer").put("sdp", description.description)
    }

    suspend fun acceptOfferAndCreateAnswer(payload: JSONObject): JSONObject {
        checkOpen()
        require(payload.optString("type") == "offer") { "Voice offer ไม่ถูกต้อง" }
        val sdp = payload.getString("sdp").takeIf { it.isNotBlank() }
            ?: error("Voice offer ไม่มี SDP")
        setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, sdp))
        val answer = createDescription { observer ->
            peer.createAnswer(observer, audioOfferConstraints())
        }
        setLocalDescription(answer)
        return JSONObject().put("type", "answer").put("sdp", answer.description)
    }

    suspend fun acceptAnswer(payload: JSONObject) {
        checkOpen()
        require(payload.optString("type") == "answer") { "Voice answer ไม่ถูกต้อง" }
        val sdp = payload.getString("sdp").takeIf { it.isNotBlank() }
            ?: error("Voice answer ไม่มี SDP")
        setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    fun addRemoteIce(payload: JSONObject): Boolean {
        checkOpen()
        val candidate = IceCandidate(
            payload.optString("sdpMid").takeIf { it.isNotBlank() },
            payload.getInt("sdpMLineIndex"),
            payload.getString("candidate")
        )
        if (!remoteDescriptionReady.get()) {
            synchronized(remoteIceLock) {
                if (!remoteDescriptionReady.get()) {
                    pendingRemoteIce += candidate
                    return true
                }
            }
        }
        return peer.addIceCandidate(candidate)
    }

    fun setMuted(muted: Boolean) {
        checkOpen()
        audioTrack.setEnabled(!muted)
        peer.setAudioRecording(!muted)
    }

    @Suppress("DEPRECATION")
    fun setSpeakerEnabled(enabled: Boolean) {
        checkOpen()
        configureAudioRoute(enabled)
    }

    private fun observer(): PeerConnection.Observer = object : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            when (newState) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> setState(NativeVoicePeerState.CONNECTED)
                PeerConnection.IceConnectionState.DISCONNECTED -> setState(NativeVoicePeerState.DISCONNECTED)
                PeerConnection.IceConnectionState.FAILED -> setState(NativeVoicePeerState.FAILED)
                PeerConnection.IceConnectionState.CLOSED -> setState(NativeVoicePeerState.CLOSED)
                PeerConnection.IceConnectionState.CHECKING -> setState(NativeVoicePeerState.CONNECTING)
                PeerConnection.IceConnectionState.NEW -> Unit
            }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            when (newState) {
                PeerConnection.PeerConnectionState.NEW -> Unit
                PeerConnection.PeerConnectionState.CONNECTING -> setState(NativeVoicePeerState.CONNECTING)
                PeerConnection.PeerConnectionState.CONNECTED -> setState(NativeVoicePeerState.CONNECTED)
                PeerConnection.PeerConnectionState.DISCONNECTED -> setState(NativeVoicePeerState.DISCONNECTED)
                PeerConnection.PeerConnectionState.FAILED -> setState(NativeVoicePeerState.FAILED)
                PeerConnection.PeerConnectionState.CLOSED -> setState(NativeVoicePeerState.CLOSED)
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit

        override fun onIceCandidate(candidate: IceCandidate) {
            if (closed.get()) return
            onLocalIce(
                JSONObject()
                    .put("sdpMid", candidate.sdpMid ?: JSONObject.NULL)
                    .put("sdpMLineIndex", candidate.sdpMLineIndex)
                    .put("candidate", candidate.sdp)
            )
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(dataChannel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) = Unit
    }

    private suspend fun createDescription(
        start: (SdpObserver) -> Unit
    ): SessionDescription {
        val result = CompletableDeferred<SessionDescription>()
        start(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) {
                result.complete(description)
            }
            override fun onSetSuccess() = Unit
            override fun onCreateFailure(error: String) {
                result.completeExceptionally(IllegalStateException(error))
            }
            override fun onSetFailure(error: String) = Unit
        })
        return withTimeout(12_000) { result.await() }
    }

    private suspend fun setLocalDescription(description: SessionDescription) {
        val result = CompletableDeferred<Unit>()
        peer.setLocalDescription(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) = Unit
            override fun onSetSuccess() { result.complete(Unit) }
            override fun onCreateFailure(error: String) = Unit
            override fun onSetFailure(error: String) {
                result.completeExceptionally(IllegalStateException(error))
            }
        }, description)
        withTimeout(12_000) { result.await() }
    }

    private suspend fun setRemoteDescription(description: SessionDescription) {
        val result = CompletableDeferred<Unit>()
        peer.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) = Unit
            override fun onSetSuccess() { result.complete(Unit) }
            override fun onCreateFailure(error: String) = Unit
            override fun onSetFailure(error: String) {
                result.completeExceptionally(IllegalStateException(error))
            }
        }, description)
        withTimeout(12_000) { result.await() }
        remoteDescriptionReady.set(true)
        val queued = synchronized(remoteIceLock) {
            pendingRemoteIce.toList().also { pendingRemoteIce.clear() }
        }
        queued.forEach { candidate ->
            check(peer.addIceCandidate(candidate)) { "เพิ่ม ICE candidate ไม่สำเร็จ" }
        }
    }

    private fun audioOfferConstraints(): MediaConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
    }

    @Suppress("DEPRECATION")
    private fun configureAudioRoute(speaker: Boolean) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = speaker
    }

    private fun setState(next: NativeVoicePeerState) {
        if (closed.get() && next != NativeVoicePeerState.CLOSED) return
        if (state == next) return
        state = next
        onStateChanged(next)
    }

    private fun checkOpen() {
        check(!closed.get()) { "Voice peer ปิดแล้ว" }
    }

    @Suppress("DEPRECATION")
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { peer.setAudioRecording(false) }
        runCatching { peer.setAudioPlayout(false) }
        runCatching { peer.close() }
        runCatching { peer.dispose() }
        runCatching { audioTrack.dispose() }
        runCatching { audioSource.dispose() }
        runCatching { factory.dispose() }
        runCatching {
            audioManager.mode = originalAudioMode
            audioManager.isSpeakerphoneOn = originalSpeaker
        }
        state = NativeVoicePeerState.CLOSED
        onStateChanged(NativeVoicePeerState.CLOSED)
    }

    companion object {
        @Volatile private var initialized = false

        private fun ensureWebRtcInitialized(context: Context) {
            if (initialized) return
            synchronized(this) {
                if (initialized) return
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions()
                )
                initialized = true
            }
        }
    }
}
