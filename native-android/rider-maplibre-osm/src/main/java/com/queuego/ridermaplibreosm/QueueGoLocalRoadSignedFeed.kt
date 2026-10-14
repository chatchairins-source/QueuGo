package com.queuego.ridermaplibreosm

import java.io.IOException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val QUEUEGO_LOCAL_ROAD_MAX_SIGNED_PAYLOAD_BYTES = 8L * 1024L * 1024L
private const val QUEUEGO_LOCAL_ROAD_MAX_SIGNATURE_CHARS = 1_024

const val QUEUEGO_LOCAL_ROAD_HEADER_KEY_ID = "X-QueueGo-Local-Road-Key-Id"
const val QUEUEGO_LOCAL_ROAD_HEADER_SHA256 = "X-QueueGo-Local-Road-SHA256"
const val QUEUEGO_LOCAL_ROAD_HEADER_SIGNATURE = "X-QueueGo-Local-Road-Signature"

data class QueueGoLocalRoadSignedHeaders(
    val keyId: String,
    val sha256Hex: String,
    val signatureBase64: String
)

data class QueueGoLocalRoadSignedPayload(
    val bytes: ByteArray,
    val headers: QueueGoLocalRoadSignedHeaders
)

fun interface QueueGoLocalRoadFeedDecoder {
    fun decode(payload: ByteArray): QueueGoLocalRoadFeed
}

class QueueGoLocalRoadEcdsaVerifier(
    private val expectedKeyId: String,
    publicKeyDer: ByteArray
) {
    private val publicKey: PublicKey

    init {
        require(expectedKeyId.isNotBlank()) { "Local-road key id is required" }
        require(expectedKeyId.length <= 96) { "Local-road key id is too long" }
        require(publicKeyDer.isNotEmpty()) { "Local-road public key is required" }
        publicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(publicKeyDer.copyOf()))
    }

    fun verify(payload: QueueGoLocalRoadSignedPayload) {
        val headers = payload.headers
        require(headers.keyId == expectedKeyId) { "Local-road signing key id mismatch" }
        require(headers.sha256Hex.length == 64) { "Local-road SHA-256 header is invalid" }
        require(headers.sha256Hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "Local-road SHA-256 header is invalid"
        }
        require(headers.signatureBase64.isNotBlank()) { "Local-road signature is required" }
        require(headers.signatureBase64.length <= QUEUEGO_LOCAL_ROAD_MAX_SIGNATURE_CHARS) {
            "Local-road signature is too large"
        }

        val expectedDigest = headers.sha256Hex.hexToBytes()
        val actualDigest = MessageDigest.getInstance("SHA-256").digest(payload.bytes)
        require(MessageDigest.isEqual(expectedDigest, actualDigest)) {
            "Local-road payload digest mismatch"
        }

        val signatureBytes = runCatching {
            Base64.getDecoder().decode(headers.signatureBase64)
        }.getOrElse {
            throw IllegalArgumentException("Local-road signature encoding is invalid", it)
        }
        require(signatureBytes.isNotEmpty()) { "Local-road signature is empty" }

        val valid = Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(payload.bytes)
            verify(signatureBytes)
        }
        require(valid) { "Local-road signature verification failed" }
    }
}

class QueueGoLocalRoadSignedFeedClient(
    private val client: OkHttpClient,
    private val endpoint: HttpUrl,
    private val verifier: QueueGoLocalRoadEcdsaVerifier,
    private val decoder: QueueGoLocalRoadFeedDecoder,
    private val maxPayloadBytes: Long = QUEUEGO_LOCAL_ROAD_MAX_SIGNED_PAYLOAD_BYTES
) {
    init {
        require(endpoint.isHttps) { "Local-road endpoint must use HTTPS" }
        require(maxPayloadBytes in 1L..QUEUEGO_LOCAL_ROAD_MAX_SIGNED_PAYLOAD_BYTES) {
            "Local-road payload limit is invalid"
        }
    }

    suspend fun fetch(
        currentRevision: Long? = null
    ): QueueGoValidatedLocalRoadFeed {
        val signedPayload = fetchSignedPayload()
        verifier.verify(signedPayload)

        val feed = runCatching {
            decoder.decode(signedPayload.bytes)
        }.getOrElse {
            throw IllegalArgumentException("Local-road payload cannot be decoded", it)
        }
        return validateQueueGoLocalRoadFeed(feed, currentRevision)
    }

    private suspend fun fetchSignedPayload(): QueueGoLocalRoadSignedPayload =
        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url(endpoint)
                .get()
                .header("Accept", "application/vnd.queuego.local-roads+json")
                .build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { result ->
                        try {
                            if (!result.isSuccessful) {
                                throw IOException("Local-road snapshot HTTP " + result.code)
                            }
                            val body = result.body ?: throw IOException("Local-road snapshot is empty")
                            val contentLength = body.contentLength()
                            if (contentLength > maxPayloadBytes) {
                                throw IOException("Local-road snapshot exceeds size limit")
                            }

                            val source = body.source()
                            if (source.request(maxPayloadBytes + 1L)) {
                                throw IOException("Local-road snapshot exceeds size limit")
                            }
                            val bytes = source.readByteArray()
                            if (bytes.isEmpty()) {
                                throw IOException("Local-road snapshot is empty")
                            }

                            val keyId = result.header(QUEUEGO_LOCAL_ROAD_HEADER_KEY_ID)
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() }
                                ?: throw IOException("Local-road key-id header is missing")
                            val sha256 = result.header(QUEUEGO_LOCAL_ROAD_HEADER_SHA256)
                                ?.trim()
                                ?.lowercase()
                                ?.takeIf { it.isNotEmpty() }
                                ?: throw IOException("Local-road SHA-256 header is missing")
                            val signature = result.header(QUEUEGO_LOCAL_ROAD_HEADER_SIGNATURE)
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() }
                                ?: throw IOException("Local-road signature header is missing")

                            if (continuation.isActive) {
                                continuation.resume(
                                    QueueGoLocalRoadSignedPayload(
                                        bytes = bytes,
                                        headers = QueueGoLocalRoadSignedHeaders(
                                            keyId = keyId,
                                            sha256Hex = sha256,
                                            signatureBase64 = signature
                                        )
                                    )
                                )
                            }
                        } catch (error: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    }
                }
            })
        }
}

internal fun ByteArray.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Hex value has invalid length" }
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
