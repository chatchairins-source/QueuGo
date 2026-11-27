package com.queuego.ridermaplibreosm

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature

private const val QUEUEGO_LOCAL_ROAD_ENVELOPE_VERSION = 1
internal const val QUEUEGO_LOCAL_ROAD_MAX_SIGNED_PAYLOAD_BYTES = 8 * 1024 * 1024
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
    val envelopeVersion: Int,
    val keyId: String,
    val revision: Long,
    val payloadSha256Hex: String,
    val payload: ByteArray,
    val signature: ByteArray
)

class QueueGoLocalRoadSnapshotCache(
    private val directory: File
) {
    private val snapshotFile = File(directory, "local-roads.qglr")
    private val tempFile = File(directory, "local-roads.qglr.tmp")

    @Synchronized
    fun read(): QueueGoLocalRoadCachedSnapshot? {
        if (!snapshotFile.exists()) return null
        require(snapshotFile.isFile) { "Local-road cache path is invalid" }

        return DataInputStream(FileInputStream(snapshotFile)).use { input ->
            require(input.readInt() == CACHE_MAGIC) { "Local-road cache magic is invalid" }
            require(input.readInt() == CACHE_FORMAT_VERSION) {
                "Unsupported local-road cache format"
            }
            val envelopeVersion = input.readInt()
            require(envelopeVersion == QUEUEGO_LOCAL_ROAD_ENVELOPE_VERSION) {
                "Unsupported cached local-road envelope version"
            }
            val keyId = input.readUTF()
            require(keyId.isNotBlank() && keyId.length <= QUEUEGO_LOCAL_ROAD_MAX_KEY_ID_LENGTH) {
                "Local-road cached signing key id is invalid"
            }
            val revision = input.readLong()
            require(revision > 0L) { "Local-road cache revision is invalid" }

            val expectedSha = normalizeSha256(input.readUTF())
                ?: throw IllegalArgumentException("Local-road cache SHA-256 is invalid")
            val signatureLength = input.readInt()
            require(signatureLength in 1..QUEUEGO_LOCAL_ROAD_MAX_SIGNATURE_BYTES) {
                "Local-road cached signature size is invalid"
            }
            val signature = ByteArray(signatureLength)
            input.readFully(signature)

            val payloadLength = input.readInt()
            require(payloadLength in 1..QUEUEGO_LOCAL_ROAD_MAX_SIGNED_PAYLOAD_BYTES) {
                "Local-road cache payload size is invalid"
            }
            val payload = ByteArray(payloadLength)
            input.readFully(payload)
            require(input.read() == -1) { "Local-road cache contains trailing bytes" }

            val actualSha = queueGoSha256Hex(payload)
            require(actualSha == expectedSha) {
                "Local-road cache payload digest does not match"
            }
            QueueGoLocalRoadCachedSnapshot(
                envelopeVersion = envelopeVersion,
                keyId = keyId,
                revision = revision,
                payloadSha256Hex = actualSha,
                payload = payload,
                signature = signature
            )
        }
    }

    @Synchronized
    fun write(
        revision: Long,
        envelope: QueueGoLocalRoadSignedEnvelope
    ) {
        require(revision > 0L) { "Local-road cache revision must be positive" }
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
            "Local-road cache payload size is invalid"
        }
        require(envelope.signature.isNotEmpty() &&
            envelope.signature.size <= QUEUEGO_LOCAL_ROAD_MAX_SIGNATURE_BYTES
        ) {
            "Local-road signature size is invalid"
        }
        val safeSha = normalizeSha256(envelope.payloadSha256Hex)
            ?: throw IllegalArgumentException("Local-road cache SHA-256 is invalid")
        require(queueGoSha256Hex(envelope.payload) == safeSha) {
            "Local-road cache payload digest does not match"
        }

        directory.mkdirs()
        require(directory.isDirectory) { "Local-road cache directory is unavailable" }

        FileOutputStream(tempFile).use { fileOutput ->
            DataOutputStream(fileOutput).use { output ->
                output.writeInt(CACHE_MAGIC)
                output.writeInt(CACHE_FORMAT_VERSION)
                output.writeInt(envelope.envelopeVersion)
                output.writeUTF(envelope.keyId)
                output.writeLong(revision)
                output.writeUTF(safeSha)
                output.writeInt(envelope.signature.size)
                output.write(envelope.signature)
                output.writeInt(envelope.payload.size)
                output.write(envelope.payload)
                output.flush()
                fileOutput.fd.sync()
            }
        }

        moveAtomically(tempFile, snapshotFile)
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

    private companion object {
        const val CACHE_MAGIC = 0x51474C52
        const val CACHE_FORMAT_VERSION = 2
    }
}

enum class QueueGoLocalRoadDeliveryStatus {
    NO_SNAPSHOT,
    CACHED,
    APPLIED,
    UNCHANGED,
    REJECTED_STALE
}

data class QueueGoLocalRoadDeliveryResult(
    val status: QueueGoLocalRoadDeliveryStatus,
    val revision: Long?,
    val roads: List<QueueGoLocalRoad> = emptyList(),
    val junctions: List<QueueGoVerifiedLocalRoadJunction> = emptyList()
)

class QueueGoLocalRoadSignedDelivery(
    private val transport: QueueGoLocalRoadSnapshotTransport,
    private val decoder: QueueGoLocalRoadFeedDecoder,
    private val verifier: QueueGoLocalRoadSignatureVerifier,
    private val cache: QueueGoLocalRoadSnapshotCache
) {
    fun loadCached(): QueueGoLocalRoadDeliveryResult {
        val trusted = loadTrustedCache()
            ?: return QueueGoLocalRoadDeliveryResult(
                status = QueueGoLocalRoadDeliveryStatus.NO_SNAPSHOT,
                revision = null
            )
        return QueueGoLocalRoadDeliveryResult(
            status = QueueGoLocalRoadDeliveryStatus.CACHED,
            revision = trusted.validated.revision,
            roads = trusted.validated.roads,
            junctions = trusted.validated.junctions
        )
    }

    suspend fun refresh(): QueueGoLocalRoadDeliveryResult {
        val current = loadTrustedCache()
        val envelope = transport.fetch()
            ?: return current?.let {
                QueueGoLocalRoadDeliveryResult(
                    status = QueueGoLocalRoadDeliveryStatus.CACHED,
                    revision = it.validated.revision,
                    roads = it.validated.roads,
                    junctions = it.validated.junctions
                )
            } ?: QueueGoLocalRoadDeliveryResult(
                status = QueueGoLocalRoadDeliveryStatus.NO_SNAPSHOT,
                revision = null
            )

        validateEnvelope(envelope)
        require(verifier.verify(envelope.keyId, envelope.payload, envelope.signature)) {
            "Local-road signature verification failed"
        }

        val feed = decoder.decode(envelope.payload)
        val validated = validateQueueGoLocalRoadFeed(
            feed = feed,
            currentRevision = current?.validated?.revision
        )

        return when (validated.decision) {
            QueueGoLocalRoadFeedDecision.APPLY -> {
                cache.write(
                    revision = validated.revision,
                    envelope = envelope
                )
                QueueGoLocalRoadDeliveryResult(
                    status = QueueGoLocalRoadDeliveryStatus.APPLIED,
                    revision = validated.revision,
                    roads = requireNotNull(validated.roadsForApply()),
                    junctions = requireNotNull(validated.junctionsForApply())
                )
            }
            QueueGoLocalRoadFeedDecision.UNCHANGED ->
                QueueGoLocalRoadDeliveryResult(
                    status = QueueGoLocalRoadDeliveryStatus.UNCHANGED,
                    revision = validated.revision,
                    roads = current?.validated?.roads.orEmpty(),
                    junctions = current?.validated?.junctions.orEmpty()
                )
            QueueGoLocalRoadFeedDecision.REJECT_STALE ->
                QueueGoLocalRoadDeliveryResult(
                    status = QueueGoLocalRoadDeliveryStatus.REJECTED_STALE,
                    revision = current?.validated?.revision,
                    roads = current?.validated?.roads.orEmpty(),
                    junctions = current?.validated?.junctions.orEmpty()
                )
        }
    }

    private fun loadTrustedCache(): TrustedCachedLocalRoadSnapshot? {
        val cached = cache.read() ?: return null
        val envelope = QueueGoLocalRoadSignedEnvelope(
            envelopeVersion = cached.envelopeVersion,
            keyId = cached.keyId,
            payload = cached.payload,
            payloadSha256Hex = cached.payloadSha256Hex,
            signature = cached.signature
        )
        validateEnvelope(envelope)
        require(verifier.verify(envelope.keyId, envelope.payload, envelope.signature)) {
            "Cached local-road signature verification failed"
        }
        val validated = validateQueueGoLocalRoadFeed(decoder.decode(envelope.payload))
        require(validated.revision == cached.revision) {
            "Cached local-road revision does not match signed payload"
        }
        return TrustedCachedLocalRoadSnapshot(cached, validated)
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

private data class TrustedCachedLocalRoadSnapshot(
    val cached: QueueGoLocalRoadCachedSnapshot,
    val validated: QueueGoValidatedLocalRoadFeed
)

internal fun queueGoSha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun normalizeSha256(value: String): String? =
    value.trim()
        .lowercase()
        .takeIf { it.length == 64 && it.all { ch -> ch in '0'..'9' || ch in 'a'..'f' } }
