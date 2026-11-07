package com.queuego.shared

import java.net.URI
import java.util.Base64
import org.json.JSONObject

internal const val QUEUEGO_PRODUCTION_BACKEND = "https://pkypiqhlrmzocysgeqew.supabase.co"
internal const val QUEUEGO_PRODUCTION_PUBLISHABLE_KEY = "sb_publishable_Vn2Il4jp-iBtzNsGRNY8Lg_Tpvk2Vre"

internal data class QueueGoBackendConfiguration(val baseUrl: String, val publishableKey: String) {
    val websocketOrigin: String get() = baseUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
}

/** Release always uses Production; debug overrides must be a complete, isolated pair. */
internal fun queueGoBackendConfiguration(
    debug: Boolean,
    overrideUrl: String?,
    overrideKey: String?
): QueueGoBackendConfiguration {
    val production = QueueGoBackendConfiguration(QUEUEGO_PRODUCTION_BACKEND, QUEUEGO_PRODUCTION_PUBLISHABLE_KEY)
    if (!debug) return production
    val url = overrideUrl.orEmpty().trim()
    val key = overrideKey.orEmpty().trim()
    if (url.isEmpty() && key.isEmpty()) return production
    require(url.isNotEmpty() && key.isNotEmpty()) { "Debug backend URL and public key must be supplied together" }
    val uri = runCatching { URI(url) }.getOrNull()
    require(uri != null && uri.host != null && uri.userInfo == null && uri.query == null &&
        uri.fragment == null && (uri.port == -1 || uri.port in 1..65535) && (uri.path.isNullOrEmpty() || uri.path == "/")) {
        "Debug backend must be an origin without credentials, path, query or fragment"
    }
    val local = uri.host in setOf("localhost", "127.0.0.1", "10.0.2.2", "[::1]", "::1")
    require(uri.scheme == "https" || (uri.scheme == "http" && local)) {
        "Debug backend requires HTTPS except for explicit emulator/loopback hosts"
    }
    val origin = url.trimEnd('/')
    require(!uri.host.equals(URI(QUEUEGO_PRODUCTION_BACKEND).host, ignoreCase = true) &&
        key != QUEUEGO_PRODUCTION_PUBLISHABLE_KEY) { "Isolated debug backend must not mix Production settings" }
    val isPublishable = key.startsWith("sb_publishable_") && key.length in 24..4096 && key.none(Char::isWhitespace)
    val isAnonJwt = runCatching {
        val parts = key.split('.')
        parts.size == 3 && parts.all { it.isNotEmpty() } &&
            JSONObject(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)).optString("role") == "anon"
    }.getOrDefault(false)
    require(isPublishable || isAnonJwt) { "Debug backend key must be a public publishable or anon key" }
    return QueueGoBackendConfiguration(origin, key)
}

object QueueGoBackendEnvironment {
    private val configuration by lazy {
        queueGoBackendConfiguration(BuildConfig.DEBUG, BuildConfig.QG_DEBUG_BACKEND_URL, BuildConfig.QG_DEBUG_BACKEND_PUBLISHABLE_KEY)
    }
    val baseUrl: String get() = configuration.baseUrl
    val publishableKey: String get() = configuration.publishableKey
    val websocketOrigin: String get() = configuration.websocketOrigin
}
