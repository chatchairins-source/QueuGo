val qgVersionCode = providers.gradleProperty("QG_VERSION_CODE").orNull?.toIntOrNull()
val qgVersionName = providers.gradleProperty("QG_VERSION_NAME").orNull
val qgStoreFile = providers.gradleProperty("QG_STORE_FILE").orNull
val qgStorePassword = providers.gradleProperty("QG_STORE_PASSWORD").orNull
val qgKeyAlias = providers.gradleProperty("QG_KEY_ALIAS").orNull
val qgKeyPassword = providers.gradleProperty("QG_KEY_PASSWORD").orNull
val qgReleaseRequested = gradle.startParameter.taskNames.any {
    it.contains("Release", ignoreCase = true)
}

if (qgReleaseRequested) {
    require(qgVersionCode != null && qgVersionCode > 0) {
        "QueueGo release build requires QG_VERSION_CODE"
    }
    require(!qgVersionName.isNullOrBlank()) {
        "QueueGo release build requires QG_VERSION_NAME"
    }
    require(!qgStoreFile.isNullOrBlank()) {
        "QueueGo release build requires QG_STORE_FILE"
    }
    require(!qgStorePassword.isNullOrBlank()) {
        "QueueGo release build requires QG_STORE_PASSWORD"
    }
    require(!qgKeyAlias.isNullOrBlank()) {
        "QueueGo release build requires QG_KEY_ALIAS"
    }
    require(!qgKeyPassword.isNullOrBlank()) {
        "QueueGo release build requires QG_KEY_PASSWORD"
    }
    require(file("google-services.json").isFile) {
        "QueueGo release build requires role-specific google-services.json"
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.queuego.rider"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.queuego.rider"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = qgVersionCode ?: 5
        versionName = qgVersionName ?: "0.4.1-rider-map"
    }

    buildFeatures { compose = true }
    signingConfigs {
        create("release") {
            if (qgStoreFile != null) {
                storeFile = rootProject.file(qgStoreFile)
                storePassword = qgStorePassword
                keyAlias = qgKeyAlias
                keyPassword = qgKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            if (qgStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("junit:junit:4.13.2")
    implementation(platform("com.google.firebase:firebase-bom:35.0.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation(project(":shared"))
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("androidx.compose.foundation:foundation:1.11.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
