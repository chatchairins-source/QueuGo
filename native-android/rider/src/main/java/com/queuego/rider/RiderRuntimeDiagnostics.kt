package com.queuego.rider

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.view.Choreographer
import com.queuego.ridernavigationmap.NavigationMapDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class RiderNetworkRuntimeSnapshot(
    val totalRequests: Long = 0,
    val failedRequests: Long = 0,
    val slowRequests: Long = 0,
    val totalDurationMs: Long = 0,
    val worstDurationMs: Long = 0
) {
    val averageDurationMs: Long
        get() = if (totalRequests == 0L) 0L else totalDurationMs / totalRequests
}

internal data class RiderUiJankSnapshot(
    val totalFrames: Long = 0,
    val jankyFrames: Long = 0,
    val severeFrames: Long = 0,
    val worstFrameMs: Long = 0
)

internal object RiderRuntimeDiagnosticsStore {
    private val mutableMapDiagnostics = MutableStateFlow<NavigationMapDiagnostics?>(null)
    private val mutableNetworkDiagnostics = MutableStateFlow(RiderNetworkRuntimeSnapshot())
    private val mutableUiJankDiagnostics = MutableStateFlow(RiderUiJankSnapshot())

    val mapDiagnostics: StateFlow<NavigationMapDiagnostics?> = mutableMapDiagnostics.asStateFlow()
    val networkDiagnostics: StateFlow<RiderNetworkRuntimeSnapshot> = mutableNetworkDiagnostics.asStateFlow()
    val uiJankDiagnostics: StateFlow<RiderUiJankSnapshot> = mutableUiJankDiagnostics.asStateFlow()

    fun publishMap(value: NavigationMapDiagnostics) {
        mutableMapDiagnostics.value = value
    }

    @Synchronized
    fun recordNetwork(durationMs: Long, success: Boolean) {
        mutableNetworkDiagnostics.value = riderNetworkSnapshotAfter(
            mutableNetworkDiagnostics.value,
            durationMs,
            success
        )
    }

    @Synchronized
    fun recordFrame(frameDurationMs: Long) {
        mutableUiJankDiagnostics.value = riderUiJankSnapshotAfter(
            mutableUiJankDiagnostics.value,
            frameDurationMs
        )
    }
}

internal fun riderNetworkSnapshotAfter(
    previous: RiderNetworkRuntimeSnapshot,
    durationMs: Long,
    success: Boolean
): RiderNetworkRuntimeSnapshot {
    val duration = durationMs.coerceAtLeast(0L)
    return previous.copy(
        totalRequests = previous.totalRequests + 1,
        failedRequests = previous.failedRequests + if (success) 0 else 1,
        slowRequests = previous.slowRequests + if (duration >= 1_500L) 1 else 0,
        totalDurationMs = previous.totalDurationMs + duration,
        worstDurationMs = maxOf(previous.worstDurationMs, duration)
    )
}

internal fun riderUiJankSnapshotAfter(
    previous: RiderUiJankSnapshot,
    frameDurationMs: Long
): RiderUiJankSnapshot {
    val duration = frameDurationMs.coerceAtLeast(0L)
    return previous.copy(
        totalFrames = previous.totalFrames + 1,
        jankyFrames = previous.jankyFrames + if (duration >= 34L) 1 else 0,
        severeFrames = previous.severeFrames + if (duration >= 100L) 1 else 0,
        worstFrameMs = maxOf(previous.worstFrameMs, duration)
    )
}

internal object RiderFrameJankTracker {
    private var started = false
    private var lastFrameNanos = 0L

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!started) return
            val previous = lastFrameNanos
            lastFrameNanos = frameTimeNanos
            if (previous > 0L) {
                val durationMs = ((frameTimeNanos - previous) / 1_000_000L).coerceAtLeast(0L)
                if (durationMs in 1L..500L) {
                    RiderRuntimeDiagnosticsStore.recordFrame(durationMs)
                }
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        if (started) return
        started = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(callback)
    }

    fun stop() {
        if (!started) return
        started = false
        lastFrameNanos = 0L
        Choreographer.getInstance().removeFrameCallback(callback)
    }
}

internal data class RiderRuntimeHealth(
    val healthy: Boolean?,
    val headline: String,
    val detail: String
)

internal fun riderNetworkRuntimeHealth(value: RiderNetworkRuntimeSnapshot): RiderRuntimeHealth {
    if (value.totalRequests == 0L) {
        return RiderRuntimeHealth(
            healthy = null,
            headline = "ยังไม่มีข้อมูล QueueGo API ใน session นี้",
            detail = "เมื่อแอปเริ่มโหลดงาน/สถานะ ระบบจะวัดเฉพาะเวลาและผลสำเร็จของ request"
        )
    }
    val failurePercent = value.failedRequests * 100.0 / value.totalRequests.toDouble()
    val healthy = failurePercent <= 10.0 && value.averageDurationMs < 1_500L
    return RiderRuntimeHealth(
        healthy = healthy,
        headline = if (healthy) "การเชื่อมต่อ QueueGo ปกติ" else "พบ request ช้าหรือล้มเหลวที่ควรตรวจ",
        detail = "request ${value.totalRequests} · fail ${value.failedRequests} · slow ${value.slowRequests}" +
            " · avg ${value.averageDurationMs} ms · worst ${value.worstDurationMs} ms"
    )
}

internal fun riderUiJankRuntimeHealth(value: RiderUiJankSnapshot): RiderRuntimeHealth {
    if (value.totalFrames < 60L) {
        return RiderRuntimeHealth(
            healthy = null,
            headline = "กำลังเก็บข้อมูลความลื่นของหน้าจอ",
            detail = "frames ${value.totalFrames} · ต้องมีอย่างน้อย 60 เฟรมก่อนประเมิน"
        )
    }
    val jankPercent = value.jankyFrames * 100.0 / value.totalFrames.toDouble()
    val healthy = jankPercent <= 5.0 && value.severeFrames == 0L
    return RiderRuntimeHealth(
        healthy = healthy,
        headline = if (healthy) "เฟรม UI ลื่นใน session นี้" else "พบเฟรมกระตุกที่ควรตรวจ",
        detail = "frames ${value.totalFrames} · jank ${"%.1f".format(java.util.Locale.US, jankPercent)}%" +
            " · severe ${value.severeFrames} · worst ${value.worstFrameMs} ms"
    )
}
internal data class RiderMapRuntimeHealth(
    val healthy: Boolean,
    val headline: String,
    val detail: String
)

internal fun riderMapRuntimeHealth(value: NavigationMapDiagnostics): RiderMapRuntimeHealth {
    val healthy =
        value.mapCreatedCount <= 1 &&
            value.mapReplacementCount == 0 &&
            value.snapshotBridgeFailureCount == 0
    val headline = if (healthy) {
        "Map reuse ปกติ"
    } else {
        "พบการสร้าง/แทนที่ map ที่ควรตรวจ"
    }
    val detail =
        "surface ${value.viewCreatedCount} · map ${value.mapCreatedCount}" +
            " · replace ${value.mapReplacementCount}" +
            " · render ${value.renderPassCount}" +
            " · route rebuild ${value.routeLayerRebuildCount}" +
            " · snapshot fail ${value.snapshotBridgeFailureCount}"
    return RiderMapRuntimeHealth(healthy, headline, detail)
}

internal data class RiderProcessExitDiagnostic(
    val label: String,
    val detail: String,
    val problematic: Boolean
)

internal fun riderExitReasonLabel(reason: Int): String = when (reason) {
    ApplicationExitInfo.REASON_CRASH -> "แอปปิดจาก crash"
    ApplicationExitInfo.REASON_ANR -> "แอปไม่ตอบสนอง (ANR)"
    ApplicationExitInfo.REASON_LOW_MEMORY -> "ระบบปิดแอปเพราะหน่วยความจำต่ำ"
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "ระบบปิดจากการใช้ทรัพยากรสูง"
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "เริ่ม process ไม่สำเร็จ"
    ApplicationExitInfo.REASON_SIGNALED -> "process ถูก signal ให้หยุด"
    ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency ของ process หยุดทำงาน"
    ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "process รีสตาร์ตหลังสิทธิ์เปลี่ยน"
    ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "แอปถูกอัปเดต"
    ApplicationExitInfo.REASON_USER_REQUESTED -> "ผู้ใช้/ระบบร้องขอให้ปิดแอป"
    ApplicationExitInfo.REASON_USER_STOPPED -> "ผู้ใช้หยุดแอป"
    ApplicationExitInfo.REASON_OTHER -> "แอปถูกปิดด้วยเหตุผลอื่น"
    else -> "เหตุผลการปิด process #$reason"
}

internal fun riderExitReasonProblematic(reason: Int): Boolean = reason in setOf(
    ApplicationExitInfo.REASON_CRASH,
    ApplicationExitInfo.REASON_ANR,
    ApplicationExitInfo.REASON_LOW_MEMORY,
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
    ApplicationExitInfo.REASON_SIGNALED,
    ApplicationExitInfo.REASON_DEPENDENCY_DIED
)

internal fun readRiderLastProcessExit(context: Context): RiderProcessExitDiagnostic? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    val manager = context.getSystemService(ActivityManager::class.java) ?: return null
    val exit = runCatching {
        manager.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()
    }.getOrNull() ?: return null
    return RiderProcessExitDiagnostic(
        label = riderExitReasonLabel(exit.reason),
        detail = "Android exit reason · importance " + exit.importance,
        problematic = riderExitReasonProblematic(exit.reason)
    )
}
