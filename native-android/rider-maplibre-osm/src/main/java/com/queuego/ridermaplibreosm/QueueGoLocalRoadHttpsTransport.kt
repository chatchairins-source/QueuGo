package com.queuego.ridermaplibreosm

import java.io.IOException
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

private const val QUEUEGO_LOCAL_ROAD_MAX_SIGNATURE_HEADER_CHARS = 8 * 1024

const val QUEUEGO_LOCAL_ROAD_HEADER_ENVELOPE_VERSION = "X-QueueGo-Local-Road-Envelope-Version"
const val QUEUEGO_LOCAL_ROAD_HEADER_KEY_ID = "X-QueueGo-Local-Road-Key-Id"
const val QUEUEGO_LOCAL_ROAD_HEADER_SHA256 = "X-QueueGo-Local-Road-SHA256"
const val QUEUEGO_LOCAL_ROAD_HEADER_SIGNATURE = "X-QueueGo-Local-Road-Signature"
const val QUEUEGO_LOCAL_ROAD_MEDIA_TYPE = "application/vnd.queuego.local-roads+json"

class QueueGoLocalRoadHttpsTransport(
    private val client: OkHttpClient,
    private val endpoint: HttpUrl,
    private val maxPayloadBytes: Long = QUEUEGO_LOCAL_ROAD_MAX_SIGNED_PAYLOAD_BYTES.toLong()
) : QueueGoLocalRoadSnapshotTransport {
    init {
        require(endpoint.isHttps) { "Local-road endpoint must use HTTPS" }
        require(maxPayloadBytes in 1L..QUEUEGO_LOCAL_ROAD_MAX_SIGNED_PAYLOAD_BYTES.toLong()) {
            "Local-road payload limit is invalid"
        }
    }

    override suspend fun fetch(): QueueGoLocalRoadSignedEnvelope? =
        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url(endpoint)
                .get()
                .header("Accept", QUEUEGO_LOCAL_ROAD_MEDIA_TYPE)
                .build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { result ->
                        try {
                            if (result.code == 204 || result.code == 304) {
                                if (continuation.isActive) continuation.resume(null)
                                return
                            }
                            if (!result.isSuccessful) {
                                throw IOException("Local-road snapshot HTTP " + result.code)
                            }

                            val body = result.body
                                ?: throw IOException("Local-road snapshot is empty")
                            val contentType = body.contentType()
                                ?: throw IOException("Local-road Content-Type is missing")
                            if (
                                contentType.type != "application" ||
                                contentType.subtype != "vnd.queuego.local-roads+json"
                            ) {
                                throw IOException("Local-road Content-Type is invalid")
                            }

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

                            val envelopeVersion = requiredHeader(
                                result,
                                QUEUEGO_LOCAL_ROAD_HEADER_ENVELOPE_VERSION,
                                "envelope-version"
                            ).toIntOrNull()
                                ?: throw IOException(
                                    "Local-road envelope-version header is invalid"
                                )
                            val keyId = requiredHeader(
                                result,
                                QUEUEGO_LOCAL_ROAD_HEADER_KEY_ID,
                                "key-id"
                            )
                            val sha256 = requiredHeader(
                                result,
                                QUEUEGO_LOCAL_ROAD_HEADER_SHA256,
                                "SHA-256"
                            ).lowercase()
                            val signatureHeader = requiredHeader(
                                result,
                                QUEUEGO_LOCAL_ROAD_HEADER_SIGNATURE,
                                "signature"
                            )
                            if (signatureHeader.length > QUEUEGO_LOCAL_ROAD_MAX_SIGNATURE_HEADER_CHARS) {
                                throw IOException("Local-road signature header is too large")
                            }
                            val signature = try {
                                Base64.getDecoder().decode(signatureHeader)
                            } catch (error: IllegalArgumentException) {
                                throw IOException("Local-road signature encoding is invalid", error)
                            }
                            if (signature.isEmpty()) {
                                throw IOException("Local-road signature is empty")
                            }

                            if (continuation.isActive) {
                                continuation.resume(
                                    QueueGoLocalRoadSignedEnvelope(
                                        envelopeVersion = envelopeVersion,
                                        keyId = keyId,
                                        payload = bytes,
                                        payloadSha256Hex = sha256,
                                        signature = signature
                                    )
                                )
                            }
                        } catch (error: Exception) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(error)
                            }
                        }
                    }
                }
            })
        }

    private fun requiredHeader(
        response: Response,
        name: String,
        label: String
    ): String =
        response.header(name)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw IOException("Local-road $label header is missing")
}
