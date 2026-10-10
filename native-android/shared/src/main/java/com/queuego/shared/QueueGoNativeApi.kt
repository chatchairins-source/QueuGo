package com.queuego.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

class QueueGoHttpException(val statusCode: Int, message: String) : Exception(message)

class QueueGoNativeApi {
    companion object {
        const val BASE_URL = "https://pkypiqhlrmzocysgeqew.supabase.co"
        const val PUBLISHABLE_KEY = "sb_publishable_Vn2Il4jp-iBtzNsGRNY8Lg_Tpvk2Vre"
        private const val TIMEOUT = 15000
    }

    suspend fun get(path: String, token: String? = null): Any =
        withContext(Dispatchers.IO) { request("GET", "/rest/v1/" + path, token, null) }

    suspend fun post(path: String, token: String?, body: Any): Any =
        withContext(Dispatchers.IO) { request("POST", "/rest/v1/" + path, token, body) }

    suspend fun upsert(path: String, token: String, body: Any): Any =
        withContext(Dispatchers.IO) {
            request(
                "POST",
                "/rest/v1/" + path,
                token,
                body,
                "return=representation,resolution=merge-duplicates"
            )
        }

    suspend fun patch(path: String, token: String, body: JSONObject): Any =
        withContext(Dispatchers.IO) { request("PATCH", "/rest/v1/" + path, token, body) }

    suspend fun delete(path: String, token: String): Any =
        withContext(Dispatchers.IO) { request("DELETE", "/rest/v1/" + path, token, null) }

    suspend fun rpc(name: String, token: String? = null, body: JSONObject = JSONObject()): Any =
        withContext(Dispatchers.IO) { request("POST", "/rest/v1/rpc/" + name, token, body) }

    suspend fun functionPost(name: String, token: String, body: JSONObject): Any =
        withContext(Dispatchers.IO) { request("POST", "/functions/v1/" + name, token, body) }

    fun array(value: Any): JSONArray = value as? JSONArray ?: JSONArray()
    fun obj(value: Any): JSONObject = value as? JSONObject ?: JSONObject()
    fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun request(
        method: String,
        path: String,
        token: String?,
        body: Any?,
        prefer: String? = null
    ): Any {
        val connection = URL(BASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            connection.setRequestProperty("apikey", PUBLISHABLE_KEY)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer " + token)
            }
            if (method != "GET") {
                connection.setRequestProperty("Prefer", prefer ?: "return=representation")
            }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use {
                    it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText()
            }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    val o = JSONObject(text)
                    o.optString("message").ifBlank {
                        o.optString("error_description").ifBlank { o.optString("hint") }
                    }
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP $code"
                throw QueueGoHttpException(code, message)
            }
            if (text.isBlank()) return JSONObject()
            val value = text.trim()
            return when {
                value.startsWith("[") -> JSONArray(value)
                value.startsWith("{") -> JSONObject(value)
                value == "true" -> true
                value == "false" -> false
                value == "null" -> JSONObject.NULL
                else -> value.trim('"')
            }
        } finally {
            connection.disconnect()
        }
    }
}
