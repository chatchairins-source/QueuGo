package com.queuego.ridermaplibreosm

import java.io.File

data class MapLibreOsmConfig(
    val sourceUri: String,
    val initialLatitude: Double,
    val initialLongitude: Double,
    val initialZoom: Double = 16.0
) {
    init {
        require(isSupportedSourceUri(sourceUri)) {
            "sourceUri must use https/http/file/asset or pmtiles://https:// / pmtiles://file://"
        }
        require(initialLatitude in -90.0..90.0) { "initialLatitude out of range" }
        require(initialLongitude in -180.0..180.0) { "initialLongitude out of range" }
        require(initialZoom in 0.0..24.0) { "initialZoom out of range" }
    }

    val isOfflinePmTiles: Boolean
        get() = sourceUri.startsWith("pmtiles://file://")

    companion object {
        fun localPmTiles(
            file: File,
            initialLatitude: Double,
            initialLongitude: Double,
            initialZoom: Double = 16.0
        ): MapLibreOsmConfig {
            require(file.isAbsolute) { "PMTiles file must use an absolute path" }
            return MapLibreOsmConfig(
                sourceUri = "pmtiles://file://${file.absolutePath}",
                initialLatitude = initialLatitude,
                initialLongitude = initialLongitude,
                initialZoom = initialZoom
            )
        }

        fun onlineTileJson(
            tileJsonUrl: String,
            initialLatitude: Double,
            initialLongitude: Double,
            initialZoom: Double = 16.0
        ): MapLibreOsmConfig = MapLibreOsmConfig(
            sourceUri = tileJsonUrl,
            initialLatitude = initialLatitude,
            initialLongitude = initialLongitude,
            initialZoom = initialZoom
        )

        internal fun isSupportedSourceUri(value: String): Boolean =
            value.startsWith("https://") ||
                value.startsWith("http://") ||
                value.startsWith("file://") ||
                value.startsWith("asset://") ||
                value.startsWith("pmtiles://https://") ||
                value.startsWith("pmtiles://file://")
    }
}
