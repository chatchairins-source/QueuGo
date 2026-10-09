package com.queuego.merchant

import com.queuego.shared.NativeAuth
import com.queuego.shared.QueueGoNativeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Existing private bucket and five-minute signing flow from Production qgmOpenGpSlip. */
internal suspend fun signMerchantGpSlip(auth: NativeAuth, objectPath: String): String = withContext(Dispatchers.IO) {
    val parts = objectPath.split('/')
    require(parts.all { it.isNotBlank() && it != "." && it != ".." }) { "พาธสลิปไม่ถูกต้อง" }
    val encoded = parts.joinToString("/") { QueueGoNativeApi().enc(it).replace("+", "%20") }
    val connection = URL(QueueGoNativeApi.BASE_URL + "/storage/v1/object/sign/gp-slips/" + encoded).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.doOutput = true
        connection.setRequestProperty("apikey", QueueGoNativeApi.PUBLISHABLE_KEY)
        connection.setRequestProperty("Authorization", "Bearer " + auth.session.accessToken)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(JSONObject().put("expiresIn", 300).toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        val data = JSONObject(raw.ifBlank { "{}" })
        check(code in 200..299) { data.optString("message").ifBlank { "เปิดสลิปไม่สำเร็จ (HTTP $code)" } }
        val signed = data.optString("signedURL")
        check(signed.startsWith("/object/sign/gp-slips/")) { "เปิดสลิปไม่สำเร็จ" }
        QueueGoNativeApi.BASE_URL + "/storage/v1" + signed
    } finally { connection.disconnect() }
}
