package com.queuego.rider

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SessionStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("queuego_rider_native", Context.MODE_PRIVATE)
    private val alias = "queuego_rider_native_session"

    fun deviceId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() } ?: "android-device"

    @Synchronized
    fun pushDeviceId(): String {
        val existing = prefs.getString("push_device_id", null).orEmpty()
        if (existing.matches(Regex("^[0-9a-fA-F-]{36}$"))) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString("push_device_id", created).apply()
        return created
    }

    fun save(auth: QueueGoAuth) {
        val json = JSONObject()
            .put("authUserId", auth.session.authUserId)
            .put("accessToken", auth.session.accessToken)
            .put("refreshToken", auth.session.refreshToken)
            .put("expiresAtMs", auth.session.expiresAtMs)
            .put("sessionId", auth.session.sessionId)
            .put("userId", auth.user.id)
            .put("name", auth.user.name)
            .put("role", auth.user.role)
            .put("status", auth.user.status)
            .toString()
        prefs.edit().putString("session", encrypt(json)).apply()
    }

    fun load(): QueueGoAuth? = runCatching {
        val raw = prefs.getString("session", null) ?: return null
        val o = JSONObject(decrypt(raw))
        QueueGoAuth(
            QueueGoSession(
                authUserId = o.getString("authUserId"),
                accessToken = o.getString("accessToken"),
                refreshToken = o.optString("refreshToken").takeIf { it.isNotBlank() && it != "null" },
                expiresAtMs = o.optLong("expiresAtMs"),
                sessionId = o.getString("sessionId")
            ),
            QueueGoUser(
                id = o.getString("userId"),
                name = o.optString("name").ifBlank { "ไรเดอร์" },
                role = o.getString("role"),
                status = o.optString("status")
            )
        )
    }.getOrNull()

    fun clear() = prefs.edit().remove("session").apply()

    fun pendingRegistrationUserId(): String? = prefs.getString("pending_registration_user", null)

    fun saveRegistrationCheckpoint(auth: QueueGoAuth) {
        save(auth)
        check(prefs.edit().putString("pending_registration_user", auth.session.authUserId).commit())
    }

    fun finishRegistration() {
        check(prefs.edit().remove("pending_registration_user").commit())
        java.io.File(context.noBackupFilesDir, "rider-registration.enc").delete()
    }

    @Synchronized
    fun saveRegistrationDraft(value: JSONObject) {
        val encrypted = encrypt(value.toString())
        val target = java.io.File(context.noBackupFilesDir, "rider-registration.enc")
        val temporary = java.io.File(context.noBackupFilesDir, "rider-registration.tmp")
        java.io.FileOutputStream(temporary).use { stream ->
            stream.write(encrypted.toByteArray(Charsets.UTF_8)); stream.fd.sync()
        }
        check(temporary.renameTo(target)) { "บันทึกใบสมัครในเครื่องไม่สำเร็จ" }
    }

    @Synchronized
    fun loadRegistrationDraft(): JSONObject? {
        val target = java.io.File(context.noBackupFilesDir, "rider-registration.enc")
        if (!target.exists()) return null
        require(target.length() <= 100 * 1024 * 1024) { "ใบสมัครในเครื่องไม่ถูกต้อง" }
        return JSONObject(decrypt(target.readText()))
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 12)
        val iv = bytes.copyOfRange(0, 12)
        val ciphertext = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }
}
