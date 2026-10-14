package com.queuego.ridermaplibreosm

import android.content.Context
import android.graphics.RectF
import android.os.Bundle
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
    private var created = false
    private var started = false
    private var resumed = false
    private var destroyed = false
    private var styleApplyCount = 0

    var onMapReady: ((MapLibreMap) -> Unit)? = null
    var onMapFullyRendered: (() -> Unit)? = null

    init {
        MapLibre.getInstance(context.applicationContext)
        mapView = MapView(context)
        mapView.addOnDidFinishRenderingMapListener { fully ->
            if (fully) onMapFullyRendered?.invoke()
        }
        addView(
            mapView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun onCreate(savedInstanceState: Bundle? = null) {
        check(!destroyed) { "MapLibre view already destroyed" }
        if (created) return
        created = true
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { readyMap ->
            if (destroyed) return@getMapAsync
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
        if (previous?.sourceUri != config.sourceUri) {
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
        styleApplyCount += 1
        readyMap.setStyle(ShortbreadVillageStyle.builder(config.sourceUri)) {
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
        if (destroyed || !created || started) return
        started = true
        mapView.onStart()
    }

    fun onResume() {
        if (destroyed || !created || resumed) return
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

    fun onSaveInstanceState(outState: Bundle) {
        if (!destroyed && created) mapView.onSaveInstanceState(outState)
    }

    fun onLowMemory() {
        if (!destroyed && created) mapView.onLowMemory()
    }

    fun destroy() {
        if (destroyed) return
        if (resumed) onPause()
        if (started) onStop()
        destroyed = true
        if (created) mapView.onDestroy()
        created = false
        map = null
        removeAllViews()
    }

    fun renderedFeatureCount(layerIds: Array<String>): Int {
        val readyMap = map ?: return 0
        val mapWidth = mapView.width
        val mapHeight = mapView.height
        if (mapWidth <= 0 || mapHeight <= 0) return 0
        return readyMap.queryRenderedFeatures(
            RectF(0f, 0f, mapWidth.toFloat(), mapHeight.toFloat()),
            *layerIds
        ).size
    }

    fun mapViewIdentity(): Int = System.identityHashCode(mapView)
    fun mapIdentity(): Int? = map?.let(System::identityHashCode)
    fun styleApplyCount(): Int = styleApplyCount
    fun isDestroyed(): Boolean = destroyed
}
