plugins { alias(libs.plugins.android.library) }
android {
    namespace = "com.queuetech.queuego.core.network"
    compileSdk = 37
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    api(project(":core-model"))
    implementation(libs.kotlinx.coroutines.android)
}
