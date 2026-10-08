plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.queuego.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("androidx.compose.foundation:foundation:1.11.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
