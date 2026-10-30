plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.queuego.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    androidResources { noCompress += "pmtiles" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
// Use the variant source API: AGP 9.4's legacy library sourceSets accessor
// performs an invalid AndroidLibrarySourceSet cast when configured here.
providers.environmentVariable("QG_OFFLINE_MAP_ASSETS").orNull?.let { assetsRoot ->
    require(file("$assetsRoot/maps/thailand-shortbread.pmtiles").isFile) {
        "QG_OFFLINE_MAP_ASSETS has no nationwide Shortbread archive"
    }
    androidComponents.onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory(assetsRoot)
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
    implementation("org.maplibre.gl:android-sdk-opengl:13.5.3")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("androidx.compose.foundation:foundation:1.11.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
