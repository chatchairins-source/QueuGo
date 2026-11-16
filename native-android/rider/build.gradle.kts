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
        versionCode = providers.environmentVariable("QG_RIDER_VERSION_CODE").orNull?.toInt() ?: 5
        versionName = providers.environmentVariable("QG_NATIVE_VERSION_NAME").orNull ?: "0.4.1-rider-map"
        manifestPlaceholders["queuegoMapServiceBaseUrl"] =
            providers.environmentVariable("QG_MAP_SERVICE_BASE_URL").orNull
                ?.takeIf { it.isNotBlank() } ?: "https://queuego-map-buriram.onrender.com"
    }

    sourceSets.getByName("androidTest").assets.srcDir(rootProject.projectDir.parentFile.resolve("ops/maps/sources"))

    buildFeatures { compose = true }
    androidResources { noCompress += "pmtiles" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


// Only Rider ships the nationwide offline archive. Customer/Merchant use online maps.
providers.environmentVariable("QG_OFFLINE_MAP_ASSETS").orNull?.let { assetsRoot ->
    require(file("$assetsRoot/maps/thailand-shortbread.pmtiles").isFile) {
        "QG_OFFLINE_MAP_ASSETS has no nationwide Shortbread archive"
    }
    androidComponents.onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory(assetsRoot)
    }
}

// Routing tiles are a separate, checksummed Valhalla graph; PMTiles cannot route.
providers.environmentVariable("QG_OFFLINE_ROUTING_ASSETS").orNull?.let { assetsRoot ->
    require(file("$assetsRoot/routing/manifest.json").isFile &&
        file("$assetsRoot/routing/tiles.tar").isFile &&
        file("$assetsRoot/routing/valhalla.json").isFile) {
        "QG_OFFLINE_ROUTING_ASSETS has no complete Valhalla routing package"
    }
    androidComponents.onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory(assetsRoot)
    }
}

dependencies {
    // Debug lifecycle host reads the actual renderer camera; use the same pinned SDK.
    debugImplementation("org.maplibre.gl:android-sdk-opengl:13.7.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("io.github.rallista:valhalla-mobile:0.6.4")
    androidTestImplementation("junit:junit:4.13.2")
    implementation(platform("com.google.firebase:firebase-bom:35.0.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation(project(":shared"))
    implementation(project(":rider-navigation-map"))
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("androidx.compose.foundation:foundation:1.11.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}


// Fail closed for Play/Internal Testing and release APKs: an online-only Rider
// bundle must never be distributed as a nationwide offline navigation build.
val verifyRiderReleaseOfflineAssets = tasks.register("verifyRiderReleaseOfflineAssets") {
    group = "verification"
    description = "Require both nationwide offline map and Valhalla routing inputs for Rider release"
    doLast {
        val mapRoot = providers.environmentVariable("QG_OFFLINE_MAP_ASSETS").orNull
            ?.takeIf { it.isNotBlank() }
            ?: error("Rider release requires QG_OFFLINE_MAP_ASSETS")
        val routingRoot = providers.environmentVariable("QG_OFFLINE_ROUTING_ASSETS").orNull
            ?.takeIf { it.isNotBlank() }
            ?: error("Rider release requires QG_OFFLINE_ROUTING_ASSETS")
        val required = listOf(
            file("$mapRoot/maps/thailand-shortbread.pmtiles"),
            file("$routingRoot/routing/manifest.json"),
            file("$routingRoot/routing/tiles.tar"),
            file("$routingRoot/routing/valhalla.json"),
        )
        required.forEach { asset ->
            check(asset.isFile && asset.length() > 0L) {
                "Rider release offline asset missing or empty: ${asset.path}"
            }
        }
        // Reject placeholders before shipping an offline-navigation release.
        // PMTiles v3 starts with the ASCII signature "PMTiles"; mere existence
        // and nonzero length would otherwise allow a bogus archive to pass.
        val pmtiles = required.first()
        val signature = pmtiles.inputStream().buffered().use { stream ->
            ByteArray(7).also { bytes ->
                check(stream.read(bytes) == bytes.size) {
                    "Rider release PMTiles header is truncated"
                }
            }
        }
        check(signature.contentEquals("PMTiles".toByteArray(Charsets.US_ASCII))) {
            "Rider release PMTiles has an invalid signature"
        }

        // PMTiles v3 encodes its version immediately after the seven-byte magic.
        // Reject a superficially valid header from an incompatible archive.
        val pmtilesVersion = pmtiles.inputStream().buffered().use { stream ->
            check(stream.skip(7L) == 7L) { "Rider release PMTiles header is truncated" }
            stream.read()
        }
        check(pmtilesVersion == 3) {
            "Rider release requires PMTiles version 3 (found $pmtilesVersion)"
        }

        // Valhalla routing tiles must be a nonempty, block-aligned TAR archive.
        // A zero-filled placeholder or arbitrary binary file is not a routing graph.
        val tilesTar = required[2]
        check(tilesTar.length() >= 1024L && tilesTar.length() % 512L == 0L) {
            "Rider release routing TAR has invalid block alignment or length"
        }
        val tarHeader = tilesTar.inputStream().buffered().use { stream ->
            ByteArray(512).also { bytes ->
                var offset = 0
                while (offset < bytes.size) {
                    val read = stream.read(bytes, offset, bytes.size - offset)
                    check(read > 0) { "Rider release routing TAR header is truncated" }
                    offset += read
                }
            }
        }
        check(tarHeader.copyOfRange(257, 262).contentEquals("ustar".toByteArray(Charsets.US_ASCII))) {
            "Rider release routing TAR has no POSIX/GNU TAR header"
        }

        // Ensure routing metadata is valid JSON, not an empty placeholder.
        listOf(required[1], required[3]).forEach { metadata ->
            val parsed = try {
                groovy.json.JsonSlurper().parse(metadata)
            } catch (exception: Exception) {
                error("Rider release routing JSON is invalid: ${metadata.path}: ${exception.message}")
            }
            check(parsed is Map<*, *> && parsed.isNotEmpty()) {
                "Rider release routing metadata must be a nonempty JSON object: ${metadata.path}"
            }
        }
    }
}

// The gate is also applied to bundleRelease for Play AAB delivery, not only APKs.
// It does not interfere with debug emulator builds that inject test assets.
tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }
    .configureEach { dependsOn(verifyRiderReleaseOfflineAssets) }
