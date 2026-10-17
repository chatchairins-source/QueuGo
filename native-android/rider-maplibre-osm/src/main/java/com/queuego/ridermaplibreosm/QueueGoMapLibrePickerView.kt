package com.queuego.ridermaplibreosm

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.widget.FrameLayout
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * QueueGo-branded location picker renderer.
 *
 * The renderer is MapLibre (the same map stack used by QueueGo Map). Until the
 * signed QueueGo vector package is shipped inside Customer, this view uses the
 * public OSM raster fallback defined by the QueueGo Map architecture.
 */
class QueueGoMapLibrePickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val mapView: MapView
    private var map: MapLibreMap? = null
    private var created = false
    private var started = false
    private var resumed = false
    private var destroyed = false
    private var userMove = false
    private var pendingCamera: CameraRequest? = null

    var onReady: ((Boolean) -> Unit)? = null
    var onCenterObserved: ((Double, Double) -> Unit)? = null
    var onCenterChanged: ((Double, Double) -> Unit)? = null

    private data class CameraRequest(
        val latitude: Double,
        val longitude: Double,
        val zoom: Double,
        val select: Boolean,
        val animate: Boolean
    )

    init {
        MapLibre.getInstance(context.applicationContext)
        mapView = MapView(context)
        addView(mapView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun onCreate(savedInstanceState: Bundle? = null) {
        check(!destroyed) { "QueueGo picker map already destroyed" }
        if (created) return
        created = true
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { ready ->
            if (destroyed) return@getMapAsync
            map = ready
            ready.uiSettings.isAttributionEnabled = true
            ready.uiSettings.isLogoEnabled = false
            ready.uiSettings.isCompassEnabled = false
            ready.uiSettings.isRotateGesturesEnabled = false
            ready.uiSettings.isTiltGesturesEnabled = false

            ready.addOnCameraMoveStartedListener { reason ->
                userMove = reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE
            }
            ready.addOnCameraIdleListener {
                val target = ready.cameraPosition.target ?: return@addOnCameraIdleListener
                val lat = target.latitude
                val lng = target.longitude
                if (valid(lat, lng)) {
                    onCenterObserved?.invoke(lat, lng)
                    if (userMove) onCenterChanged?.invoke(lat, lng)
                }
                userMove = false
            }

            ready.setStyle(Style.Builder().fromJson(queueGoRasterStyleJson())) {
                if (destroyed) return@setStyle
                pendingCamera?.let(::applyCamera)
                onReady?.invoke(true)
            }
        }
    }

    fun setCamera(
        latitude: Double,
        longitude: Double,
        zoom: Double = 16.0,
        select: Boolean = false,
        animate: Boolean = true
    ) {
        if (!valid(latitude, longitude)) return
        val request = CameraRequest(latitude, longitude, zoom.coerceIn(5.0, 19.0), select, animate)
        pendingCamera = request
        map?.style?.let { applyCamera(request) }
    }

    fun selectCurrentCenter(): Boolean {
        val target = map?.cameraPosition?.target ?: return false
        if (!valid(target.latitude, target.longitude)) return false
        onCenterObserved?.invoke(target.latitude, target.longitude)
        onCenterChanged?.invoke(target.latitude, target.longitude)
        return true
    }

    private fun applyCamera(request: CameraRequest) {
        val ready = map ?: return
        userMove = false
        val target = LatLng(request.latitude, request.longitude)
        if (request.animate) {
            ready.animateCamera(CameraUpdateFactory.newLatLngZoom(target, request.zoom), 450)
        } else {
            ready.cameraPosition = CameraPosition.Builder().target(target).zoom(request.zoom).build()
        }
        onCenterObserved?.invoke(request.latitude, request.longitude)
        if (request.select) onCenterChanged?.invoke(request.latitude, request.longitude)
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

    fun onLowMemory() {
        if (!destroyed && created) mapView.onLowMemory()
    }

    fun destroy() {
        if (destroyed) return
        if (resumed) onPause()
        if (started) onStop()
        destroyed = true
        onReady?.invoke(false)
        if (created) mapView.onDestroy()
        map = null
        removeAllViews()
    }

    private fun valid(latitude: Double, longitude: Double): Boolean =
        latitude in 5.0..21.0 && longitude in 97.0..106.0 &&
            !(latitude == 0.0 && longitude == 0.0)

    private fun queueGoRasterStyleJson(): String = """
        {
          "version": 8,
          "sources": {
            "queuego_osm": {
              "type": "raster",
              "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
              "tileSize": 256,
              "attribution": "© OpenStreetMap contributors"
            }
          },
          "layers": [
            {
              "id": "queuego-background",
              "type": "background",
              "paint": {"background-color": "#f3f0e8"}
            },
            {
              "id": "queuego-osm",
              "type": "raster",
              "source": "queuego_osm"
            }
          ]
        }
    """.trimIndent()
}
