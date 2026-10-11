package com.queuego.shared

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private const val NATIVE_EVIDENCE_MAX_BYTES = 5 * 1024 * 1024
private val NATIVE_EVIDENCE_TYPES = setOf("image/jpeg", "image/png", "image/webp")

private fun InputStream.readNativeEvidenceLimited(): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= NATIVE_EVIDENCE_MAX_BYTES) {
            "รูปหลักฐานต้องไม่เกิน 5 MB"
        }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

/**
 * Shared private qg-evidence storage transport for Native Customer/Rider support.
 *
 * The caller supplies the authenticated actor's Auth UUID as [ownerPrefix].
 * Storage/RLS remains authoritative; no privileged key is used on-device.
 */
object NativeEvidenceStorage {
    suspend fun uploadImage(
        context: Context,
        accessToken: String,
        ownerPrefix: String,
        uri: Uri
    ): String = withContext(Dispatchers.IO) {
        require(accessToken.isNotBlank()) { "เซสชันหมดอายุ กรุณาเข้าสู่ระบบใหม่" }
        require(ownerPrefix.isNotBlank() && !ownerPrefix.contains('/') && !ownerPrefix.contains("..")) {
            "ข้อมูลผู้ใช้งานไม่ถูกต้อง"
        }

        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty().lowercase()
        require(mime in NATIVE_EVIDENCE_TYPES) {
            "หลักฐานต้องเป็น JPG, PNG หรือ WebP"
        }
        val bytes = resolver.openInputStream(uri)?.use { it.readNativeEvidenceLimited() }
            ?: error("อ่านรูปหลักฐานไม่สำเร็จ")

        val extension = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val objectPath = ownerPrefix + "/" + UUID.randomUUID() + "." + extension
        val connection = URL(
            QueueGoNativeApi.BASE_URL + "/storage/v1/object/qg-evidence/" + objectPath
        ).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("apikey", QueueGoNativeApi.PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer " + accessToken)
            connection.setRequestProperty("Content-Type", mime)
            connection.setRequestProperty("x-upsert", "false")
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val detail = runCatching {
                    JSONObject(errorText).optString("message")
                }.getOrNull().orEmpty()
                throw QueueGoHttpException(
                    code,
                    detail.ifBlank { "อัปโหลดหลักฐานไม่สำเร็จ" }
                )
            }
        } finally {
            connection.disconnect()
        }
        objectPath
    }

    suspend fun signedImageUrl(
        accessToken: String,
        path: String,
        expiresInSeconds: Int = 300
    ): String = withContext(Dispatchers.IO) {
        require(accessToken.isNotBlank()) { "เซสชันหมดอายุ กรุณาเข้าสู่ระบบใหม่" }
        require(path.isNotBlank() && !path.contains("..")) { "ไม่พบรูปหลักฐาน" }
        require(expiresInSeconds in 30..3600) { "อายุลิงก์หลักฐานไม่ถูกต้อง" }

        val connection = URL(
            QueueGoNativeApi.BASE_URL + "/storage/v1/object/sign/qg-evidence/" + path
        ).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("apikey", QueueGoNativeApi.PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer " + accessToken)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use {
                it.write(
                    JSONObject()
                        .put("expiresIn", expiresInSeconds)
                        .toString()
                        .toByteArray(Charsets.UTF_8)
                )
            }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(body).optString("message") }
                    .getOrNull().orEmpty()
                throw QueueGoHttpException(
                    code,
                    detail.ifBlank { "เปิดหลักฐานไม่สำเร็จ" }
                )
            }
            val json = JSONObject(body)
            val signed = json.optString("signedURL")
                .ifBlank { json.optString("signedUrl") }
            require(signed.isNotBlank()) { "เปิดหลักฐานไม่สำเร็จ" }
            if (signed.startsWith("http")) signed
            else QueueGoNativeApi.BASE_URL + "/storage/v1" + signed
        } finally {
            connection.disconnect()
        }
    }
}
