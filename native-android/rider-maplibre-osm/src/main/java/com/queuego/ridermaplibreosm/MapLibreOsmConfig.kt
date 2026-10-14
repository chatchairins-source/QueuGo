package com.queuego.ridermaplibreosm

data class MapLibreOsmConfig(
    val vectorSourceUri: String,
    val initialLatitude: Double,
    val initialLongitude: Double,
    val initialZoom: Double = 16.0
) {
    init {
        require(isSupportedVectorSourceUri(vectorSourceUri)) {
            "vectorSourceUri must use https/http/file/asset or pmtiles://https/file/asset"
        }
        require(initialLatitude in -90.0..90.0) { "initialLatitude out of range" }
        require(initialLongitude in -180.0..180.0) { "initialLongitude out of range" }
        require(initialZoom in 0.0..24.0) { "initialZoom out of range" }
    }
}

internal fun isSupportedVectorSourceUri(uri: String): Boolean =
    uri.startsWith("https://") ||
        uri.startsWith("http://") ||
        uri.startsWith("file://") ||
        uri.startsWith("asset://") ||
        uri.startsWith("pmtiles://https://") ||
        uri.startsWith("pmtiles://http://") ||
        uri.startsWith("pmtiles://file://") ||
        uri.startsWith("pmtiles://asset://")
