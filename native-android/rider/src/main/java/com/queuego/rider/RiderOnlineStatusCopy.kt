package com.queuego.rider

/**
 * Truthful Home copy for the server-authoritative online state.
 *
 * An existing online lease must not be silently represented as "ready" when
 * Android location permission or the device location service is unavailable.
 * This is presentation only: it never toggles the server lease or dispatches.
 */
internal data class RiderOnlineStatusCopy(
    val title: String,
    val detail: String,
    val locationWarning: Boolean
)

internal fun riderOnlineStatusCopy(
    online: Boolean,
    locationPermission: Boolean,
    locationServices: Boolean
): RiderOnlineStatusCopy = when {
    !online -> RiderOnlineStatusCopy(
        title = "ยังไม่พร้อมรับงาน",
        detail = "เปิดเพื่อเริ่มรับงาน",
        locationWarning = false
    )
    !locationPermission || !locationServices -> RiderOnlineStatusCopy(
        title = "เปิดรับงานอยู่ · GPS ไม่พร้อม",
        detail = "สถานะเซิร์ฟเวอร์ยังเปิดอยู่ กรุณาอนุญาตตำแหน่งและเปิด GPS",
        locationWarning = true
    )
    else -> RiderOnlineStatusCopy(
        title = "พร้อมรับงานแล้ว",
        detail = "คุณจะได้รับงานในพื้นที่ใกล้เคียง",
        locationWarning = false
    )
}
