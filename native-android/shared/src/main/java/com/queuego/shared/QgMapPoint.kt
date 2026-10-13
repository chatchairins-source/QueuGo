package com.queuego.shared

/** Same Thailand coordinate bounds as Production qgIsValidCoordinate(..., true). */
enum class QgMapRole { CUSTOMER, MERCHANT, RIDER }

data class QgMapPoint(val latitude: Double, val longitude: Double, val color: Int, val role: QgMapRole = QgMapRole.CUSTOMER) {
    val valid: Boolean get() = latitude.isFinite() && longitude.isFinite() &&
        latitude in 5.0..21.0 && longitude in 97.0..106.0
}
