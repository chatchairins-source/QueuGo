package com.queuego.ridermaplibreosm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import android.widget.FrameLayout
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

data class QueueGoTrackingPoint(
    val latitude: Double,
    val longitude: Double,
    val color: Int,
    val title: String
)

class QueueGoMapLibreTrackingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val mapView = MapView(context)
    private var map: MapLibreMap? = null
    private var created = false
    private var started = false
    private var resumed = false
    private var destroyed = false
    private var pendingPoints: List<QueueGoTrackingPoint> = emptyList()
    private val iconBitmaps = linkedMapOf<Int, Bitmap>()

    var onReady: ((Boolean) -> Unit)? = null

    init {
        MapLibre.getInstance(context.applicationContext)
        addView(mapView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun onCreate(savedInstanceState: Bundle? = null) {
        if (created || destroyed) return
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
            ready.setStyle(Style.Builder().fromJson(queueGoRasterStyleJson())) {
                if (destroyed) return@setStyle
                render(pendingPoints)
                onReady?.invoke(true)
            }
        }
    }

    fun setPoints(points: List<QueueGoTrackingPoint>) {
        pendingPoints = points.filter { valid(it.latitude, it.longitude) }.take(24)
        map?.style?.let { render(pendingPoints) }
    }

    private fun render(points: List<QueueGoTrackingPoint>) {
        val ready = map ?: return
        ready.clear()
        if (points.isEmpty()) return

        val factory = IconFactory.getInstance(context)
        val markers = points.map { point ->
            val bitmap = iconBitmaps.getOrPut(point.color) { makeMarkerBitmap(point.color) }
            MarkerOptions()
                .position(LatLng(point.latitude, point.longitude))
                .title(point.title)
                .icon(factory.fromBitmap(bitmap))
        }
        ready.addMarkers(markers)

        val latLngs = points.map { LatLng(it.latitude, it.longitude) }
        if (latLngs.size == 1) {
            ready.cameraPosition = CameraPosition.Builder()
                .target(latLngs.first())
                .zoom(16.0)
                .build()
        } else {
            val bounds = LatLngBounds.fromLatLngs(latLngs)
            val camera = ready.getCameraForLatLngBounds(bounds)
            if (camera != null) {
                ready.cameraPosition = CameraPosition.Builder()
                    .target(camera.target)
                    .zoom((camera.zoom - 0.35).coerceAtMost(16.0))
                    .build()
            }
        }
    }

    private fun makeMarkerBitmap(color: Int): Bitmap {
        val size = 52
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, 23f, paint)
        paint.color = color
        canvas.drawCircle(size / 2f, size / 2f, 17f, paint)
        return bitmap
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

    fun destroy() {
        if (destroyed) return
        if (resumed) onPause()
        if (started) onStop()
        destroyed = true
        runCatching { map?.clear() }
        onReady?.invoke(false)
        if (created) mapView.onDestroy()
        map = null
        iconBitmaps.values.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
        iconBitmaps.clear()
        removeAllViews()
    }

    private fun valid(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in 5.0..21.0 && longitude in 97.0..106.0

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
