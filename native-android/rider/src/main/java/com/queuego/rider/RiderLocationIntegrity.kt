package com.queuego.rider

import android.location.Location
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class RiderLocationIntegrityDiagnostics(
    val observedFixes: Int = 0,
    val mockFixes: Int = 0,
    val lastMockElapsedRealtimeNanos: Long? = null
)

internal data class RiderLocationIntegrityHealth(
    val headline: String,
    val detail: String,
    val healthy: Boolean?
)

internal object RiderLocationIntegrityStore {
    private val mutableDiagnostics = MutableStateFlow(RiderLocationIntegrityDiagnostics())
    val diagnostics: StateFlow<RiderLocationIntegrityDiagnostics> = mutableDiagnostics.asStateFlow()

    fun startSession() {
        mutableDiagnostics.value = RiderLocationIntegrityDiagnostics()
    }

    fun record(location: Location) {
        val current = mutableDiagnostics.value
        val mock = riderLocationIsMock(location)
        mutableDiagnostics.value = current.copy(
            observedFixes = current.observedFixes + 1,
            mockFixes = current.mockFixes + if (mock) 1 else 0,
            lastMockElapsedRealtimeNanos =
                if (mock) location.elapsedRealtimeNanos else current.lastMockElapsedRealtimeNanos
        )
    }
}

@Suppress("DEPRECATION")
internal fun riderLocationIsMock(location: Location): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock
    else location.isFromMockProvider

internal fun riderLocationIntegrityHealth(
    diagnostics: RiderLocationIntegrityDiagnostics
): RiderLocationIntegrityHealth = when {
    diagnostics.observedFixes <= 0 -> RiderLocationIntegrityHealth(
        headline = "ยังไม่มีข้อมูล GPS ใน session นี้",
        detail = "เปิดออนไลน์หรือเริ่มนำทางเพื่อให้ QueueGo ตรวจ fix ตำแหน่งจริงจาก Android",
        healthy = null
    )
    diagnostics.mockFixes <= 0 -> RiderLocationIntegrityHealth(
        headline = "ไม่พบตำแหน่งจำลอง",
        detail = "ตรวจแล้ว ${diagnostics.observedFixes} fix · Android ยังไม่ระบุ fix ใดว่าเป็น mock location",
        healthy = true
    )
    else -> RiderLocationIntegrityHealth(
        headline = "พบสัญญาณตำแหน่งจำลอง",
        detail = "Android ระบุ ${diagnostics.mockFixes}/${diagnostics.observedFixes} fix ว่าเป็น mock location · รอบนี้ QueueGo แจ้งเตือนเท่านั้นและยังไม่บล็อกงานอัตโนมัติ",
        healthy = false
    )
}
