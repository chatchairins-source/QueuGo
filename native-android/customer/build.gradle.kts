plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
android {
    namespace = "com.queuego.customer"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.queuego.customer"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = providers.environmentVariable("QG_CUSTOMER_VERSION_CODE").orNull?.toInt() ?: 3
        versionName = providers.environmentVariable("QG_NATIVE_VERSION_NAME").orNull ?: "0.3.0-visual-blueprint"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:35.0.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.11.4")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.11.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    implementation(project(":shared"))
    implementation(project(":rider-maplibre-osm"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("androidx.compose.foundation:foundation:1.11.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
