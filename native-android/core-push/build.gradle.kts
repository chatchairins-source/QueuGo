plugins { alias(libs.plugins.android.library) }

android {
    namespace = "com.queuetech.queuego.core.push"
    compileSdk = 36

    defaultConfig { minSdk = 24 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core-model"))
    implementation(project(":core-network"))
    implementation(project(":core-auth"))
    implementation(libs.kotlinx.coroutines.android)
}
