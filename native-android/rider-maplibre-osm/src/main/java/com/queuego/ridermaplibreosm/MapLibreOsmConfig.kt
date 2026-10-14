package com.queuego.ridermaplibreosm

data class MapLibreOsmConfig(
    val tileJsonUrl: String,
    val initialLatitude: Double,
    val initialLongitude: Double,
    val initialZoom: Double = 16.0
) {
    init {
        require(
            tileJsonUrl.startsWith("https://") ||
                tileJsonUrl.startsWith("http://") ||
                tileJsonUrl.startsWith("file://") ||
                tileJsonUrl.startsWith("asset://")
        ) { "tileJsonUrl must use https/http/file/asset" }
        require(initialLatitude in -90.0..90.0) { "initialLatitude out of range" }
        require(initialLongitude in -180.0..180.0) { "initialLongitude out of range" }
        require(initialZoom in 0.0..24.0) { "initialZoom out of range" }
    }
}
