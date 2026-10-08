pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "QueueGoNative"
include(":core-model", ":core-network", ":core-auth", ":core-ui", ":customer-app", ":merchant-app", ":rider-app")
