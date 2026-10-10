import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("com.android.application") version "9.4.0" apply false
    id("com.android.library") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
}

// One signing implementation for all three Native apps. Credentials stay in
// runner/local environment and are never given a debug-key fallback.
val releaseKeystore = providers.environmentVariable("QG_ANDROID_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("QG_ANDROID_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("QG_ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("QG_ANDROID_KEY_PASSWORD").orNull
val signingAvailable = listOf(releaseKeystore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }

val verifyNativeReleaseGate = tasks.register<Exec>("verifyNativeReleaseGate") {
    workingDir(rootProject.projectDir.parentFile)
    commandLine("python3", rootProject.file("qa/verify-native-release-gate.py").absolutePath)
}

subprojects {
    pluginManager.withPlugin("com.android.application") {
        extensions.configure<ApplicationExtension> {
            val nativeReleaseSigning = if (signingAvailable) signingConfigs.create("queuegoRelease") {
                storeFile = file(releaseKeystore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            } else null
            buildTypes.getByName("release") {
                isDebuggable = false
                signingConfig = nativeReleaseSigning
            }
        }
        tasks.configureEach {
            // Guard direct internal release tasks too, not just assemble/bundle entry points.
            if (name.contains("Release")) {
                dependsOn(verifyNativeReleaseGate)
            }
        }
    }
}
