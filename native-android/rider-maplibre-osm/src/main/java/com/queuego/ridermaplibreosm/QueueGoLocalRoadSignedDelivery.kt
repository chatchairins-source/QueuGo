package com.queuego.ridermaplibreosm

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature

private const val QUEUEGO_LOCAL_ROAD_ENVELOPE_VERSION = 1
private const val QUEUEGO_LOCAL_ROAD_MAX_SIGNED_PAYLOAD_BYTES = 8 * 1024 * 1024
private const val QUEUEGO_LOCAL_ROAD_MAX_SIGNATURE_BYTES = 4 * 1024
private const val QUEUEGO_LOCAL_ROAD_MAX_KEY_ID_LENGTH = 96

data class QueueGoLocalRoadSignedEnvelope(
    val envelopeVersion: Int = QUEUEGO_LOCAL_ROAD_ENVELOPE_VERSION,
    val keyId: String,
    val payload: ByteArray,
    val payloadSha256Hex: String,
    val signature: ByteArray
)

interface QueueGoLocalRoadSnapshotTransport {
    suspend fun fetch(): QueueGoLocalRoadSignedEnvelope?
}

interface QueueGoLocalRoadFeedDecoder {
    fun decode(payload: ByteArray): QueueGoLocalRoadFeed
}

fun interface QueueGoLocalRoadSignatureVerifier {
    fun verify(keyId: String, payload: ByteArray, signature: ByteArray): Boolean
}

class QueueGoLocalRoadEcdsaVerifier(
    trustedKeys: Map<String, PublicKey>
) : QueueGoLocalRoadSignatureVerifier {
    private val trustedKeys = trustedKeys.toMap()

    override fun verify(
        keyId: String,
        payload: ByteArray,
        signature: ByteArray
    ): Boolean {
        val publicKey = trustedKeys[keyId] ?: return false
        return runCatching {
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(publicKey)
            verifier.update(payload)
            verifier.verify(signature)
        }.getOrDefault(false)
    }
}

data class QueueGoLocalRoadCachedSnapshot(
    val revision: Long,
    val payloadSha256Hex: String,
    val payload: ByteArray
)

class QueueGoLocalRoadSnapshotCache(
    private val directory: File
) {
    private val payloadFile = File(directory, "local-roads.snapshot")
    private val metadataFile = File(directory, "local-roads.meta")

    @Synchronized
    fun read(): QueueGoLocalRoadCachedSnapshot? = runCatching {
        if (!payloadFile.isFile || !metadataFile.isFile) return null
        val lines = metadataFile.readLines()
        if (lines.size != 2) return null
        val revision = lines[0].toLongOrNull()?.takeIf { it > 0L } ?: return null
        val expectedSha = normalizeSha256(lines[1]) ?: return null
        val payload = payloadFile.readBytes()
        val actualSha = queueGoSha256Hex(payload)
        if (actualSha != expectedSha) return null
        QueueGoLocalRoadCachedSnapshot(
            revision = revision,
            payloadSha256Hex = actualSha,
            payload = payload
        )
    }.getOrNull()

    @Synchronized
    fun write(
        revision: Long,
        payloadSha256Hex: String,
        payload: ByteArray
    ) {
        require(revision > 0L) { "Local-road cache revision must be positive" }
        val safeSha = normalizeSha256(payloadSha256Hex)
            ?: throw IllegalArgumentException("Local-road cache SHA-256 is invalid")
        require(queueGoSha256Hex(payload) == safeSha) {
            "Local-road cache payload digest does not match"
        }

        directory.mkdirs()
        require(directory.isDirectory) { "Local-road cache directory is unavailable" }

        val payloadTmp = File(directory, "local-roads.snapshot.tmp")
        val metadataTmp = File(directory, "local-roads.meta.tmp")
        writeSynced(payloadTmp, payload)
        writeSynced(metadataTmp, "$revision\n$safeSha\n".toByteArray(Charsets.UTF_8))

        moveAtomically(payloadTmp, payloadFile)
        try {
            moveAtomically(metadataTmp, metadataFile)
        } catch (error: Throwable) {
            // A metadata failure leaves the payload unreadable as a snapshot because read()
            // requires matching metadata. Never invent or auto-promote a revision.
            runCatching { metadataTmp.delete() }
            throw error
        }
    }

    private fun writeSynced(file: File, bytes: ByteArray) {
        FileOutputStream(file).use { output ->
            output.write(bytes)
            output.flush()
            output.fd.sync()
        }
    }

    private fun moveAtomically(from: File, to: File) {
        runCatching {
            Files.move(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        }.getOrElse {
            Files.move(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
}

enum class QueueGoLocalRoadDeliveryStatus {
    NO_SNAPSHOT,
    APPLIED,
    UNCHANGED,
    REJECTED_STALE
}

data class QueueGoLocalRoadDeliveryResult(
    val status: QueueGoLocalRoadDeliveryStatus,
    val revision: Long?,
    val roads: List<QueueGoLocalRoad> = emptyList()
)

class QueueGoLocalRoadSignedDelivery(
    private val transport: QueueGoLocalRoadSnapshotTransport,
    private val decoder: QueueGoLocalRoadFeedDecoder,
    private val verifier: QueueGoLocalRoadSignatureVerifier,
    private val cache: QueueGoLocalRoadSnapshotCache
) {
    suspend fun refresh(): QueueGoLocalRoadDeliveryResult {
        val current = cache.read()
        val envelope = transport.fetch()
            ?: return QueueGoLocalRoadDeliveryResult(
                status = QueueGoLocalRoadDeliveryStatus.NO_SNAPSHOT,
                revision = current?.revision
            )

        validateEnvelope(envelope)
        require(verifier.verify(envelope.keyId, envelope.payload, envelope.signature)) {
            "Local-road signature verification failed"
        }

        val feed = decoder.decode(envelope.payload)
        val validated = validateQueueGoLocalRoadFeed(
            feed = feed,
            currentRevision = current?.revision
        )

        return when (validated.decision) {
            QueueGoLocalRoadFeedDecision.APPLY -> {
                cache.write(
                    revision = validated.revision,
                    payloadSha256Hex = envelope.payloadSha256Hex,
                    payload = envelope.payload
                )
                QueueGoLocalRoadDeliveryResult(
                    status = QueueGoLocalRoadDeliveryStatus.APPLIED,
                    revision = validated.revision,
                    roads = requireNotNull(validated.roadsForApply())
                )
            }
            QueueGoLocalRoadFeedDecision.UNCHANGED ->
                QueueGoLocalRoadDeliveryResult(
                    status = QueueGoLocalRoadDeliveryStatus.UNCHANGED,
                    revision = validated.revision
                )
            QueueGoLocalRoadFeedDecision.REJECT_STALE ->
                QueueGoLocalRoadDeliveryResult(
                    status = QueueGoLocalRoadDeliveryStatus.REJECTED_STALE,
                    revision = current?.revision
                )
        }
    }

    private fun validateEnvelope(envelope: QueueGoLocalRoadSignedEnvelope) {
        require(envelope.envelopeVersion == QUEUEGO_LOCAL_ROAD_ENVELOPE_VERSION) {
            "Unsupported local-road envelope version"
        }
        require(envelope.keyId.isNotBlank() &&
            envelope.keyId.length <= QUEUEGO_LOCAL_ROAD_MAX_KEY_ID_LENGTH
        ) {
            "Local-road signing key id is invalid"
        }
        require(envelope.payload.isNotEmpty() &&
            envelope.payload.size <= QUEUEGO_LOCAL_ROAD_MAX_SIGNED_PAYLOAD_BYTES
        ) {
            "Local-road signed payload size is invalid"
        }
        require(envelope.signature.isNotEmpty() &&
            envelope.signature.size <= QUEUEGO_LOCAL_ROAD_MAX_SIGNATURE_BYTES
        ) {
            "Local-road signature size is invalid"
        }

        val expectedSha = normalizeSha256(envelope.payloadSha256Hex)
            ?: throw IllegalArgumentException("Local-road envelope SHA-256 is invalid")
        require(queueGoSha256Hex(envelope.payload) == expectedSha) {
            "Local-road payload digest verification failed"
        }
    }
}

internal fun queueGoSha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun normalizeSha256(value: String): String? =
    value.trim()
        .lowercase()
        .takeIf { it.length == 64 && it.all { ch -> ch in '0'..'9' || ch in 'a'..'f' } }
