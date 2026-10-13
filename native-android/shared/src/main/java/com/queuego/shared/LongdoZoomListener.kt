package com.queuego.shared

import com.longdo.api.IZoomListener

/**
 * Longdo 0.1.8 calls postZoom without a null check when setZoom keeps the
 * current level. Headless maps have no zoom-control listener by default.
 * Supply only the absent UI callback; preserve any SDK/app listener already set.
 */
fun qgEnsureLongdoZoomListener(map: com.longdo.api.Map) {
    if (map.getZoomListener() != null) return
    map.setZoomListener(object : IZoomListener {
        override fun preZoom(zoomLevel: Int) = Unit
        override fun postZoom(zoomLevel: Int) = Unit
        override fun finishZoomAnimation(newZoomLevel: Int) = Unit
    })
}
