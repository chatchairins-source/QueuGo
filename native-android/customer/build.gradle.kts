plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.queuego.customer"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.queuego.customer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-native"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3:1.4.0")
}
