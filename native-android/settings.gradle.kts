pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        providers.environmentVariable("QG_MAPLIBRE_COMPAT_REPOSITORY").orNull?.let { repository ->
            require(file("$repository/component.json").isFile) { "Verified MapLibre component provenance is required" }
            maven {
                url = uri(repository)
                content { includeModule("org.maplibre.gl", "android-sdk-opengl") }
            }
        }
        google(); mavenCentral()
    }
}
rootProject.name = "QueueGoNative"
include(
    ":shared", ":customer", ":merchant", ":rider", ":rider-navigation-map", ":rider-maplibre-osm",
    ":rider_offline_map_pack", ":rider_offline_routing_pack",
)
