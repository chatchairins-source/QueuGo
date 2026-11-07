plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Reject privileged keys before BuildConfig generation; runtime rejection alone
// would still leave an accidentally supplied server key inside a debug APK.
val debugBackendUrl = providers.environmentVariable("QG_DEBUG_BACKEND_URL").orNull.orEmpty().trim()
val debugBackendPublicKey = providers.environmentVariable("QG_DEBUG_BACKEND_PUBLISHABLE_KEY").orNull.orEmpty().trim()
if (debugBackendUrl.isNotEmpty() || debugBackendPublicKey.isNotEmpty()) {
    require(debugBackendUrl.isNotEmpty() && debugBackendPublicKey.isNotEmpty()) {
        "Debug backend URL and public key must be supplied together"
    }
    val uri = runCatching { java.net.URI(debugBackendUrl) }.getOrNull()
    require(uri != null && uri.host != null && uri.userInfo == null && uri.query == null &&
        uri.fragment == null && (uri.port == -1 || uri.port in 1..65535) &&
        (uri.path.isNullOrEmpty() || uri.path == "/")) { "Invalid isolated debug backend origin" }
    val local = uri.host in setOf("localhost", "127.0.0.1", "10.0.2.2", "[::1]", "::1")
    require(uri.scheme == "https" || (uri.scheme == "http" && local)) { "Debug backend requires HTTPS or loopback" }
    require(!uri.host.equals("pkypiqhlrmzocysgeqew.supabase.co", ignoreCase = true) &&
        debugBackendPublicKey != "sb_publishable_Vn2Il4jp-iBtzNsGRNY8Lg_Tpvk2Vre") { "Debug backend must not mix Production settings" }
    val publishable = debugBackendPublicKey.startsWith("sb_publishable_") &&
        debugBackendPublicKey.length in 24..4096 && debugBackendPublicKey.none(Char::isWhitespace)
    val anon = runCatching {
        val parts = debugBackendPublicKey.split('.')
        val payload = if (parts.size == 3 && parts.all { it.isNotEmpty() })
            groovy.json.JsonSlurper().parseText(String(java.util.Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)) as? Map<*, *> else null
        payload?.get("role") == "anon"
    }.getOrDefault(false)
    require(publishable || anon) { "Debug backend key must be public; server keys cannot enter APKs" }
}

fun backendBuildConfigLiteral(value: String): String = "\"" + value
    .replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\n", "\\n").replace("\r", "\\r") + "\""

android {
    namespace = "com.queuego.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "QG_DEBUG_BACKEND_URL", "\"\"")
        buildConfigField("String", "QG_DEBUG_BACKEND_PUBLISHABLE_KEY", "\"\"")
    }
    buildTypes {
        getByName("debug") {
            buildConfigField("String", "QG_DEBUG_BACKEND_URL", backendBuildConfigLiteral(debugBackendUrl))
            buildConfigField("String", "QG_DEBUG_BACKEND_PUBLISHABLE_KEY", backendBuildConfigLiteral(debugBackendPublicKey))
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(project(":rider-maplibre-osm"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.github.webrtc-sdk:android:150.7871.01")
    implementation("androidx.lifecycle:lifecycle-runtime:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.maplibre.gl:android-sdk-opengl:13.7.1")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("androidx.compose.foundation:foundation:1.11.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
