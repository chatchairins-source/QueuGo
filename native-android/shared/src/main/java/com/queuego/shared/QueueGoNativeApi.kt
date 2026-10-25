package com.queuego.shared

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

class QueueGoHttpException(val statusCode: Int, message: String) : Exception(message)

data class QueueGoPlaceSearchResult(
    val id: String,
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val category: String?,
    val subcategory: String?,
    val province: String?
)

private const val QUEUEGO_NATIVE_RESPONSE_MAX_BYTES = 12 * 1024 * 1024

private fun InputStream.readQueueGoResponseBounded(
    limit: Int = QUEUEGO_NATIVE_RESPONSE_MAX_BYTES
): String {
    val output = ByteArrayOutputStream(minOf(limit, 256 * 1024))
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit) {
            "ข้อมูลตอบกลับมีขนาดใหญ่เกินไป กรุณาลองใหม่"
        }
        output.write(buffer, 0, count)
    }
    return output.toString(StandardCharsets.UTF_8.name())
}

private const val QUEUEGO_REVERSE_RADIUS_M = 100_000
private const val QUEUEGO_REVERSE_MAX_LABEL_DISTANCE_M = 50_000.0
private val queueGoAddressKeys =
    listOf("address", "village", "moo", "subdistrict", "district", "province", "postcode")

internal fun queueGoPlaceAddress(row: JSONObject): String? {
    val seen = HashSet<String>()
    val parts = queueGoAddressKeys.mapNotNull { key ->
        row.optString(key).trim()
            .takeIf { it.isNotBlank() && it != "null" }
            ?.takeIf { seen.add(it.lowercase()) }
    }
    return parts.joinToString(" ").takeIf { it.isNotBlank() }
        ?: row.optString("canonical_name").trim().takeIf { it.isNotBlank() && it != "null" }
}

internal fun queueGoPlaceSearchResult(row: JSONObject): QueueGoPlaceSearchResult? {
    val id = row.optString("place_id").trim()
    val name = row.optString("canonical_name").trim()
    val latitude = row.optDouble("latitude", Double.NaN)
    val longitude = row.optDouble("longitude", Double.NaN)
    if (
        id.isBlank() || name.isBlank() ||
        !latitude.isFinite() || !longitude.isFinite() ||
        latitude !in 5.0..21.0 || longitude !in 97.0..106.0
    ) return null
    val address = queueGoPlaceAddress(row)?.takeIf { !it.equals(name, ignoreCase = true) }
    return QueueGoPlaceSearchResult(
        id = id,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
        category = row.optString("category").trim().takeIf { it.isNotBlank() && it != "null" },
        subcategory = row.optString("subcategory").trim().takeIf { it.isNotBlank() && it != "null" },
        province = row.optString("province").trim().takeIf { it.isNotBlank() && it != "null" }
    )
}

internal fun queueGoReverseCandidate(rows: JSONArray): JSONObject? {
    var fallback: JSONObject? = null
    for (index in 0 until rows.length()) {
        val row = rows.optJSONObject(index) ?: continue
        val distance = row.optDouble("distance_m", Double.POSITIVE_INFINITY)
        if (!distance.isFinite() || distance > QUEUEGO_REVERSE_MAX_LABEL_DISTANCE_M) continue
        if (fallback == null) fallback = row
        if (row.optString("subcategory").equals("subdistrict", ignoreCase = true)) return row
    }
    return fallback
}

class QueueGoNativeApi {
    private val orderReads = QueueGoOrderReadCompatibility()
    companion object {
        const val BASE_URL = "https://pkypiqhlrmzocysgeqew.supabase.co"
        const val PUBLISHABLE_KEY = "sb_publishable_Vn2Il4jp-iBtzNsGRNY8Lg_Tpvk2Vre"
        private const val TIMEOUT = 15000
    }

    suspend fun get(path: String, token: String? = null): Any =
        withContext(Dispatchers.IO) {
            orderReads.read(path, { (it as? QueueGoHttpException)?.statusCode }) {
                request("GET", "/rest/v1/" + it, token, null)
            }
        }

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

    suspend fun searchPlaces(
        query: String,
        token: String? = null,
        limit: Int = 8
    ): List<QueueGoPlaceSearchResult> {
        val normalized = query.trim()
        if (normalized.length < 2) return emptyList()
        return try {
            val rows = array(
                rpc(
                    "qg_search_places",
                    token,
                    JSONObject()
                        .put("p_query", normalized)
                        .put("p_limit", limit.coerceIn(1, 12))
                )
            )
            buildList {
                for (index in 0 until rows.length()) {
                    queueGoPlaceSearchResult(rows.optJSONObject(index) ?: continue)?.let(::add)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun reversePlaceAddress(
        latitude: Double,
        longitude: Double,
        token: String? = null
    ): String? {
        if (latitude !in 5.0..21.0 || longitude !in 97.0..106.0) return null
        return try {
            val rows = array(
                rpc(
                    "qg_search_places",
                    token,
                    JSONObject()
                        .put("p_query", "")
                        .put("p_latitude", latitude)
                        .put("p_longitude", longitude)
                        .put("p_category", "administrative_area")
                        .put("p_limit", 16)
                        .put("p_radius_m", QUEUEGO_REVERSE_RADIUS_M)
                )
            )
            queueGoReverseCandidate(rows)?.let(::queueGoPlaceAddress)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

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
            val declaredLength = connection.contentLengthLong
            require(declaredLength <= 0L || declaredLength <= QUEUEGO_NATIVE_RESPONSE_MAX_BYTES) {
                "ข้อมูลตอบกลับมีขนาดใหญ่เกินไป กรุณาลองใหม่"
            }
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readQueueGoResponseBounded() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    val o = JSONObject(text)
                    o.optString("message").ifBlank {
                        o.optString("error_description").ifBlank {
                            o.optString("error").ifBlank { o.optString("hint") }
                        }
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
