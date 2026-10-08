package com.queuego.shared

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureRoleSessionStore(private val context: Context, private val roleKey: String) {
    private val prefs = context.getSharedPreferences("queuego_native_" + roleKey, Context.MODE_PRIVATE)
    private val alias = "queuego_native_session_" + roleKey

    fun deviceId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() } ?: "android-device"

    fun save(auth: NativeAuth) {
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

    fun load(): NativeAuth? = runCatching {
        val raw = prefs.getString("session", null) ?: return null
        val o = JSONObject(decrypt(raw))
        NativeAuth(
            NativeSession(
                o.getString("authUserId"),
                o.getString("accessToken"),
                o.optString("refreshToken").takeIf { it.isNotBlank() && it != "null" },
                o.optLong("expiresAtMs"),
                o.getString("sessionId")
            ),
            NativeUser(
                o.getString("userId"),
                o.getString("authUserId"),
                o.optString("name").ifBlank { "QueueGo" },
                o.getString("role"),
                o.optString("status")
            )
        )
    }.getOrNull()

    fun clear() { prefs.edit().remove("session").apply() }

    private fun key(): SecretKey {
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
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 12)
        val iv = bytes.copyOfRange(0, 12)
        val ciphertext = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }
}
