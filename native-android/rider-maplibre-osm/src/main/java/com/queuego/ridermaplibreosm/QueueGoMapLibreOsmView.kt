package com.queuego.ridermaplibreosm

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

class QueueGoMapLibreOsmView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val mapView: MapView
    private var map: MapLibreMap? = null
    private var currentConfig: MapLibreOsmConfig? = null
    private var started = false
    private var resumed = false
    private var destroyed = false

    var onMapReady: ((MapLibreMap) -> Unit)? = null

    init {
        MapLibre.getInstance(context.applicationContext)
        mapView = MapView(context)
        addView(
            mapView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        mapView.onCreate(null)
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.uiSettings.isAttributionEnabled = true
            readyMap.uiSettings.isLogoEnabled = true
            currentConfig?.let(::applyConfig)
        }
    }

    fun setConfig(config: MapLibreOsmConfig) {
        check(!destroyed) { "MapLibre view already destroyed" }
        val previous = currentConfig
        currentConfig = config
        val readyMap = map ?: return
        if (previous?.tileJsonUrl != config.tileJsonUrl) {
            applyConfig(config)
        } else if (
            previous.initialLatitude != config.initialLatitude ||
            previous.initialLongitude != config.initialLongitude ||
            previous.initialZoom != config.initialZoom
        ) {
            applyCamera(readyMap, config)
        }
    }

    private fun applyConfig(config: MapLibreOsmConfig) {
        val readyMap = map ?: return
        readyMap.setStyle(ShortbreadVillageStyle.builder(config.tileJsonUrl)) {
            applyCamera(readyMap, config)
            onMapReady?.invoke(readyMap)
        }
    }

    private fun applyCamera(map: MapLibreMap, config: MapLibreOsmConfig) {
        map.cameraPosition = CameraPosition.Builder()
            .target(LatLng(config.initialLatitude, config.initialLongitude))
            .zoom(config.initialZoom)
            .build()
    }

    fun onStart() {
        if (destroyed || started) return
        started = true
        mapView.onStart()
    }

    fun onResume() {
        if (destroyed || resumed) return
        resumed = true
        mapView.onResume()
    }

    fun onPause() {
        if (destroyed || !resumed) return
        resumed = false
        mapView.onPause()
    }

    fun onStop() {
        if (destroyed || !started) return
        started = false
        mapView.onStop()
    }

    fun onLowMemory() {
        if (!destroyed) mapView.onLowMemory()
    }

    fun destroy() {
        if (destroyed) return
        if (resumed) onPause()
        if (started) onStop()
        destroyed = true
        mapView.onDestroy()
        map = null
        removeAllViews()
    }

    fun mapViewIdentity(): Int = System.identityHashCode(mapView)
    fun mapIdentity(): Int? = map?.let(System::identityHashCode)
}
